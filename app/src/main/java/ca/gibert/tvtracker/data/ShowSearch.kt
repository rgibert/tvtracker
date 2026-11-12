package ca.gibert.tvtracker.data

/** Shortest query worth sending to Trakt; one letter matches almost everything. */
const val MIN_SEARCH_LENGTH = 2

/** Trims and collapses whitespace, or returns null when it is too short to search. */
fun normalizeSearchQuery(raw: String): String? {
    val query = raw.trim().replace(Regex("\\s+"), " ")
    return query.takeIf { it.length >= MIN_SEARCH_LENGTH }
}

/**
 * Keeps the shows from a Trakt search response in Trakt's relevance order,
 * dropping entries without a show and any duplicate ids.
 */
fun searchResultShows(results: List<SearchResult>): List<Show> {
    val seen = HashSet<Int>()
    return results.mapNotNull { it.show }.filter { seen.add(it.ids.trakt) }
}

/** Ids of every show already on the To Watch list (watching or watchlisted). */
fun queuedShowIds(items: List<WatchItem>): Set<Int> = items.mapTo(HashSet()) { item ->
    when (item) {
        is WatchItem.UpNext -> item.show.ids.trakt
        is WatchItem.Watchlisted -> item.show.ids.trakt
    }
}
