package ca.gibert.tvtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.net.URLEncoder

private val Context.plexSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "plex_settings",
)

/**
 * Where "Open in Plex" sends shows whose channel is Plex.
 *
 * It is a device preference (a home server's LAN address is specific to this
 * phone's network), so neither disconnecting the account nor clearing cached
 * shows touches it. With nothing set, Plex's hosted web app is used.
 */
class PlexSettingsStore(private val context: Context) {

    private val preferences: Flow<Preferences> = context.plexSettingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    /** The address the user saved, or null for Plex's hosted web app. */
    val address: Flow<String?> = preferences.map { it[KEY]?.takeIf(String::isNotBlank) }

    suspend fun readAddress(): String? = address.first()

    /** Saves [address] after normalizing it; a blank value goes back to the default. */
    suspend fun setAddress(address: String) {
        val normalized = normalizePlexAddress(address)
        context.plexSettingsDataStore.edit { preferences ->
            if (normalized == null) preferences.remove(KEY) else preferences[KEY] = normalized
        }
    }

    suspend fun clearAddress() {
        context.plexSettingsDataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("address")
    }
}

const val DEFAULT_PLEX_ADDRESS = "https://app.plex.tv"

/**
 * Turns what people type into a base URL: `192.168.1.10:32400`,
 * `http://nas.local:32400/web/index.html` and `https://app.plex.tv/desktop/`
 * all reduce to scheme + host (+ port). A bare host gets `http://`, because a
 * home server reached by address alone rarely has a certificate for it.
 */
fun normalizePlexAddress(raw: String): String? {
    var address = raw.trim()
    if (address.isEmpty()) return null
    if (!address.contains("://")) address = "http://$address"

    // Drop anything after the web app's path, including a "#!/..." route.
    address = address.substringBefore('#').substringBefore('?')
    address = address.trimEnd('/')
    for (suffix in listOf("/web/index.html", "/web", "/desktop")) {
        if (address.endsWith(suffix, ignoreCase = true)) {
            address = address.dropLast(suffix.length).trimEnd('/')
            break
        }
    }

    val host = address.substringAfter("://", missingDelimiterValue = "").substringBefore('/')
    return if (host.isBlank()) null else address
}

/**
 * A link that opens Plex's web app on a search for [title].
 *
 * The hosted app lives under `/desktop`; a self-hosted server serves the same
 * app under `/web/index.html`. Both use the `#!/search` route.
 */
fun plexSearchUrl(address: String?, title: String): String {
    val base = address?.let(::normalizePlexAddress) ?: DEFAULT_PLEX_ADDRESS
    val host = base.substringAfter("://").substringBefore('/').substringBefore(':')
    val webApp = if (host.equals("app.plex.tv", ignoreCase = true)) {
        "$base/desktop/"
    } else {
        "$base/web/index.html"
    }
    val query = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
    return "$webApp#!/search?query=$query"
}

/** Whether a channel name means Plex, matching how its badge is chosen. */
fun isPlexChannel(network: String?): Boolean =
    network?.contains("plex", ignoreCase = true) == true
