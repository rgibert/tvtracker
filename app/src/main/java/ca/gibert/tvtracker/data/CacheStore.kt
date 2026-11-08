package ca.gibert.tvtracker.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.YearMonth

/**
 * Small JSON-file cache. It is deliberately simpler than a database: these are
 * read-mostly snapshots that can be re-fetched at any time.
 */
class CacheStore(
    context: Context,
    private val json: Json,
) {

    private val directory = File(context.filesDir, "snapshots")
    private val mutex = Mutex()

    suspend fun readToWatch(): CachedToWatch? =
        read(TO_WATCH_FILE, CachedToWatch.serializer())

    suspend fun writeToWatch(value: CachedToWatch) =
        write(TO_WATCH_FILE, CachedToWatch.serializer(), value)

    suspend fun readUpcoming(): CachedUpcoming? =
        read(UPCOMING_FILE, CachedUpcoming.serializer())

    suspend fun writeUpcoming(value: CachedUpcoming) =
        write(UPCOMING_FILE, CachedUpcoming.serializer(), value)

    suspend fun readUpcomingMonth(month: YearMonth): CachedUpcoming? =
        read(upcomingMonthFile(month), CachedUpcoming.serializer())

    suspend fun writeUpcomingMonth(month: YearMonth, value: CachedUpcoming) =
        write(upcomingMonthFile(month), CachedUpcoming.serializer(), value)

    suspend fun readShowDetail(showId: Int): ShowDetail? =
        read(detailFile(showId), ShowDetail.serializer())

    suspend fun writeShowDetail(showId: Int, value: ShowDetail) =
        write(detailFile(showId), ShowDetail.serializer(), value)

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            directory.deleteRecursively()
            Unit
        }
    }

    private suspend fun <T> read(name: String, serializer: KSerializer<T>): T? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = File(directory, name)
                if (!file.isFile) return@withLock null
                runCatching {
                    json.decodeFromString(serializer, file.readText())
                }.getOrNull()
            }
        }

    private suspend fun <T> write(name: String, serializer: KSerializer<T>, value: T) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                directory.mkdirs()
                val target = File(directory, name)
                val temp = File(directory, "$name.tmp")
                temp.writeText(json.encodeToString(serializer, value))
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
        }

    private fun detailFile(showId: Int) = "show_$showId.json"

    private fun upcomingMonthFile(month: YearMonth) = "upcoming_$month.json"

    private companion object {
        const val TO_WATCH_FILE = "to_watch.json"
        const val UPCOMING_FILE = "upcoming.json"
    }
}
