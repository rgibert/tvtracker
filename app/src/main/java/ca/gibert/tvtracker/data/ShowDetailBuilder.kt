package ca.gibert.tvtracker.data

import ca.gibert.tvtracker.util.isAired

/**
 * Merges the per-season episode metadata from `/shows/:id/seasons` with the
 * watched flags from `/shows/:id/progress/watched`.
 */
internal fun buildShowDetail(
    show: Show,
    progress: ShowProgress,
    seasons: List<Season>,
    fetchedAt: Long,
): ShowDetail {
    val progressBySeason = progress.seasons.associateBy { it.number }

    val detailSeasons = seasons
        .asSequence()
        .filter { it.number > 0 }
        .mapNotNull { season ->
            val seasonProgress = progressBySeason[season.number]
            val completedByNumber = seasonProgress
                ?.episodes
                ?.associateBy { it.number }
                .orEmpty()

            val episodes = season.episodes
                .asSequence()
                .filter { it.season > 0 }
                .sortedBy { it.number }
                .map { episode ->
                    DetailEpisode(
                        episode = episode,
                        completed = completedByNumber[episode.number]?.completed == true,
                    )
                }
                .toList()

            if (episodes.isEmpty()) return@mapNotNull null

            DetailSeason(
                number = season.number,
                title = season.title ?: seasonProgress?.title,
                aired = seasonProgress?.aired ?: episodes.count { isAired(it.episode.firstAired) },
                completed = seasonProgress?.completed ?: episodes.count { it.completed },
                episodes = episodes,
            )
        }
        .sortedBy { it.number }
        .toList()

    return ShowDetail(
        show = show,
        nextEpisode = progress.nextEpisode,
        airedEpisodes = progress.aired,
        completedEpisodes = progress.completed,
        seasons = detailSeasons,
        fetchedAt = fetchedAt,
    )
}
