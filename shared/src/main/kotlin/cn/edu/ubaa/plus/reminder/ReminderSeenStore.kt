package cn.edu.ubaa.plus.reminder

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 记录已经通知过的签到/作业 key，避免每轮轮询重复打扰。 */
object ReminderSeenStore {
  private const val PREFIX = "reminder_seen_"
  private const val MAX_KEYS = 400

  private val json = Json { ignoreUnknownKeys = true }

  private var _settings: Settings? = null
  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
    }

  @Synchronized fun isSeen(userKey: String, key: String): Boolean = read(userKey).contains(key)

  /** 返回 true 表示第一次见到该 key，应发通知。 */
  @Synchronized
  fun markIfNew(userKey: String, key: String): Boolean {
    val keys = read(userKey).toMutableSet()
    if (!keys.add(key)) return false
    write(userKey, keys)
    return true
  }

  @Synchronized
  fun isSourceSeeded(userKey: String, source: String): Boolean =
      read(userKey).contains(seedKey(source))

  @Synchronized
  fun seedSource(userKey: String, source: String, keys: Collection<String>) {
    val all = read(userKey).toMutableSet()
    all.add(seedKey(source))
    all.addAll(keys)
    write(userKey, all)
  }

  private fun seedKey(source: String): String = "__seeded_$source"

  @Synchronized
  fun clearUser(userKey: String) {
    settings.remove(keyFor(userKey))
  }

  private fun read(userKey: String): Set<String> {
    val raw = settings.getStringOrNull(keyFor(userKey)) ?: return emptySet()
    return runCatching { json.decodeFromString<Set<String>>(raw) }.getOrDefault(emptySet())
  }

  private fun write(userKey: String, keys: Set<String>) {
    val trimmed = if (keys.size <= MAX_KEYS) keys else keys.toList().takeLast(MAX_KEYS).toSet()
    settings.putString(keyFor(userKey), json.encodeToString(trimmed))
  }

  private fun keyFor(userKey: String): String = "$PREFIX${userKey.filter { it.isLetterOrDigit() }}"
}
