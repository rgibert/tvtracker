package ca.gibert.tvtracker

import android.app.Application
import ca.gibert.tvtracker.data.AppContainer

class TvTrackerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
