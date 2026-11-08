package ca.gibert.tvtracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressReconcilerTest {

    private fun show(airedEpisodes: Int? = 24) = Show(
        title = "Example",
        ids = TraktIds(trakt = 1),
        airedEpisodes = airedEpisodes,
    )

    private fun upNextProgress(completed: Int, aired: Int) = Progress(
        aired = aired,
        completed = completed,
        nextEpisode = Episode(
            season = 2,
            number = 1,
            ids = TraktIds(trakt = 999),
        ),
    )

    /** A season with [watched] watched episodes. */
    private fun watchedSeason(number: Int, watched: Int) = WatchedSeason(
        number = number,
        episodes = (1..watched).map { WatchedEpisode(number = it) },
    )

    @Test
    fun `bulk watched count only counts episodes that are actually watched`() {
        // The reported bug: seasons 1 and 3 watched, season 2 untouched, yet
        // up-next claimed every episode before the latest one was watched.
        val watched = WatchedShow(
            show = show(airedEpisodes = 24),
            seasons = listOf(
                watchedSeason(number = 1, watched = 8),
                watchedSeason(number = 3, watched = 8),
            ),
        )

        assertEquals(16, watched.completedEpisodeCount())
    }

    @Test
    fun `next episode from up next is preserved`() {
        val original = upNextProgress(completed = 12, aired = 18)

        val progress = reconcileProgressFromCount(
            show = show(airedEpisodes = 19),
            upNext = original,
            completed = 8,
        )

        assertEquals(original.nextEpisode, progress.nextEpisode)
        assertEquals(8, progress.completed)
        assertEquals(19, progress.aired)
    }

    @Test
    fun `aired episodes metadata wins over the up next aggregate`() {
        val progress = reconcileProgressFromCount(
            show = show(airedEpisodes = 24),
            upNext = upNextProgress(completed = 8, aired = 30),
            completed = 8,
        )

        assertEquals(24, progress.aired)
    }

    @Test
    fun `missing bulk data keeps the up next completed count`() {
        val progress = reconcileProgressFromCount(
            show = show(airedEpisodes = 24),
            upNext = upNextProgress(completed = 12, aired = 18),
            completed = null,
        )

        assertEquals(12, progress.completed)
        assertEquals(24, progress.aired)
    }

    @Test
    fun `bulk watched count ignores specials`() {
        val watched = WatchedShow(
            show = show(airedEpisodes = 8),
            seasons = listOf(
                watchedSeason(number = 0, watched = 3),
                watchedSeason(number = 1, watched = 4),
            ),
        )

        assertEquals(4, watched.completedEpisodeCount())
    }

    @Test
    fun `bulk watched count is unknown without season data`() {
        val watched = WatchedShow(show = show(), seasons = emptyList())

        assertNull(watched.completedEpisodeCount())
    }

    @Test
    fun `bulk completed count replaces the up next aggregate`() {
        val progress = reconcileProgressFromCount(
            show = show(airedEpisodes = 24),
            upNext = upNextProgress(completed = 24, aired = 24),
            completed = 16,
        )

        assertEquals(16, progress.completed)
        assertEquals(24, progress.aired)
    }
}
