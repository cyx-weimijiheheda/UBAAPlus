package cn.edu.ubaa.plus.api.storage

import com.russhwolf.settings.Settings

/** 首次使用免责声明的接受状态。 */
object DisclaimerStore {
  private const val KEY_ACCEPTED = "disclaimer_accepted"

  private var _settings: Settings? = null

  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  fun isAccepted(): Boolean = settings.getBoolean(KEY_ACCEPTED, false)

  fun accept() {
    settings.putBoolean(KEY_ACCEPTED, true)
  }
}
