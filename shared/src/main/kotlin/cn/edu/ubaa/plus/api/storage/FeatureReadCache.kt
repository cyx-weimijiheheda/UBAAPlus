package cn.edu.ubaa.plus.api.storage

import cn.edu.ubaa.plus.repository.ScheduleStore
import com.russhwolf.settings.Settings
import java.security.MessageDigest
import kotlin.time.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** 只保存白名单业务 DTO；安全存储不可用时不降级到明文。 */
internal object FeatureReadCache {
  private var backing: Settings? = null
  var settings: Settings
    get() = backing ?: SecureSettings.get().also { backing = it }
    set(value) {
      backing = value
    }

  val json = Json { ignoreUnknownKeys = true }

  @Serializable
  data class Entry(
      val body: String,
      val savedAt: Long,
      val lastAttempt: Long,
      val complete: Boolean = true,
  )

  private fun hash(value: String) =
      MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
        "%02x".format(it)
      }

  private fun key(owner: String, feature: String, query: String) =
      "business:${hash(owner)}:$feature:${hash(query)}"

  fun entry(feature: String, query: String, owner: String? = ScheduleStore.account()): Entry? =
      runCatching {
            if (owner == null || owner != ScheduleStore.account()) return null
            json.decodeFromString<Entry>(settings.getString(key(owner, feature, query), ""))
          }
          .getOrNull()

  inline fun <reified T> get(feature: String, query: String): T? =
      get(feature, query, serializer<T>())

  fun <T> get(feature: String, query: String, serializer: KSerializer<T>): T? =
      entry(feature, query)
          ?.takeIf { it.complete }
          ?.let { runCatching { json.decodeFromString(serializer, it.body) }.getOrNull() }

  fun <T> save(
      owner: String?,
      feature: String,
      query: String,
      serializer: KSerializer<T>,
      value: T,
  ) {
    if (owner == null || owner != ScheduleStore.account()) return
    runCatching {
      val body = json.encodeToString(serializer, value)
      if (body.length > 2_000_000) return
      val now = Clock.System.now().toEpochMilliseconds()
      settings.putString(key(owner, feature, query), json.encodeToString(Entry(body, now, now)))
      val entries =
          settings.keys
              .filter { it.startsWith("business:") }
              .map { it to settings.getString(it, "") }
      var size = entries.sumOf { it.second.length }
      entries
          .sortedBy {
            runCatching { json.decodeFromString<Entry>(it.second).savedAt }.getOrDefault(0)
          }
          .dropLast(1)
          .forEachIndexed { index, (key, value) ->
            if (size > 6_000_000 || entries.size - index > 96) {
              settings.remove(key)
              size -= value.length
            }
          }
    }
  }

  fun attempted(feature: String, query: String) {
    val owner = ScheduleStore.account() ?: return
    val old = entry(feature, query, owner) ?: return
    runCatching {
      settings.putString(
          key(owner, feature, query),
          json.encodeToString(old.copy(lastAttempt = Clock.System.now().toEpochMilliseconds())),
      )
    }
  }

  fun invalidate(feature: String) {
    val owner = ScheduleStore.account() ?: return
    runCatching {
      settings.keys
          .filter { it.startsWith("business:${hash(owner)}:$feature:") }
          .forEach(settings::remove)
    }
  }

  fun clear() {
    runCatching { settings.keys.filter { it.startsWith("business:") }.forEach(settings::remove) }
  }
}
