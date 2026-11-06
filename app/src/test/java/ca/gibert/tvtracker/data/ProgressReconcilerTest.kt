package ca.gibert.tvtracker.data

import org.junit.Assert.assertEquals
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

    /** A season of 8 aired episodes with the first [watched] ones flagged. */
    private fun season(number: Int, watched: Int) = SeasonProgress(
        number = number,
        aired = 8,
        completed = watched,
        episodes = (1..8).map { episode ->
            EpisodeProgress(number = episode, completed = episode <= watched)
        },
    )

    @Test
    fun `seasons watched out of order only count the episodes actually watched`() {
        // The reported bug: seasons 1 and 3 watched, season 2 untouched, yet
        // up-next claimed every episode before the latest one was watched.
        val progress = reconcileProgress(
            show = show(airedEpisodes = 24),
            upNext = upNextProgress(completed = 24, aired = 24),
            detail = ShowProgress(
                seasons = listOf(
                    season(number = 1, watched = 8),
                    season(number = 2, watched = 0),
                    season(number = 3, watched = 8),
                ),
            ),
        )

        assertEquals(16, progress.completed)
        assertEquals(24, progress.aired)
    }

    @Test
    fun `next episode from up next is preserved`() {
        val original = upNextProgress(completed = 12, aired = 18)

        val progress = reconcileProgress(
            show = show(airedEpisodes = 19),
            upNext = original,
            detail = ShowProgress(seasons = listOf(season(number = 1, watched = 8))),
        )

        assertEquals(original.nextEpisode, progress.nextEpisode)
        assertEquals(8, progress.completed)
        assertEquals(19, progress.aired)
    }

    @Test
    fun `aired episodes metadata wins over the up next aggregate`() {
        val progress = reconcileProgress(
            show = show(airedEpisodes = 24),
            upNext = upNextProgress(completed = 8, aired = 30),
            detail = ShowProgress(seasons = listOf(season(number = 1, watched = 8))),
        )

        assertEquals(24, progress.aired)
    }

    @Test
    fun `missing detail keeps the up next completed count`() {
        val progress = reconcileProgress(
            show = show(airedEpisodes = 24),
            upNext = upNextProgress(completed = 12, aired = 18),
            detail = null,
        )

        assertEquals(12, progress.completed)
        assertEquals(24, progress.aired)
    }

    @Test
    fun `empty detail seasons fall back to the up next completed count`() {
        val progress = reconcileProgress(
            show = show(airedEpisodes = null),
            upNext = upNextProgress(completed = 12, aired = 18),
            detail = ShowProgress(seasons = emptyList()),
        )

        assertEquals(12, progress.completed)
        assertEquals(18, progress.aired)
    }

    @Test
    fun `specials season is never counted`() {
        val progress = reconcileProgress(
            show = show(airedEpisodes = 8),
            upNext = upNextProgress(completed = 5, aired = 8),
            detail = ShowProgress(
                seasons = listOf(
                    SeasonProgress(
                        number = 0,
                        episodes = (1..3).map { EpisodeProgress(number = it, completed = true) },
                    ),
                    season(number = 1, watched = 4),
                ),
            ),
        )

        assertEquals(4, progress.completed)
    }
}
