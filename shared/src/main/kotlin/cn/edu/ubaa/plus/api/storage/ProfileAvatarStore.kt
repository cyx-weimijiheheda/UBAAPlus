package cn.edu.ubaa.plus.api.storage

import cn.edu.ubaa.plus.repository.ScheduleStore
import com.russhwolf.settings.Settings
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Account-scoped, encrypted thumbnail cache; no original photo or URI is retained. */
internal object ProfileAvatarStore {
  private var backing: Settings? = null
  var settings: Settings
    get() = backing ?: SecureSettings.get().also { backing = it }
    set(value) {
      backing = value
      revision.value++
    }

  private val revision = MutableStateFlow(0L)
  val changes = revision.asStateFlow()

  private fun key(owner: String) =
      "profile_avatar:" +
          MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString("") {
            "%02x".format(it)
          }

  fun read(owner: String): ByteArray? {
    if (owner != ScheduleStore.account()) return null
    return runCatching { Base64.getDecoder().decode(settings.getString(key(owner), "")) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() && it.size <= 256 * 1024 }
  }

  fun save(owner: String, bytes: ByteArray) {
    require(owner == ScheduleStore.account())
    require(bytes.isNotEmpty() && bytes.size <= 256 * 1024)
    settings.putString(key(owner), Base64.getEncoder().encodeToString(bytes))
    revision.value++
  }

  fun clearAccount(owner: String) {
    settings.remove(key(owner))
    revision.value++
  }

  fun clear() {
    settings.keys.filter { it.startsWith("profile_avatar:") }.forEach(settings::remove)
    revision.value++
  }

  fun sizeBytes(): Long =
      settings.keys
          .filter { it.startsWith("profile_avatar:") }
          .sumOf { settings.getString(it, "").toByteArray().size.toLong() }
}
