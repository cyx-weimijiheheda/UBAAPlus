package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.auth.ApiClientProvider
import cn.edu.ubaa.plus.api.core.ApiFactory
import cn.edu.ubaa.plus.api.core.DefaultApiFactory
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
import cn.edu.ubaa.plus.api.local.LocalCookieStore
import cn.edu.ubaa.plus.api.local.LocalJudgeApiCache
import cn.edu.ubaa.plus.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.plus.api.storage.AuthTokensStore
import cn.edu.ubaa.plus.api.storage.CredentialStore
import cn.edu.ubaa.plus.api.storage.GradeScoreCacheStore
import cn.edu.ubaa.plus.repository.GlobalTermRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionMode(
    val storageKey: String,
    val displayName: String,
    val description: String,
) {
  DIRECT(
      storageKey = "direct",
      displayName = "直连模式",
      description = "直接访问校内上游。",
  ),
  WEBVPN(
      storageKey = "webvpn",
      displayName = "WebVPN模式",
      description = "通过北航 WebVPN 访问上游。",
  ),
  AUTO(
      storageKey = "auto",
      displayName = "自动模式",
      description = "校园网内直连，校外自动使用 WebVPN。",
  );

  companion object {
    fun fromStorageKey(value: String?): ConnectionMode? =
        entries.firstOrNull { it.storageKey == value?.trim()?.lowercase() }
  }
}

object ConnectionModeStore {
  private const val KEY_CONNECTION_MODE = "connection_mode"
  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  fun save(mode: ConnectionMode) {
    settings.putString(KEY_CONNECTION_MODE, mode.storageKey)
  }

  fun get(): ConnectionMode? =
      ConnectionMode.fromStorageKey(settings.getStringOrNull(KEY_CONNECTION_MODE))

  fun clear() {
    settings.remove(KEY_CONNECTION_MODE)
  }
}

object ConnectionRuntime {
  internal var apiFactoryProvider: () -> ApiFactory = { DefaultApiFactory }

  private val _currentMode = MutableStateFlow(
      ConnectionModeStore.get()?.takeIf { it != ConnectionMode.AUTO }
  )
  val currentModeFlow: StateFlow<ConnectionMode?> = _currentMode.asStateFlow()

  internal fun apiFactory(): ApiFactory = apiFactoryProvider()

  fun availableModes(): List<ConnectionMode> = ConnectionMode.entries

  fun resolveSelectedMode(): ConnectionMode? {
    val allowedModes = availableModes().filter { it != ConnectionMode.AUTO }
    val storedMode = ConnectionModeStore.get()
    val resolvedMode =
        when {
          storedMode == ConnectionMode.AUTO ->
              _currentMode.value?.takeIf { it != ConnectionMode.AUTO }
          storedMode != null && storedMode in allowedModes -> storedMode
          allowedModes.size == 1 -> allowedModes.single().also(ConnectionModeStore::save)
          else -> null
        }
    if (resolvedMode != null) _currentMode.value = resolvedMode
    return resolvedMode
  }

  fun preferredMode(): ConnectionMode? = ConnectionModeStore.get()

  fun currentMode(): ConnectionMode? =
      _currentMode.value ?: resolveSelectedMode()?.takeIf { it != ConnectionMode.AUTO }

  /** App 启动时调用：AUTO 先探测，其它模式直接返回。 */
  suspend fun resolveStartupMode(): ConnectionMode? {
    val stored = ConnectionModeStore.get() ?: return resolveSelectedMode()
    if (stored != ConnectionMode.AUTO) return resolveSelectedMode()
    val effective = AutoTransportProbe.resolve(previous = _currentMode.value)
    applyEffectiveTransport(effective)
    return effective
  }

  /** 显式切换模式；AUTO 需要探测，因此是 suspend 的。 */
  suspend fun switchModeAsync(mode: ConnectionMode) {
    if (mode != ConnectionMode.AUTO) {
      switchMode(mode)
      return
    }
    resetSession()
    ConnectionModeStore.save(mode)
    val effective = AutoTransportProbe.resolve(previous = _currentMode.value)
    applyEffectiveTransport(effective)
  }

  /** AUTO 自动切换传输时只重置客户端/缓存，保留登录态与自动登录。 */
  fun applyEffectiveTransport(mode: ConnectionMode) {
    require(mode != ConnectionMode.AUTO) { "AUTO is not a transport mode" }
    ApiClientProvider.reset()
    DefaultApiFactory.clearCachedBackends()
    LocalUpstreamClientProvider.reset()
    LocalJudgeApiCache.clearAll()
    GlobalTermRepository.instance.clear()
    ConnectionModeStore.save(mode)
    _currentMode.value = mode
  }

  fun switchMode(mode: ConnectionMode) {
    require(mode in availableModes()) {
      "Connection mode ${mode.name} is not supported on this platform"
    }
    val current = currentMode()
    if (current == mode && ConnectionModeStore.get() == mode) {
      return
    }
    resetSession()
    ConnectionModeStore.save(mode)
    _currentMode.value = mode
  }

  fun resetSession() {
    AuthTokensStore.clearAllScopes()
    GradeScoreCacheStore.clearAllScopes()
    LocalAuthSessionStore.clearAllScopes()
    LocalCookieStore.clearAllScopes()
    CredentialStore.setAutoLogin(false)
    ApiClientProvider.reset()
    DefaultApiFactory.clearCachedBackends()
    LocalUpstreamClientProvider.reset()
    LocalJudgeApiCache.clearAll()
    GlobalTermRepository.instance.clear()
  }

  fun clearSelectedMode() {
    resetSession()
    ConnectionModeStore.clear()
    _currentMode.value = null
  }
}

internal object ModeScopedSessionStore {
  private const val LEGACY_PREFIX = ""

  fun scopedKey(key: String, mode: ConnectionMode? = currentTransportForScope()): String {
    val effectiveMode = mode?.takeIf { it != ConnectionMode.AUTO } ?: ConnectionMode.DIRECT
    return "mode_${effectiveMode.storageKey}_$key"
  }

  fun legacyKey(key: String): String = "$LEGACY_PREFIX$key"

  private fun currentTransportForScope(): ConnectionMode {
    val stored = ConnectionModeStore.get()
    if (stored != null && stored != ConnectionMode.AUTO) return stored
    return ConnectionRuntime.currentMode()?.takeIf { it != ConnectionMode.AUTO }
        ?: ConnectionMode.DIRECT
  }
}
