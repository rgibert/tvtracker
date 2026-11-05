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

private val Context.hiddenShowsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hidden_shows",
)

@Serializable
data class HiddenShow(
    val trakt: Int,
    val title: String,
)

/**
 * Shows the user chose to keep out of To Watch and Coming Up.
 *
 * This lives in DataStore rather than the snapshot cache on purpose: it is a
 * preference, so "clear cached shows" leaves it alone. It is dropped when the
 * account is disconnected, because the ids belong to that Trakt account.
 */
class HiddenShowStore(
    private val context: Context,
    private val json: Json,
) {

    private val preferences: Flow<Preferences> = context.hiddenShowsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    val shows: Flow<List<HiddenShow>> = preferences.map { decodeHiddenShows(json, it[KEY]) }

    suspend fun read(): List<HiddenShow> = shows.first()

    suspend fun readIds(): Set<Int> = read().mapTo(mutableSetOf()) { it.trakt }

    suspend fun hide(show: Show) = write { shows ->
        shows.filterNot { it.trakt == show.ids.trakt } +
            HiddenShow(trakt = show.ids.trakt, title = show.title)
    }

    suspend fun unhide(showId: Int) = write { shows ->
        shows.filterNot { it.trakt == showId }
    }

    suspend fun clear() {
        context.hiddenShowsDataStore.edit { it.remove(KEY) }
    }

    private suspend fun write(transform: (List<HiddenShow>) -> List<HiddenShow>) {
        context.hiddenShowsDataStore.edit { preferences ->
            val updated = transform(decodeHiddenShows(json, preferences[KEY]))
            if (updated.isEmpty()) {
                preferences.remove(KEY)
            } else {
                preferences[KEY] = encodeHiddenShows(json, updated)
            }
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("shows")
    }
}

/**
 * The stored payload is JSON in a preferences file, so a hand-edited or
 * truncated value must not take the screen down with it.
 */
internal fun decodeHiddenShows(json: Json, raw: String?): List<HiddenShow> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        json.decodeFromString(ListSerializer(HiddenShow.serializer()), raw)
    }.getOrNull().orEmpty()
}

internal fun encodeHiddenShows(json: Json, shows: List<HiddenShow>): String =
    json.encodeToString(ListSerializer(HiddenShow.serializer()), shows)
