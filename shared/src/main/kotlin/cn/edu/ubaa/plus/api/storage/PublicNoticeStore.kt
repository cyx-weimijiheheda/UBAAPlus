package cn.edu.ubaa.plus.api.storage

import cn.edu.ubaa.plus.api.auth.AppAnnouncement
import cn.edu.ubaa.plus.api.auth.AppVersionCheckResponse
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/** 公共应用服务的缓存不属于学校账号，也不包含学校会话。 */
internal object PublicNoticeStore {
  private var backing: Settings? = null
  var settings: Settings
    get() = backing ?: Settings().also { backing = it }
    set(value) {
      backing = value
    }

  private val json = Json { ignoreUnknownKeys = true }

  fun update(version: String): AppVersionCheckResponse? =
      runCatching {
            json.decodeFromString<AppVersionCheckResponse>(
                settings.getString("public_update:$version", "")
            )
          }
          .getOrNull()

  fun announcement(): AppAnnouncement? =
      runCatching {
            json.decodeFromString<AppAnnouncement>(settings.getString("public_announcement", ""))
          }
          .getOrNull()

  fun saveUpdate(version: String, value: AppVersionCheckResponse?) {
    if (value == null) settings.remove("public_update:$version")
    else settings.putString("public_update:$version", json.encodeToString(value))
  }

  fun saveAnnouncement(value: AppAnnouncement?) {
    if (value == null) settings.remove("public_announcement")
    else settings.putString("public_announcement", json.encodeToString(value))
  }
}
