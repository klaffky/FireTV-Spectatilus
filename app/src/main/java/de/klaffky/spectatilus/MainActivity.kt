package de.klaffky.spectatilus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.klaffky.spectatilus.ui.AppRoot
import de.klaffky.spectatilus.ui.Refresh
import de.klaffky.spectatilus.ui.SpectatilusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpectatilusTheme { AppRoot() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Nach dem Player „Weiter ansehen“ & Fortschritt neu laden
        if (Refresh.playerOpened) {
            Refresh.playerOpened = false
            Refresh.stamp = System.currentTimeMillis()
        }
    }
}
