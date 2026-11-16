package cn.edu.ubaa.plus.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.ui.screens.schedule.OfflineScheduleScreen
import cn.edu.ubaa.plus.ui.theme.UBAATheme

/** 小组件点击直接打开相同周次，断网时也无需等待登录。 */
class ScheduleWidgetActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val sameAccount = intent.getStringExtra("owner") == ScheduleStore.account()
    setContent {
      val prefs by cn.edu.ubaa.plus.api.storage.AppPreferencesStore.state.collectAsState()
      UBAATheme(
          palette = prefs.themePalette,
          customBrandArgb = prefs.customBrandArgb,
          followWallpaper = prefs.followWallpaper,
          darkTheme =
              when (prefs.themeMode) {
                cn.edu.ubaa.plus.api.storage.ThemeMode.SYSTEM ->
                    androidx.compose.foundation.isSystemInDarkTheme()
                cn.edu.ubaa.plus.api.storage.ThemeMode.DARK -> true
                else -> false
              },
      ) {
        OfflineScheduleScreen(
            onBack = { finish() },
            initialTermCode = if (sameAccount) intent.getStringExtra("term") else null,
            initialWeek = if (sameAccount) intent.getIntExtra("week", -1) else null,
        )
      }
    }
  }
}
