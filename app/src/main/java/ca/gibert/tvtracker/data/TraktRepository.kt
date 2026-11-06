package ca.gibert.tvtracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TraktRepository(
    private val api: TraktApi,
    private val cache: CacheStore,
) {

    suspend fun cachedToWatch(): CachedToWatch? = cache.readToWatch()

    suspend fun refreshToWatch(): CachedToWatch = coroutineScope {
        val upNext = async { api.upNext(extended = EXTENDED, limit = PAGE_LIMIT) }
        val watchlist = async { api.watchlistShows(extended = EXTENDED, limit = PAGE_LIMIT) }
        val merged = mergeToWatch(upNext.await(), watchlist.await())

        // Trakt's up-next aggregates can count positionally (every episode
        // before the latest watched one), so each show with a next episode
        // gets its counts recomputed from its per-episode flags. A failed
        // lookup keeps the up-next numbers rather than failing the refresh.
        val items = merged.map { item ->
            async {
                if (item !is WatchItem.UpNext) return@async item
                val detail = try {
                    api.showProgress(item.show.ids.trakt, extended = null, specials = false)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    null
                }
                WatchItem.UpNext(
                    show = item.show,
                    progress = reconcileProgress(item.show, item.progress, detail),
                )
            }
        }.awaitAll()

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
    }

    suspend fun markEpisodeUnwatched(episodeId: Int) {
        val response = api.removeFromHistory(
            SyncEpisodesRequest(listOf(SyncEpisode(TraktIds(trakt = episodeId)))),
        )
        checkEpisodeAccepted(response)
    }

    suspend fun getProfile(): User =
        api.userSettings().user ?: error("Trakt didn't return an account profile.")

    suspend fun clearCache() = cache.clear()

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
    }
}
