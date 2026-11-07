package ca.gibert.tvtracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TraktIds(
    val trakt: Int,
    val slug: String? = null,
    val tvdb: Int? = null,
    val imdb: String? = null,
    val tmdb: Int? = null,
)

@Serializable
data class Images(
    val fanart: List<String> = emptyList(),
    val poster: List<String> = emptyList(),
    val logo: List<String> = emptyList(),
    val clearart: List<String> = emptyList(),
    val banner: List<String> = emptyList(),
    val thumb: List<String> = emptyList(),
    val screenshot: List<String> = emptyList(),
)

@Serializable
data class Show(
    val title: String,
    val year: Int? = null,
    val ids: TraktIds,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("first_aired") val firstAired: String? = null,
    @SerialName("last_aired") val lastAired: String? = null,
    val runtime: Int? = null,
    val network: String? = null,
    val country: String? = null,
    val status: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val genres: List<String> = emptyList(),
    @SerialName("aired_episodes") val airedEpisodes: Int? = null,
    val images: Images? = null,
)

@Serializable
data class Episode(
    val season: Int,
    val number: Int,
    val title: String? = null,
    @SerialName("first_aired") val firstAired: String? = null,
    val runtime: Int? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    @SerialName("episode_type") val episodeType: String? = null,
    val images: Images? = null,
    val ids: TraktIds,
)

@Serializable
data class Progress(
    val aired: Int = 0,
    val completed: Int = 0,
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
    @SerialName("reset_at") val resetAt: String? = null,
    @SerialName("next_episode") val nextEpisode: Episode? = null,
    @SerialName("last_episode") val lastEpisode: Episode? = null,
)

@Serializable
data class EpisodeProgress(
    val number: Int,
    val completed: Boolean = false,
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
)

@Serializable
data class SeasonProgress(
    val number: Int,
    val title: String? = null,
    val aired: Int = 0,
    val completed: Int = 0,
    val episodes: List<EpisodeProgress> = emptyList(),
)

@Serializable
data class ShowProgress(
    val aired: Int = 0,
    val completed: Int = 0,
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
    @SerialName("next_episode") val nextEpisode: Episode? = null,
    @SerialName("last_episode") val lastEpisode: Episode? = null,
    val seasons: List<SeasonProgress> = emptyList(),
)

@Serializable
data class Season(
    val number: Int,
    val title: String? = null,
    @SerialName("aired_episodes") val airedEpisodes: Int? = null,
    val episodes: List<Episode> = emptyList(),
)

@Serializable
data class UpNextItem(
    val show: Show,
    val progress: Progress,
)

@Serializable
data class WatchedShow(
    val show: Show,
    val seasons: List<WatchedSeason> = emptyList(),
)

@Serializable
data class WatchedSeason(
    val number: Int,
    val episodes: List<WatchedEpisode> = emptyList(),
)

@Serializable
data class WatchedEpisode(
    val number: Int,
)

@Serializable
data class WatchlistItem(
    val rank: Int? = null,
    val id: Long? = null,
    @SerialName("listed_at") val listedAt: String? = null,
    val type: String? = null,
    val show: Show? = null,
)

@Serializable
data class CalendarItem(
    @SerialName("first_aired") val firstAired: String,
    val episode: Episode,
    val show: Show,
)

@Serializable
sealed interface WatchItem {
    @Serializable
    @SerialName("up_next")
    data class UpNext(
        val show: Show,
        val progress: Progress,
    ) : WatchItem

    @Serializable
    @SerialName("watchlisted")
    data class Watchlisted(
        val show: Show,
    ) : WatchItem
}

@Serializable
data class DetailEpisode(
    val episode: Episode,
    val completed: Boolean = false,
)

@Serializable
data class DetailSeason(
    val number: Int,
    val title: String? = null,
    val aired: Int = 0,
    val completed: Int = 0,
    val episodes: List<DetailEpisode> = emptyList(),
)

@Serializable
data class ShowDetail(
    val show: Show,
    @SerialName("next_episode") val nextEpisode: Episode? = null,
    @SerialName("aired_episodes") val airedEpisodes: Int = 0,
    @SerialName("completed_episodes") val completedEpisodes: Int = 0,
    val seasons: List<DetailSeason> = emptyList(),
    @SerialName("fetched_at") val fetchedAt: Long = 0,
)

@Serializable
data class CachedToWatch(
    val items: List<WatchItem> = emptyList(),
    @SerialName("fetched_at") val fetchedAt: Long = 0,
)

@Serializable
data class CachedUpcoming(
    val items: List<CalendarItem> = emptyList(),
    @SerialName("fetched_at") val fetchedAt: Long = 0,
)

@Serializable
data class User(
    val username: String,
    val name: String? = null,
    val private: Boolean = false,
    val vip: Boolean = false,
    val images: UserImages? = null,
)

@Serializable
data class UserImages(
    val avatar: Avatar? = null,
)

@Serializable
data class Avatar(
    val full: String? = null,
)

@Serializable
data class UserSettings(
    val user: User? = null,
)

@Serializable
data class DeviceCodeRequest(
    @SerialName("client_id") val clientId: String,
)

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
)

@Serializable
data class DeviceTokenRequest(
    val code: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String? = null,
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String? = null,
    @SerialName("grant_type") val grantType: String = "refresh_token",
)

@Serializable
data class RevokeRequest(
    val token: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String,
    val scope: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
)

@Serializable
data class SyncEpisode(
    val ids: TraktIds,
)

@Serializable
data class SyncEpisodesRequest(
    val episodes: List<SyncEpisode>,
)

@Serializable
data class SyncCounts(
    val movies: Int = 0,
    val shows: Int = 0,
    val seasons: Int = 0,
    val episodes: Int = 0,
)

@Serializable
data class SyncNotFound(
    val movies: List<SyncEpisode> = emptyList(),
    val shows: List<SyncEpisode> = emptyList(),
    val seasons: List<SyncEpisode> = emptyList(),
    val episodes: List<SyncEpisode> = emptyList(),
)

@Serializable
data class SyncResponse(
    val added: SyncCounts? = null,
    val deleted: SyncCounts? = null,
    val existing: SyncCounts? = null,
    @SerialName("not_found") val notFound: SyncNotFound? = null,
)
