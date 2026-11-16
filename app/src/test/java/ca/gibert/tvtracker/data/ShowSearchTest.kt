package ca.gibert.tvtracker.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowSearchTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    private fun show(id: Int, title: String = "Show $id") =
        Show(title = title, ids = TraktIds(trakt = id))

    @Test
    fun `normalizes queries and rejects ones too short to search`() {
        assertEquals("the bear", normalizeSearchQuery("  the   bear "))
        assertEquals("ab", normalizeSearchQuery("ab"))
        assertNull(normalizeSearchQuery(""))
        assertNull(normalizeSearchQuery("   "))
        assertNull(normalizeSearchQuery(" a "))
    }

    @Test
    fun `keeps search order and drops missing or duplicate shows`() {
        val results = listOf(
            SearchResult(type = "show", score = 900.0, show = show(3)),
            SearchResult(type = "show", score = 800.0, show = null),
            SearchResult(type = "show", score = 700.0, show = show(1)),
            SearchResult(type = "show", score = 600.0, show = show(3, "Duplicate")),
        )

        val shows = searchResultShows(results)

        assertEquals(listOf(3, 1), shows.map { it.ids.trakt })
        assertEquals("Show 3", shows.first().title)
    }

    @Test
    fun `collects ids of watching and watchlisted shows`() {
        val items = listOf(
            WatchItem.UpNext(show = show(5), progress = Progress()),
            WatchItem.Watchlisted(show = show(9)),
        )

        assertEquals(setOf(5, 9), queuedShowIds(items))
    }

    @Test
    fun `decodes a show search response`() {
        val payload = """
            [
              {
                "type": "show",
                "score": 1043.5,
                "show": {
                  "title": "The Bear",
                  "year": 2022,
                  "ids": {"trakt": 196200, "slug": "the-bear", "tmdb": 136315},
                  "network": "FX",
                  "overview": "A young chef returns home.",
                  "images": {"poster": ["media.trakt.tv/images/shows/poster.jpg"]},
                  "unknown_field": true
                }
              }
            ]
        """.trimIndent()

        val shows = searchResultShows(json.decodeFromString<List<SearchResult>>(payload))

        val show = shows.single()
        assertEquals("The Bear", show.title)
        assertEquals(196200, show.ids.trakt)
        assertEquals("FX", show.network)
        assertEquals(2022, show.year)
    }

    @Test
    fun `encodes a watchlist add request with only the trakt id`() {
        val body = json.encodeToString(
            SyncShowsRequest.serializer(),
            SyncShowsRequest(listOf(SyncShow(TraktIds(trakt = 196200)))),
        )

        assertEquals("""{"shows":[{"ids":{"trakt":196200}}]}""", body)
    }
}
