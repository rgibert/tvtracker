package ca.gibert.tvtracker.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkOverrideStoreTest {

    // Mirrors the Json configuration AppContainer builds the store with.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    private fun show(id: Int, network: String?) = Show(
        title = "Show $id",
        ids = TraktIds(trakt = id),
        network = network,
    )

    @Test
    fun roundTripsTheStoredList() {
        val overrides = listOf(
            NetworkOverride(trakt = 1, title = "Doctor Who", network = "Disney+"),
            NetworkOverride(trakt = 2, title = "Industry", network = "HBO"),
        )

        assertEquals(
            overrides,
            decodeNetworkOverrides(json, encodeNetworkOverrides(json, overrides)),
        )
    }

    @Test
    fun treatsAMissingOrCorruptPayloadAsEmpty() {
        assertTrue(decodeNetworkOverrides(json, null).isEmpty())
        assertTrue(decodeNetworkOverrides(json, "").isEmpty())
        assertTrue(decodeNetworkOverrides(json, "[{\"trakt\": 1, \"title\": \"Doc").isEmpty())
    }

    @Test
    fun mapSkipsBlankNamesAndTrims() {
        val map = listOf(
            NetworkOverride(trakt = 1, title = "A", network = "  HBO "),
            NetworkOverride(trakt = 2, title = "B", network = "   "),
        ).toOverrideMap()

        assertEquals(mapOf(1 to "HBO"), map)
    }

    @Test
    fun pinnedChannelReplacesTraktsNetwork() {
        val overridden = show(1, "BBC One").withNetworkOverride(mapOf(1 to "HBO"))

        assertEquals("HBO", overridden.network)
    }

    @Test
    fun pinnedChannelFillsAMissingNetwork() {
        assertEquals("HBO", show(1, null).withNetworkOverride(mapOf(1 to "HBO")).network)
    }

    @Test
    fun showsWithoutAnOverrideAreReturnedAsIs() {
        val original = show(2, "BBC One")

        assertSame(original, original.withNetworkOverride(mapOf(1 to "HBO")))
    }

    @Test
    fun appliesToWatchAndCalendarItems() {
        val overrides = mapOf(1 to "HBO")
        val upNext = WatchItem.UpNext(show = show(1, "BBC One"), progress = Progress())
        val watchlisted = WatchItem.Watchlisted(show = show(1, "BBC One"))
        val calendar = CalendarItem(
            firstAired = "2026-11-08T21:00:00.000Z",
            episode = Episode(season = 1, number = 1, ids = TraktIds(trakt = 10)),
            show = show(1, "BBC One"),
        )

        assertEquals(
            "HBO",
            (upNext.withNetworkOverride(overrides) as WatchItem.UpNext).show.network,
        )
        assertEquals(
            "HBO",
            (watchlisted.withNetworkOverride(overrides) as WatchItem.Watchlisted).show.network,
        )
        assertEquals("HBO", calendar.withNetworkOverride(overrides).show.network)
        assertSame(calendar, calendar.withNetworkOverride(emptyMap()))
    }
}
