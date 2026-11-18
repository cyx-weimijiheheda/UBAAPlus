@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package cn.edu.ubaa.plus.ui.screens.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Display the verified school photo, or a name fallback. No local picker. */
@Composable
internal fun ProfileAvatar(name: String, encodedImage: String?) {
  val image by
      produceState<ImageBitmap?>(null, encodedImage) {
        value =
            withContext(Dispatchers.Default) {
              runCatching {
                    val bytes =
                        encodedImage?.let { kotlin.io.encoding.Base64.decode(it) }
                            ?: return@runCatching null
                    if (bytes.size > 1024 * 1024) return@runCatching null
                    val bounds =
                        android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096)
                        return@runCatching null
                    val opts =
                        android.graphics.BitmapFactory.Options().apply {
                          inSampleSize = 1
                          while (
                              maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 256
                          ) inSampleSize *= 2
                        }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        ?.asImageBitmap()
                  }
                  .getOrNull()
            }
      }
  Box(
      Modifier.size(64.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
  ) {
    val initials = profileInitials(name)
    if (image != null)
        Image(image!!, "学校头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else if (initials.isNotEmpty())
        Text(
            initials,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    else
        Icon(
            Icons.Default.AccountCircle,
            "用户头像",
            Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
  }
}
