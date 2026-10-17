package cn.edu.ubaa.plus.ui.screens.venue

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.ubaa.plus.api.venue.VenueCaptchaChallenge
import cn.edu.ubaa.plus.api.venue.VenuePoint

/**
 * 点击文字验证码弹窗。
 *
 * 用户按文字顺序在图片上点击，提交时回传原始图片像素坐标与图片尺寸， 由控制器换算到验证码画布坐标系后再做加密。
 */
@Composable
fun VenueCaptchaDialog(
    challenge: VenueCaptchaChallenge,
    onCancel: () -> Unit,
    onSubmit: (points: List<VenuePoint>, imageWidth: Int, imageHeight: Int) -> Unit,
) {
  val bitmap = remember(challenge.token) { decodeVenueImage(challenge.imageBase64) }
  var points by remember(challenge.token) { mutableStateOf<List<VenuePoint>>(emptyList()) }
  val expected = challenge.wordList.size.takeIf { it > 0 } ?: 3

  AlertDialog(
      onDismissRequest = onCancel,
      title = { Text("点击文字验证码") },
      text = {
        Column {
          Text(
              text = "请按顺序点击：${challenge.wordList.joinToString("、")}",
              fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(8.dp))
          if (bitmap == null) {
            Text("验证码图片加载失败，请取消后重试。")
          } else {
            BoxWithConstraints(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(
                            if (bitmap.height > 0) {
                              (320 * bitmap.height / bitmap.width).dp
                            } else {
                              120.dp
                            }
                        )
            ) {
              Image(
                  bitmap = bitmap.asImageBitmap(),
                  contentDescription = "验证码",
                  modifier =
                      Modifier.fillMaxSize().pointerInput(bitmap) {
                        detectTapGestures { offset ->
                          val width = size.width.toFloat()
                          val height = size.height.toFloat()
                          if (width <= 0f || height <= 0f) return@detectTapGestures
                          if (points.size >= expected) return@detectTapGestures
                          points =
                              points +
                                  VenuePoint(
                                      x = offset.x / width * bitmap.width,
                                      y = offset.y / height * bitmap.height,
                                  )
                        }
                      },
              )
              points.forEachIndexed { index, point ->
                val x = maxWidth * (point.x / bitmap.width) - 11.dp
                val y = maxHeight * (point.y / bitmap.height) - 11.dp
                Box(
                    modifier =
                        Modifier.offset(x = x, y = y)
                            .size(22.dp)
                            .background(Color.Red, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                  Text(text = "${index + 1}", color = Color.White, fontSize = 11.sp)
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text("已点击 ${points.size}/$expected")
        }
      },
      confirmButton = {
        TextButton(
            onClick = {
              if (bitmap != null && points.size == expected) {
                onSubmit(points, bitmap.width, bitmap.height)
              }
            },
            enabled = bitmap != null && points.size == expected,
        ) {
          Text("提交")
        }
      },
      dismissButton = {
        Row {
          TextButton(
              onClick = { points = points.dropLast(1) },
              enabled = points.isNotEmpty(),
          ) {
            Text("撤销")
          }
          TextButton(
              onClick = { points = emptyList() },
              enabled = points.isNotEmpty(),
          ) {
            Text("重置")
          }
          TextButton(onClick = onCancel) { Text("取消") }
        }
      },
  )
}
