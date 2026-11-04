package ca.gibert.tvtracker.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraktJsonTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun `decodes an up next response`() {
        val payload = """
            [
              {
                "show": {
                  "title": "Severance",
                  "year": 2022,
                  "ids": {"trakt": 154909, "slug": "severance"},
                  "images": {"poster": ["https://example.com/poster.jpg"]}
                },
                "progress": {
                  "aired": 19,
                  "completed": 18,
                  "next_episode": {
                    "season": 2,
                    "number": 10,
                    "title": "Cold Harbor",
                    "first_aired": "2025-03-21T01:00:00.000Z",
                    "ids": {"trakt": 1234567}
                  }
                }
              }
            ]
        """.trimIndent()

        val items = json.decodeFromString<List<UpNextItem>>(payload)

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("Severance", item.show.title)
        assertEquals(154909, item.show.ids.trakt)
        assertEquals("https://example.com/poster.jpg", item.show.images?.poster?.single())
        assertEquals(18, item.progress.completed)
        assertEquals(2, item.progress.nextEpisode?.season)
        assertEquals(10, item.progress.nextEpisode?.number)
        assertEquals(1234567, item.progress.nextEpisode?.ids?.trakt)
    }

    @Test
    fun `decodes calendar items and tolerates unknown fields`() {
        val payload = """
            [
              {
                "first_aired": "2025-03-21T01:00:00.000Z",
                "episode": {
                  "season": 2,
                  "number": 10,
                  "title": "Cold Harbor",
                  "episode_type": "season_finale",
                  "ids": {"trakt": 1234567}
                },
                "show": {
                  "title": "Severance",
                  "ids": {"trakt": 154909}
                },
                "available_on": "streaming"
              }
            ]
        """.trimIndent()

        val items = json.decodeFromString<List<CalendarItem>>(payload)

        assertEquals("2025-03-21T01:00:00.000Z", items.single().firstAired)
        assertEquals("season_finale", items.single().episode.episodeType)
        assertEquals("Severance", items.single().show.title)
    }

    @Test
    fun `decodes watched progress with seasons`() {
        val payload = """
            {
              "aired": 2,
              "completed": 1,
              "next_episode": {"season": 1, "number": 2, "ids": {"trakt": 2}},
              "seasons": [
                {
                  "number": 1,
                  "title": "Season 1",
                  "aired": 2,
                  "completed": 1,
                  "episodes": [
                    {"number": 1, "completed": true, "last_watched_at": "2024-01-01T00:00:00.000Z"},
                    {"number": 2, "completed": false}
                  ]
                }
              ]
            }
        """.trimIndent()

        val progress = json.decodeFromString<ShowProgress>(payload)

        assertEquals(2, progress.aired)
        assertEquals(1, progress.completed)
        assertEquals(1, progress.seasons.size)
        assertTrue(progress.seasons.single().episodes.first().completed)
        assertFalse(progress.seasons.single().episodes.last().completed)
        assertEquals(2, progress.nextEpisode?.number)
    }

    @Test
    fun `decodes device and token responses`() {
        val device = json.decodeFromString<DeviceCodeResponse>(
            """
            {
              "device_code": "device-123",
              "user_code": "ABCD1234",
              "verification_url": "https://trakt.tv/activate",
              "expires_in": 600,
              "interval": 5
            }
            """.trimIndent(),
        )
        val token = json.decodeFromString<TokenResponse>(
            """
            {
              "access_token": "access",
              "token_type": "bearer",
              "expires_in": 604800,
              "refresh_token": "refresh",
              "scope": "public",
              "created_at": 1715000000
            }
            """.trimIndent(),
        )

        assertEquals("ABCD1234", device.userCode)
        assertEquals(600, device.expiresIn)
        assertEquals("access", token.accessToken)
        assertEquals(604800, token.expiresIn)
        assertEquals("refresh", token.refreshToken)
    }

    @Test
    fun `decodes cached to watch items with the polymorphic discriminator`() {
        val payload = """
            {
              "items": [
                {
                  "type": "up_next",
                  "show": {"title": "Severance", "ids": {"trakt": 154909}},
                  "progress": {
                    "aired": 10,
                    "completed": 9,
                    "next_episode": {"season": 2, "number": 1, "ids": {"trakt": 1}}
                  }
                },
                {
                  "type": "watchlisted",
                  "show": {"title": "Slow Horses", "ids": {"trakt": 178259}}
                }
              ],
              "fetched_at": 123
            }
        """.trimIndent()

        val cached = json.decodeFromString<CachedToWatch>(payload)

        assertEquals(2, cached.items.size)
        assertTrue(cached.items[0] is WatchItem.UpNext)
        assertTrue(cached.items[1] is WatchItem.Watchlisted)
        assertEquals(1, (cached.items[0] as WatchItem.UpNext).progress.nextEpisode?.number)
        assertEquals(123L, cached.fetchedAt)
    }

    @Test
    fun `decodes episode screenshots from season responses`() {
        val payload = """
            [
              {
                "number": 1,
                "title": "Season 1",
                "episodes": [
                  {
                    "season": 1,
                    "number": 1,
                    "title": "Pilot",
                    "ids": {"trakt": 101},
                    "images": {
                      "screenshot": [
                        "media.trakt.tv/images/episodes/000/000/101/screenshots/medium/still.jpg.webp"
                      ]
                    }
                  }
                ]
              }
            ]
        """.trimIndent()

        val seasons = json.decodeFromString<List<Season>>(payload)

        val still = seasons.single().episodes.single().images?.screenshot?.single()
        assertEquals(
            "media.trakt.tv/images/episodes/000/000/101/screenshots/medium/still.jpg.webp",
            still,
        )
    }

    @Test
    fun `round trips a cached show detail`() {
        val detail = ShowDetail(
            show = Show(title = "Test", ids = TraktIds(trakt = 9)),
            nextEpisode = Episode(season = 1, number = 2, ids = TraktIds(trakt = 102)),
            airedEpisodes = 2,
            completedEpisodes = 1,
            seasons = listOf(
                DetailSeason(
                    number = 1,
                    title = "Season 1",
                    aired = 2,
                    completed = 1,
                    episodes = listOf(
                        DetailEpisode(
                            episode = Episode(
                                season = 1,
                                number = 1,
                                title = "Pilot",
                                ids = TraktIds(trakt = 101),
                            ),
                            completed = true,
                        ),
                    ),
                ),
            ),
            fetchedAt = 123L,
        )

        val decoded = json.decodeFromString<ShowDetail>(json.encodeToString(detail))

        assertEquals(detail, decoded)
    }
}
