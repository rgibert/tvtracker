package ca.gibert.tvtracker.ui.components

import ca.gibert.tvtracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceBadgeTest {

    @Test
    fun `unknown networks fall back to the generic tile`() {
        assertNull(serviceLogoFor("Random Network"))
        assertNull(serviceLogoFor(""))
        assertNull(serviceLogoFor("   "))
        assertNull(serviceLogoFor(null))
    }

    @Test
    fun `hbo max resolves to max rather than hbo`() {
        assertEquals(R.drawable.service_max, serviceLogoFor("HBO Max"))
        assertEquals(R.drawable.service_max, serviceLogoFor("Max"))
    }

    @Test
    fun `plain hbo still resolves to hbo`() {
        assertEquals(R.drawable.service_hbo, serviceLogoFor("HBO"))
        assertEquals(R.drawable.service_hbo, serviceLogoFor("HBO2"))
    }

    @Test
    fun `fx on hulu credits fx not hulu`() {
        assertEquals(R.drawable.service_fx, serviceLogoFor("FX on Hulu"))
        assertEquals(R.drawable.service_fx, serviceLogoFor("FX"))
    }

    @Test
    fun `apple tv plus resolves to the apple tv mark`() {
        assertEquals(R.drawable.service_appletv, serviceLogoFor("Apple TV+"))
        assertEquals(R.drawable.service_appletv, serviceLogoFor("Apple TV"))
        assertEquals(R.drawable.service_appletv, serviceLogoFor("Apple"))
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(R.drawable.service_netflix, serviceLogoFor("NETFLIX"))
        assertEquals(R.drawable.service_netflix, serviceLogoFor("Netflix"))
    }

    @Test
    fun `amazon prime video matches either wording`() {
        assertEquals(R.drawable.service_prime_video, serviceLogoFor("Prime Video"))
        assertEquals(R.drawable.service_prime_video, serviceLogoFor("Amazon"))
    }

    @Test
    fun `every suggested channel has a bundled logo`() {
        suggestedServices.forEach { service ->
            assertNotNull("$service has no logo", serviceLogoFor(service))
        }
    }
}
