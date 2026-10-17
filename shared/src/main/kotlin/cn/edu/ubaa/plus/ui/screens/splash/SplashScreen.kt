package cn.edu.ubaa.plus.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/** 启动界面：仅显示 UBAA 与上标加号组成的一行标题。 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
  Box(
      modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text =
            buildAnnotatedString {
              append("UBAA")
              withStyle(
                  SpanStyle(
                      fontSize = 34.sp,
                      fontWeight = FontWeight.Bold,
                      baselineShift = BaselineShift.Superscript,
                  )
              ) {
                append("+")
              }
            },
        style =
            MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp,
            ),
        color = MaterialTheme.colorScheme.onBackground,
    )
  }
}
