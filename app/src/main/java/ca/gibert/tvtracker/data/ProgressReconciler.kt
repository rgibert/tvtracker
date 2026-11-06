package ca.gibert.tvtracker.data

/**
 * Recomputes the To Watch progress counts from data that cannot drift.
 *
 * Trakt's `sync/progress/up_next` aggregates (`aired`/`completed`) have been
 * observed to count positionally: every episode before the latest watched one
 * is treated as watched, so a show watched out of order reports a full bar.
 * The per-episode `completed` flags from `/shows/:id/progress/watched` are the
 * same source the show-detail screen uses for its checkmarks, so they are the
 * ground truth here. The denominator comes from the show's own
 * `aired_episodes` metadata, falling back to the up-next aggregate.
 *
 * Everything the card still needs from up-next (the next and last episode,
 * timestamps) is preserved untouched.
 */
internal fun reconcileProgress(
    show: Show,
    upNext: Progress,
    detail: ShowProgress?,
): Progress {
    val seasons = detail?.seasons
        .orEmpty()
        .filter { it.number > 0 && it.episodes.isNotEmpty() }

    val completed = seasons
        .sumOf { season -> season.episodes.count { it.completed } }
        .takeIf { seasons.isNotEmpty() } ?: upNext.completed

    val aired = show.airedEpisodes?.takeIf { it > 0 } ?: upNext.aired

    return upNext.copy(completed = completed, aired = aired)
}
