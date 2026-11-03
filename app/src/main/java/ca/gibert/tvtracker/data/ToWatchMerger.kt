package ca.gibert.tvtracker.data

import java.util.Locale

/**
 * Combines Trakt's up-next feed with shows that are only on the watchlist.
 * A show that already has an up-next entry is never repeated in the watchlist
 * section. Watchlist-only shows are sorted alphabetically so they stay stable
 * between refreshes.
 */
internal fun mergeToWatch(
    upNext: List<UpNextItem>,
    watchlist: List<WatchlistItem>,
): List<WatchItem> {
    val seen = HashSet<Int>()
    val items = ArrayList<WatchItem>(upNext.size + watchlist.size)

    upNext.forEach { item ->
        if (item.progress.nextEpisode == null) return@forEach
        if (seen.add(item.show.ids.trakt)) {
            items += WatchItem.UpNext(item.show, item.progress)
        }
    }

    watchlist.asSequence()
        .mapNotNull { it.show }
        .filter { seen.add(it.ids.trakt) }
        .sortedBy { it.title.lowercase(Locale.ROOT) }
        .forEach { items += WatchItem.Watchlisted(it) }

    return items
}
