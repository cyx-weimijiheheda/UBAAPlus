package cn.edu.ubaa.plus.ui.screens.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.storage.CacheMaintenance
import cn.edu.ubaa.plus.api.storage.ProfileAvatarStore
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.ui.common.components.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProfileAvatar(owner: String, name: String) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val revision by ProfileAvatarStore.changes.collectAsState()
  val image by
      produceState<ImageBitmap?>(null, owner, revision) {
        value =
            withContext(Dispatchers.IO) {
              ProfileAvatarStore.read(owner)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
              }
            }
      }
  var options by remember(owner) { mutableStateOf(false) }
  var loading by remember(owner) { mutableStateOf(false) }
  var error by remember(owner) { mutableStateOf<String?>(null) }
  UbaaToastEffect(error) { error = null }
  val picker =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null)
            scope.launch {
              loading = true
              val expectedRevision = CacheMaintenance.changes.value
              try {
                val bytes = withContext(Dispatchers.IO) { avatarThumbnail(context, uri) }
                CacheMaintenance.writeIfCurrent(expectedRevision) {
                  if (ScheduleStore.account() == owner) ProfileAvatarStore.save(owner, bytes)
                }
              } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
              } catch (_: Exception) {
                error = "无法读取这张照片，请选择其他图片"
              } finally {
                loading = false
              }
            }
      }
  Box(
      Modifier.size(64.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.primaryContainer)
          .semantics { contentDescription = "用户头像，点击更换" }
          .clickable(enabled = !loading, role = Role.Button) { options = true },
      contentAlignment = Alignment.Center,
  ) {
    if (image != null)
        Image(image!!, "用户头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else {
      val initials = profileInitials(name)
      if (initials.isNotEmpty())
          Text(
              initials,
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
      else
          Icon(
              Icons.Default.AccountCircle,
              null,
              Modifier.size(36.dp),
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
    }
    if (loading) UbaaProgressIndicator(size = 20.dp)
  }
  if (options)
      UbaaAlertDialog(
          onDismissRequest = { options = false },
          title = { Text("头像") },
          text = {
            Column {
              Text("仅在本机保存缩略图，按账户隔离；清理缓存会移除。", style = MaterialTheme.typography.bodyMedium)
              UbaaTextButton(
                  onClick = {
                    options = false
                    picker.launch("image/*")
                  }
              ) {
                Text("从本机选择照片")
              }
              if (image != null)
                  UbaaTextButton(
                      onClick = {
                        options = false
                        scope.launch {
                          withContext(Dispatchers.IO) { ProfileAvatarStore.clearAccount(owner) }
                        }
                      }
                  ) {
                    Text("恢复姓名头像")
                  }
            }
          },
          confirmButton = {},
      )
}

/**
 * Bound input/decode sizes, handle EXIF on older Android, and discard metadata in the thumbnail.
 */
private fun avatarThumbnail(context: Context, uri: Uri): ByteArray {
  val bytes =
      requireNotNull(context.contentResolver.openInputStream(uri)).use {
        it.readBytesBounded(12 * 1024 * 1024)
      }
  val bitmap =
      if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) {
            decoder,
            info,
            _ ->
          val max = maxOf(info.size.width, info.size.height)
          require(max > 0 && max <= 50000)
          val ratio = 256f / max.coerceAtLeast(256)
          decoder.setTargetSize(
              (info.size.width * ratio).toInt().coerceAtLeast(1),
              (info.size.height * ratio).toInt().coerceAtLeast(1),
          )
          decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
      } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..50000 && bounds.outHeight in 1..50000)
        val opts =
            BitmapFactory.Options().apply {
              while (
                  maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize.coerceAtLeast(1) > 512
              ) inSampleSize = inSampleSize.coerceAtLeast(1) * 2
            }
        val raw = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts))
        val orientation =
            runCatching {
                  ExifInterface(bytes.inputStream())
                      .getAttributeInt(
                          ExifInterface.TAG_ORIENTATION,
                          ExifInterface.ORIENTATION_NORMAL,
                      )
                }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix =
            Matrix().apply {
              when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                  setRotate(90f)
                  postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                  setRotate(270f)
                  postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
              }
            }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
          if (it !== raw) raw.recycle()
        }
      }
  try {
    val ratio = 256f / maxOf(bitmap.width, bitmap.height).coerceAtLeast(256)
    val scaled =
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    try {
      return ByteArrayOutputStream().use { output ->
        check(scaled.compress(Bitmap.CompressFormat.JPEG, 88, output))
        output.toByteArray()
      }
    } finally {
      if (scaled !== bitmap) scaled.recycle()
    }
  } finally {
    bitmap.recycle()
  }
}

private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
  val output = ByteArrayOutputStream()
  val buffer = ByteArray(8192)
  while (true) {
    val n = read(buffer)
    if (n < 0) break
    require(output.size() + n <= limit)
    output.write(buffer, 0, n)
  }
  return output.toByteArray()
}
