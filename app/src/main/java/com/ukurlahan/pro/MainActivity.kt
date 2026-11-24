package com.ukurlahan.pro

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.ukurlahan.pro.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Layar tetap menyala selama dipakai di lapangan
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AppTheme { AppRoot() } }
    }
}

enum class Screen { HOME, LEVEL, LEVELING, TOTAL_STATION, AREA, VOLUME, SLOPE, CALIBRATION, GUIDE, PROJECTS }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            secondary = Info,
            background = Color(0xFF14171B),
            onBackground = Color(0xFFECEFF1),
            surface = Color(0xFF1C2026),
            onSurface = Color(0xFFECEFF1),
            surfaceVariant = Color(0xFF262B33),
            onSurfaceVariant = Color(0xFFD5DCE0),
        ),
        content = content,
    )
}

@Composable
fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val back = { screen = Screen.HOME }
    BackHandler(enabled = screen != Screen.HOME) { back() }

    when (screen) {
        Screen.HOME -> HomeScreen(onOpen = { screen = it })
        Screen.LEVEL -> LevelScreen(back)
        Screen.LEVELING -> LevelingScreen(back)
        Screen.TOTAL_STATION -> TotalStationScreen(back)
        Screen.AREA -> AreaWalkScreen(back)
        Screen.VOLUME -> VolumeScreen(back)
        Screen.SLOPE -> SlopeScreen(back)
        Screen.CALIBRATION -> CalibrationScreen(back)
        Screen.GUIDE -> GuideScreen(back)
        Screen.PROJECTS -> ProjectsScreen(back)
    }
}
