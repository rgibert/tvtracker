package ca.gibert.tvtracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexSettingsTest {

    @Test
    fun bareAddressesGetHttp() {
        assertEquals("http://192.168.1.10:32400", normalizePlexAddress(" 192.168.1.10:32400 "))
    }

    @Test
    fun webAppPathsAndRoutesAreStripped() {
        assertEquals(
            "http://nas.local:32400",
            normalizePlexAddress("http://nas.local:32400/web/index.html#!/settings"),
        )
        assertEquals("http://nas.local:32400", normalizePlexAddress("http://nas.local:32400/web/"))
        assertEquals("https://app.plex.tv", normalizePlexAddress("https://app.plex.tv/desktop/"))
    }

    @Test
    fun blankOrHostlessAddressesAreRejected() {
        assertNull(normalizePlexAddress(""))
        assertNull(normalizePlexAddress("   "))
        assertNull(normalizePlexAddress("http://"))
    }

    @Test
    fun selfHostedServersUseTheBundledWebApp() {
        assertEquals(
            "http://192.168.1.10:32400/web/index.html#!/search?query=Doctor%20Who",
            plexSearchUrl("192.168.1.10:32400", "Doctor Who"),
        )
    }

    @Test
    fun noAddressFallsBackToTheHostedApp() {
        assertEquals(
            "https://app.plex.tv/desktop/#!/search?query=Industry",
            plexSearchUrl(null, "Industry"),
        )
    }

    @Test
    fun titlesAreUrlEncoded() {
        assertEquals(
            "https://app.plex.tv/desktop/#!/search?query=Law%20%26%20Order%3A%20SVU",
            plexSearchUrl(null, "Law & Order: SVU"),
        )
    }

    @Test
    fun plexChannelMatchesLikeTheBadge() {
        assertTrue(isPlexChannel("Plex"))
        assertTrue(isPlexChannel("My Plex server"))
        assertFalse(isPlexChannel("HBO"))
        assertFalse(isPlexChannel(null))
    }
}
