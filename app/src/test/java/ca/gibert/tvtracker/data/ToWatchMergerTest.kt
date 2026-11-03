package ca.gibert.tvtracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToWatchMergerTest {

    private fun show(id: Int, title: String) = Show(
        title = title,
        year = 2020,
        ids = TraktIds(trakt = id),
    )

    private fun upNext(id: Int, title: String, nextEpisodeNumber: Int?): UpNextItem =
        UpNextItem(
            show = show(id, title),
            progress = Progress(
                aired = 10,
                completed = 4,
                nextEpisode = nextEpisodeNumber?.let { number ->
                    Episode(
                        season = 1,
                        number = number,
                        title = "Episode $number",
                        ids = TraktIds(trakt = id * 100 + number),
                    )
                },
            ),
        )

    @Test
    fun `up next items keep Trakt order and include progress`() {
        val result = mergeToWatch(
            upNext = listOf(
                upNext(3, "Zebra", 2),
                upNext(1, "Alpha", 5),
            ),
            watchlist = emptyList(),
        )

        assertEquals(2, result.size)
        assertEquals("Zebra", result[0].show.title)
        assertEquals("Alpha", result[1].show.title)
        assertEquals(5, (result[1] as WatchItem.UpNext).progress.nextEpisode?.number)
    }

    @Test
    fun `up next entries without a next episode are skipped`() {
        val result = mergeToWatch(
            upNext = listOf(
                upNext(1, "Caught up", null),
                upNext(2, "Still watching", 1),
            ),
            watchlist = emptyList(),
        )

        assertEquals(1, result.size)
        assertEquals("Still watching", result.single().show.title)
    }

    @Test
    fun `watchlist shows already in up next are not repeated`() {
        val result = mergeToWatch(
            upNext = listOf(upNext(1, "Both lists", 1)),
            watchlist = listOf(
                WatchlistItem(show = show(1, "Both lists")),
                WatchlistItem(show = show(2, "Only watchlist")),
            ),
        )

        assertEquals(2, result.size)
        assertEquals(listOf("Both lists", "Only watchlist"), result.map { it.show.title })
        assertTrue(result.last() is WatchItem.Watchlisted)
    }

    @Test
    fun `watchlist only shows are sorted alphabetically`() {
        val result = mergeToWatch(
            upNext = emptyList(),
            watchlist = listOf(
                WatchlistItem(show = show(1, "beta show")),
                WatchlistItem(show = show(2, "Alpha show")),
                WatchlistItem(show = null),
            ),
        )

        assertEquals(listOf("Alpha show", "beta show"), result.map { it.show.title })
        assertTrue(result.all { it is WatchItem.Watchlisted })
    }

    @Test
    fun `duplicate watchlist entries are removed`() {
        val result = mergeToWatch(
            upNext = emptyList(),
            watchlist = listOf(
                WatchlistItem(show = show(1, "Same")),
                WatchlistItem(show = show(1, "Same")),
            ),
        )

        assertEquals(1, result.size)
    }

    private val WatchItem.show: Show
        get() = when (this) {
            is WatchItem.UpNext -> this.show
            is WatchItem.Watchlisted -> this.show
        }
}
