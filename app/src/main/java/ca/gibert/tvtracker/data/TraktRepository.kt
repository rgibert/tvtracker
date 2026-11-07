package ca.gibert.tvtracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

class TraktRepository(
    private val api: TraktApi,
    private val cache: CacheStore,
) {

    // Bumped after every successful history mutation so list screens can tell
    // that their snapshot is stale even when the auto-refresh window has not
    // elapsed yet.
    private val mutationCounter = AtomicLong(0)

    val mutationVersion: Long
        get() = mutationCounter.get()

    suspend fun cachedToWatch(): CachedToWatch? = cache.readToWatch()

    suspend fun refreshToWatch(): CachedToWatch = coroutineScope {
        val upNext = async { api.upNext(extended = EXTENDED, limit = PAGE_LIMIT) }
        val watchlist = async { api.watchlistShows(extended = EXTENDED, limit = PAGE_LIMIT) }

        val upNextItems = upNext.await()
        val watchlistItems = watchlist.await()

        // One (or a few) bulk watched-history requests replace the previous
        // one-request-per-show progress lookups. Shows that Trakt says have no
        // history are skipped: the up-next aggregate already reports zero.
        val completedByShow = watchedCompletedCounts(
            requiredShowIds = upNextItems
                .asSequence()
                .filter { it.progress.nextEpisode != null }
                .filter {
                    it.progress.completed > 0 ||
                        it.progress.lastWatchedAt != null ||
                        it.progress.lastEpisode != null
                }
                .map { it.show.ids.trakt }
                .toHashSet(),
        )

        val items = mergeToWatch(upNextItems, watchlistItems).map { item ->
            when (item) {
                is WatchItem.UpNext -> WatchItem.UpNext(
                    show = item.show,
                    progress = reconcileProgressFromCount(
                        show = item.show,
                        upNext = item.progress,
                        completed = completedByShow[item.show.ids.trakt],
                    ),
                )

                is WatchItem.Watchlisted -> item
            }
        }

        val snapshot = CachedToWatch(
            items = items,
            fetchedAt = System.currentTimeMillis(),
        )
        cache.writeToWatch(snapshot)
        snapshot
    }

    suspend fun cachedUpcoming(): CachedUpcoming? = cache.readUpcoming()

    suspend fun refreshUpcoming(): CachedUpcoming {
        val today = LocalDate.now()
        val todayUtc = LocalDate.now(ZoneOffset.UTC)
        val requestStart = todayUtc.minusDays(1)

        // Extra days absorb time-zone differences between Trakt's UTC calendar
        // and the device's local calendar.
        val raw = api.myCalendar(
            startDate = requestStart.toString(),
            days = CALENDAR_REQUEST_DAYS,
            extended = EXTENDED,
        )

        val end = today.plusDays(CALENDAR_DAYS)
        val items = raw
            .filter { item ->
                val date = runCatching {
                    Instant.parse(item.firstAired)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }.getOrNull() ?: return@filter false
                !date.isBefore(today) && !date.isAfter(end)
            }
            .sortedBy { it.firstAired }

        val snapshot = CachedUpcoming(
            items = items,
            fetchedAt = System.currentTimeMillis(),
        )
        cache.writeUpcoming(snapshot)
        return snapshot
    }

    suspend fun cachedShowDetail(showId: Int): ShowDetail? = cache.readShowDetail(showId)

    suspend fun cacheShowDetail(detail: ShowDetail) =
        cache.writeShowDetail(detail.show.ids.trakt, detail)

    suspend fun refreshShowDetail(showId: Int): ShowDetail = coroutineScope {
        val show = async { api.show(showId, extended = EXTENDED) }
        val progress = async {
            api.showProgress(showId, extended = "full", specials = false)
        }
        val seasons = async {
            api.seasons(showId, extended = "episodes,full,images")
        }

        val detail = buildShowDetail(
            show = show.await(),
            progress = progress.await(),
            seasons = seasons.await(),
            fetchedAt = System.currentTimeMillis(),
        )
        cache.writeShowDetail(showId, detail)
        detail
    }

    suspend fun markEpisodeWatched(episodeId: Int) {
        val response = api.addToHistory(
            SyncEpisodesRequest(listOf(SyncEpisode(TraktIds(trakt = episodeId)))),
        )
        checkEpisodeAccepted(response)
        mutationCounter.incrementAndGet()
    }

    suspend fun markEpisodeUnwatched(episodeId: Int) {
        val response = api.removeFromHistory(
            SyncEpisodesRequest(listOf(SyncEpisode(TraktIds(trakt = episodeId)))),
        )
        checkEpisodeAccepted(response)
        mutationCounter.incrementAndGet()
    }

    suspend fun getProfile(): User =
        api.userSettings().user ?: error("Trakt didn't return an account profile.")

    suspend fun clearCache() = cache.clear()

    /**
     * Fetches watched episode totals in bulk. Trakt caps `extended=progress`
     * at 100 shows per page and reports the page count in a header, so stop
     * as soon as every up-next show has a total (or the history runs out).
     * Returns an empty/partial map when the request fails; missing shows fall
     * back to the up-next aggregate.
     */
    private suspend fun watchedCompletedCounts(requiredShowIds: Set<Int>): Map<Int, Int> {
        if (requiredShowIds.isEmpty()) return emptyMap()

        val counts = HashMap<Int, Int>(requiredShowIds.size)
        val seenShowIds = HashSet<Int>(requiredShowIds.size)
        var page = 1
        try {
            while (page <= MAX_WATCHED_PAGES) {
                val response = api.watchedShows(
                    extended = WATCHED_EXTENDED,
                    page = page,
                    limit = WATCHED_PAGE_LIMIT,
                )
                if (!response.isSuccessful) break

                val watched = response.body().orEmpty()
                if (watched.isEmpty()) break

                var newCounts = 0
                watched.forEach { item ->
                    val showId = item.show.ids.trakt
                    seenShowIds += showId

                    val completed = item.completedEpisodeCount() ?: return@forEach
                    if (showId !in counts) newCounts++
                    counts[showId] = completed
                }

                val pageCount = response.headers()["X-Pagination-Page-Count"]
                    ?.toIntOrNull()
                if (requiredShowIds.all { it in seenShowIds }) break
                if (pageCount != null && page >= pageCount) break
                if (newCounts == 0) break
                page++
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Keep any partial page; shows without a count fall back to the
            // up-next aggregate rather than failing the whole refresh.
        }
        return counts
    }

    private fun checkEpisodeAccepted(response: SyncResponse) {
        if (!response.notFound?.episodes.isNullOrEmpty()) {
            error("Trakt didn't recognise that episode.")
        }
    }

    private companion object {
        const val EXTENDED = "full,images"
        const val PAGE_LIMIT = 100
        const val CALENDAR_DAYS = 14L
        const val CALENDAR_REQUEST_DAYS = 17
        const val WATCHED_EXTENDED = "progress"
        const val WATCHED_PAGE_LIMIT = 100
        const val MAX_WATCHED_PAGES = 50
    }
}
