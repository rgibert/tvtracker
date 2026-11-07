package ca.gibert.tvtracker.data

/**
 * Recomputes the To Watch progress counts from data that cannot drift.
 *
 * Trakt's `sync/progress/up_next` aggregates (`aired`/`completed`) have been
 * observed to count positionally: every episode before the latest watched one
 * is treated as watched, so a show watched out of order reports a full bar.
 * The watched episode data from `/sync/watched/shows` is the same source the
 * show-detail screen uses for its checkmarks, so it is the ground truth here.
 * The denominator comes from the show's own `aired_episodes` metadata, falling
 * back to the up-next aggregate.
 *
 * Everything the card still needs from up-next (the next and last episode,
 * timestamps) is preserved untouched.
 */
internal fun reconcileProgressFromCount(
    show: Show,
    upNext: Progress,
    completed: Int?,
): Progress {
    val aired = show.airedEpisodes?.takeIf { it > 0 } ?: upNext.aired

    return upNext.copy(
        completed = completed ?: upNext.completed,
        aired = aired,
    )
}

/**
 * Counts distinct watched episodes from a bulk `/sync/watched/shows` entry.
 *
 * Season 0 (specials) is excluded to match the per-show progress request the
 * app used before. An empty season list means the response did not include
 * progress data, so the caller should fall back to the up-next aggregate
 * instead of reporting zero.
 */
internal fun WatchedShow.completedEpisodeCount(): Int? {
    if (seasons.isEmpty()) return null

    return seasons
        .filter { it.number > 0 }
        .sumOf { season -> season.episodes.size }
}
