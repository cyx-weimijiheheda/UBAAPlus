package cn.edu.ubaa.plus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cn.edu.ubaa.plus.reminder.PendingAppRouteStore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    PendingAppRouteStore.offerFromIntent(intent)
    setContent { AndroidApp() }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    PendingAppRouteStore.offerFromIntent(intent)
  }
}

@Preview
@Composable
fun AppAndroidPreview() {
  App()
}
