package ca.gibert.tvtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ca.gibert.tvtracker.ui.TvTrackerRoot
import ca.gibert.tvtracker.ui.theme.TvTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TvTrackerApp).container
        setContent {
            TvTrackerTheme {
                TvTrackerRoot(container)
            }
        }
    }
}
