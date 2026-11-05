package ca.gibert.tvtracker.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenShowStoreTest {

    // Mirrors the Json configuration AppContainer builds the store with.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun roundTripsTheStoredList() {
        val shows = listOf(
            HiddenShow(trakt = 1399, title = "Game of Thrones"),
            HiddenShow(trakt = 1408, title = "House of the Dragon"),
        )

        assertEquals(shows, decodeHiddenShows(json, encodeHiddenShows(json, shows)))
    }

    @Test
    fun treatsAMissingPayloadAsAnEmptyList() {
        assertEquals(emptyList<HiddenShow>(), decodeHiddenShows(json, null))
        assertEquals(emptyList<HiddenShow>(), decodeHiddenShows(json, ""))
    }

    @Test
    fun survivesACorruptPayload() {
        val decoded = decodeHiddenShows(json, "[{\"trakt\": 1399, \"title\": \"Game of")

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun ignoresFieldsAddedByALaterVersion() {
        val decoded = decodeHiddenShows(
            json,
            """[{"trakt": 1399, "title": "Game of Thrones", "hidden_at": 12345}]""",
        )

        assertEquals(listOf(HiddenShow(trakt = 1399, title = "Game of Thrones")), decoded)
    }
}
