package ca.gibert.tvtracker.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {

    @POST("oauth/device/code")
    suspend fun createDeviceCode(@Body body: DeviceCodeRequest): DeviceCodeResponse

    @POST("oauth/device/token")
    suspend fun pollDeviceToken(@Body body: DeviceTokenRequest): TokenResponse

    @POST("oauth/token")
    suspend fun refreshToken(@Body body: RefreshTokenRequest): TokenResponse

    @POST("oauth/revoke")
    suspend fun revoke(@Body body: RevokeRequest)
}

interface TraktApi {

    @GET("sync/progress/up_next")
    suspend fun upNext(
        @Query("extended") extended: String,
        @Query("limit") limit: Int,
    ): List<UpNextItem>

    @GET("sync/watchlist/shows/rank/asc")
    suspend fun watchlistShows(
        @Query("extended") extended: String,
        @Query("limit") limit: Int,
    ): List<WatchlistItem>

    // Bulk watched progress. With extended=progress each entry includes the
    // watched episodes for every season, so the To Watch screen no longer
    // needs one /shows/:id/progress/watched request per show. Trakt caps this
    // mode at 100 shows per page; the response headers report the page count.
    @GET("sync/watched/shows")
    suspend fun watchedShows(
        @Query("extended") extended: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): Response<List<WatchedShow>>

    @GET("calendars/my/shows/{start_date}/{days}")
    suspend fun myCalendar(
        @Path("start_date") startDate: String,
        @Path("days") days: Int,
        @Query("extended") extended: String,
    ): List<CalendarItem>

    @GET("shows/{id}")
    suspend fun show(
        @Path("id") id: Int,
        @Query("extended") extended: String,
    ): Show

    // extended is optional: the season/episode flags come back by default and
    // only the detail screen needs the richer episode metadata.
    @GET("shows/{id}/progress/watched")
    suspend fun showProgress(
        @Path("id") id: Int,
        @Query("extended") extended: String?,
        @Query("specials") specials: Boolean,
    ): ShowProgress

    @GET("shows/{id}/seasons")
    suspend fun seasons(
        @Path("id") id: Int,
        @Query("extended") extended: String,
    ): List<Season>

    @POST("sync/history")
    suspend fun addToHistory(@Body body: SyncEpisodesRequest): SyncResponse

    @POST("sync/history/remove")
    suspend fun removeFromHistory(@Body body: SyncEpisodesRequest): SyncResponse

    @GET("users/settings")
    suspend fun userSettings(): UserSettings
}
