package ca.gibert.tvtracker.data

import ca.gibert.tvtracker.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class TraktHeadersInterceptor(
    private val session: Session,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val snapshot = session.snapshot()
        val request = chain.request().newBuilder()
            .header("Content-Type", "application/json")
            .header("trakt-api-version", "2")
            .header("User-Agent", "TVTracker/${BuildConfig.VERSION_NAME} (Android)")
            .apply {
                snapshot.clientId?.let { header("trakt-api-key", it) }
                snapshot.accessToken?.let { header("Authorization", "Bearer $it") }
            }
            .build()

        return chain.proceed(request)
    }
}
