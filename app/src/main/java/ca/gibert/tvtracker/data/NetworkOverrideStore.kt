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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.networkOverridesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "network_overrides",
)

/**
 * A channel the user pinned for a show in place of the one Trakt reports.
 *
 * Trakt lists the original network, which is often not where a show airs for
 * the viewer: a BBC co-production can be on HBO in North America. [title] is
 * kept so Settings can list overrides without a network round trip.
 */
@Serializable
data class NetworkOverride(
    val trakt: Int,
    val title: String,
    val network: String,
)

/**
 * Per-show channel overrides.
 *
 * Like [HiddenShowStore], this is a preference rather than cached data, so
 * "clear cached shows" leaves it alone; it is dropped when the account is
 * disconnected.
 */
class NetworkOverrideStore(
    private val context: Context,
    private val json: Json,
) {

    private val preferences: Flow<Preferences> = context.networkOverridesDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    val overrides: Flow<List<NetworkOverride>> =
        preferences.map { decodeNetworkOverrides(json, it[KEY]) }

    suspend fun read(): List<NetworkOverride> = overrides.first()

    /** Show id → pinned channel name, ready for [withNetworkOverride]. */
    suspend fun readMap(): Map<Int, String> = read().toOverrideMap()

    /** Pins [network] for [show]; a blank name removes the override instead. */
    suspend fun set(show: Show, network: String) {
        val name = network.trim()
        write { overrides ->
            val others = overrides.filterNot { it.trakt == show.ids.trakt }
            if (name.isEmpty()) {
                others
            } else {
                others + NetworkOverride(trakt = show.ids.trakt, title = show.title, network = name)
            }
        }
    }

    suspend fun remove(showId: Int) = write { overrides ->
        overrides.filterNot { it.trakt == showId }
    }

    suspend fun clear() {
        context.networkOverridesDataStore.edit { it.remove(KEY) }
    }

    private suspend fun write(transform: (List<NetworkOverride>) -> List<NetworkOverride>) {
        context.networkOverridesDataStore.edit { preferences ->
            val updated = transform(decodeNetworkOverrides(json, preferences[KEY]))
            if (updated.isEmpty()) {
                preferences.remove(KEY)
            } else {
                preferences[KEY] = encodeNetworkOverrides(json, updated)
            }
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("overrides")
    }
}

internal fun List<NetworkOverride>.toOverrideMap(): Map<Int, String> =
    asSequence()
        .filter { it.network.isNotBlank() }
        .associate { it.trakt to it.network.trim() }

/** The show as it should be displayed: with its pinned channel, if any. */
fun Show.withNetworkOverride(overrides: Map<Int, String>): Show {
    val pinned = overrides[ids.trakt] ?: return this
    return if (pinned == network) this else copy(network = pinned)
}

fun WatchItem.withNetworkOverride(overrides: Map<Int, String>): WatchItem {
    if (overrides.isEmpty()) return this
    return when (this) {
        is WatchItem.UpNext -> {
            val updated = show.withNetworkOverride(overrides)
            if (updated === show) this else copy(show = updated)
        }

        is WatchItem.Watchlisted -> {
            val updated = show.withNetworkOverride(overrides)
            if (updated === show) this else copy(show = updated)
        }
    }
}

fun CalendarItem.withNetworkOverride(overrides: Map<Int, String>): CalendarItem {
    if (overrides.isEmpty()) return this
    val updated = show.withNetworkOverride(overrides)
    return if (updated === show) this else copy(show = updated)
}

/**
 * The stored payload is JSON in a preferences file, so a hand-edited or
 * truncated value must not take the screen down with it.
 */
internal fun decodeNetworkOverrides(json: Json, raw: String?): List<NetworkOverride> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        json.decodeFromString(ListSerializer(NetworkOverride.serializer()), raw)
    }.getOrNull().orEmpty()
}

internal fun encodeNetworkOverrides(json: Json, overrides: List<NetworkOverride>): String =
    json.encodeToString(ListSerializer(NetworkOverride.serializer()), overrides)
