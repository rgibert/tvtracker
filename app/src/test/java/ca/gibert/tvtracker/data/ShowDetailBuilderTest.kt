package ca.gibert.tvtracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowDetailBuilderTest {

    private val show = Show(title = "Test Show", ids = TraktIds(trakt = 1))

    private fun episode(season: Int, number: Int, aired: String? = "2020-01-01T00:00:00.000Z") =
        Episode(
            season = season,
            number = number,
            title = "S${season}E$number",
            firstAired = aired,
            ids = TraktIds(trakt = season * 100 + number),
        )

    private fun season(number: Int, episodes: List<Episode>) = Season(
        number = number,
        title = "Season $number",
        airedEpisodes = episodes.size,
        episodes = episodes,
    )

    @Test
    fun `merges watched flags and drops specials`() {
        val seasons = listOf(
            season(0, listOf(episode(0, 1))),
            season(
                1,
                listOf(
                    episode(1, 2),
                    episode(1, 1),
                    episode(1, 3, aired = "2099-01-01T00:00:00.000Z"),
                ),
            ),
        )
        val progress = ShowProgress(
            aired = 2,
            completed = 1,
            nextEpisode = episode(1, 2),
            seasons = listOf(
                SeasonProgress(
                    number = 1,
                    aired = 2,
                    completed = 1,
                    episodes = listOf(
                        EpisodeProgress(number = 1, completed = true),
                        EpisodeProgress(number = 2, completed = false),
                    ),
                ),
            ),
        )

        val detail = buildShowDetail(show, progress, seasons, fetchedAt = 42L)

        assertEquals(listOf(1), detail.seasons.map { it.number })
        assertEquals(listOf(1, 2, 3), detail.seasons.single().episodes.map { it.episode.number })
        assertEquals(listOf(true, false, false), detail.seasons.single().episodes.map { it.completed })
        assertEquals(1, detail.seasons.single().completed)
        assertEquals(2, detail.seasons.single().aired)
        assertEquals(1, detail.completedEpisodes)
        assertEquals(2, detail.airedEpisodes)
        assertEquals(1, detail.nextEpisode?.season)
        assertEquals(2, detail.nextEpisode?.number)
        assertEquals(42L, detail.fetchedAt)
    }

    @Test
    fun `season without progress falls back to air dates and episode count`() {
        val seasons = listOf(
            season(
                1,
                listOf(
                    episode(1, 1),
                    episode(1, 2, aired = null),
                ),
            ),
        )

        val detail = buildShowDetail(show, ShowProgress(), seasons, fetchedAt = 1L)

        val built = detail.seasons.single()
        assertEquals(2, built.aired)
        assertEquals(0, built.completed)
        assertFalse(built.episodes.any { it.completed })
    }

    @Test
    fun `seasons with no non-special episodes are omitted`() {
        val detail = buildShowDetail(
            show,
            ShowProgress(),
            listOf(season(0, listOf(episode(0, 1))), season(2, emptyList())),
            fetchedAt = 1L,
        )

        assertTrue(detail.seasons.isEmpty())
        assertNull(detail.nextEpisode)
    }

    @Test
    fun `next episode is enriched from season metadata`() {
        val withArt = episode(1, 2).copy(
            images = Images(
                screenshot = listOf("media.trakt.tv/images/episodes/000/001/002/screenshots/medium/still.jpg.webp"),
            ),
        )
        val progress = ShowProgress(
            nextEpisode = Episode(season = 1, number = 2, ids = TraktIds(trakt = 102)),
        )

        val detail = buildShowDetail(
            show,
            progress,
            listOf(season(1, listOf(episode(1, 1), withArt))),
            fetchedAt = 1L,
        )

        assertEquals(withArt, detail.nextEpisode)
        assertEquals(withArt.images, detail.nextEpisode?.images)
    }

    @Test
    fun `season progress counts take precedence over episode flags`() {
        val seasons = listOf(season(1, listOf(episode(1, 1), episode(1, 2))))
        val progress = ShowProgress(
            seasons = listOf(
                SeasonProgress(
                    number = 1,
                    aired = 5,
                    completed = 1,
                    episodes = listOf(
                        EpisodeProgress(number = 1, completed = true),
                        EpisodeProgress(number = 2, completed = true),
                    ),
                ),
            ),
        )

        val detail = buildShowDetail(show, progress, seasons, fetchedAt = 1L)

        assertEquals(1, detail.seasons.single().completed)
        assertEquals(5, detail.seasons.single().aired)
        assertEquals(2, detail.seasons.single().episodes.count { it.completed })
    }
}
