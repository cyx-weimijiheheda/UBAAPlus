package cn.edu.ubaa.plus.api.storage

import cn.edu.ubaa.plus.repository.ScheduleStore
import com.russhwolf.settings.Settings
import java.security.MessageDigest
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** 安全存储 + 有界内存读缓存。容量索引只重建一次，不在每个响应后解密扫描全部正文。 */
internal object FeatureReadCache {
  private val lock = Any()
  private val revision = MutableStateFlow(0L)
  val changes: StateFlow<Long> = revision
  private val scheduleRevision = MutableStateFlow(0L)
  val scheduleChanges: StateFlow<Long> = scheduleRevision
  @Volatile private var cacheGeneration = 0L
  val generation: Long
    get() = cacheGeneration

  private var backing: Settings? = null

  private data class Lookup(val owner: String, val feature: String, val query: String)

  private data class DecodedKey(val storageKey: String, val descriptor: SerialDescriptor)

  private data class Metadata(val size: Int, val savedAt: Long)

  private class Lru<K, V>(private val capacity: Int) : LinkedHashMap<K, V>(capacity, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > capacity
  }

  private val keys = Lru<Lookup, String>(256)
  private val entries = Lru<String, Entry?>(48)
  private val decoded = Lru<DecodedKey, Any>(48)
  private var index: MutableMap<String, Metadata>? = null
  var settings: Settings
    get() = synchronized(lock) { backing ?: SecureSettings.get().also { backing = it } }
    set(value) =
        synchronized(lock) {
          backing = value
          cacheGeneration++
          keys.clear()
          entries.clear()
          decoded.clear()
          index = null
        }

  val json = Json { ignoreUnknownKeys = true }

  @Serializable
  data class Entry(
      val body: String,
      val savedAt: Long,
      val lastAttempt: Long,
      val complete: Boolean = true,
      val lastFailed: Boolean = false,
  )

  private fun hash(value: String): String {
    val digits = "0123456789abcdef"
    return buildString(64) {
      MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).forEach { byte ->
        val n = byte.toInt() and 255
        append(digits[n ushr 4])
        append(digits[n and 15])
      }
    }
  }

  private fun key(owner: String, feature: String, query: String): String =
      keys.getOrPut(Lookup(owner, feature, query)) {
        "business:${hash(owner)}:$feature:${hash(query)}"
      }

  fun entry(feature: String, query: String, owner: String? = ScheduleStore.account()): Entry? =
      synchronized(lock) {
        if (owner == null || owner != ScheduleStore.account()) return null
        val key = key(owner, feature, query)
        if (entries.containsKey(key)) return entries[key]
        runCatching { json.decodeFromString<Entry>(settings.getString(key, "")) }
            .getOrNull()
            .also { entries[key] = it }
      }

  inline fun <reified T> get(feature: String, query: String): T? =
      get(feature, query, serializer<T>())

  @Suppress("UNCHECKED_CAST")
  fun <T> get(feature: String, query: String, serializer: KSerializer<T>): T? =
      synchronized(lock) {
        val owner = ScheduleStore.account() ?: return null
        val entry = entry(feature, query, owner)?.takeIf { it.complete } ?: return null
        val cacheKey = DecodedKey(key(owner, feature, query), serializer.descriptor)
        decoded[cacheKey]?.let {
          return it as T
        }
        val result = runCatching { json.decodeFromString(serializer, entry.body) }.getOrNull()
        if (owner != ScheduleStore.account()) return null
        if (result != null) decoded[cacheKey] = result
        result
      }

  private fun ensureIndex(): MutableMap<String, Metadata> {
    index?.let {
      return it
    }
    return settings.keys
        .filter { it.startsWith("business:") }
        .associateWith { key ->
          val raw = settings.getString(key, "")
          Metadata(
              raw.length,
              runCatching { json.decodeFromString<Entry>(raw).savedAt }.getOrDefault(0),
          )
        }
        .toMutableMap()
        .also { index = it }
  }

  private fun forget(key: String) {
    entries.remove(key)
    decoded.keys.removeAll { it.storageKey == key }
  }

  private fun put(key: String, entry: Entry) {
    val metadata = ensureIndex()
    val encoded = json.encodeToString(entry)
    settings.putString(key, encoded)
    entries[key] = entry
    metadata[key] = Metadata(encoded.length, entry.savedAt)
    var total = metadata.values.sumOf { it.size }
    while (metadata.size > 96 || total > 6_000_000) {
      val oldest = metadata.minByOrNull { it.value.savedAt } ?: break
      settings.remove(oldest.key)
      forget(oldest.key)
      total -= oldest.value.size
      metadata.remove(oldest.key)
    }
  }

  fun <T> save(
      owner: String?,
      feature: String,
      query: String,
      serializer: KSerializer<T>,
      value: T,
      expectedGeneration: Long = generation,
  ): Unit =
      synchronized(lock) {
        if (
            owner == null ||
                owner != ScheduleStore.account() ||
                expectedGeneration != cacheGeneration
        )
            return
        runCatching {
          @Suppress("UNCHECKED_CAST")
          val projection =
              when (value) {
                is cn.edu.ubaa.plus.model.dto.BykcCourseDetailDto ->
                    value.copy(canSign = false, canSignOut = false, signConfig = null) as T
                else -> value
              }
          val body = json.encodeToString(serializer, projection)
          if (body.length > 2_000_000) return
          val key = key(owner, feature, query)
          val changed = entry(feature, query, owner)?.body != body
          val now = Clock.System.now().toEpochMilliseconds()
          forget(key)
          put(key, Entry(body, now, now))
          if (entries.containsKey(key) && projection != null)
              decoded[DecodedKey(key, serializer.descriptor)] = projection
          revision.value++
          if (feature == "schedule" && changed) scheduleRevision.value++
        }
        Unit
      }

  fun attempted(feature: String, query: String, expectedGeneration: Long = generation): Unit =
      synchronized(lock) {
        val owner = ScheduleStore.account() ?: return
        if (expectedGeneration != cacheGeneration) return
        val old = entry(feature, query, owner) ?: return
        runCatching {
          put(
              key(owner, feature, query),
              old.copy(lastAttempt = Clock.System.now().toEpochMilliseconds(), lastFailed = true),
          )
        }
        Unit
      }

  fun readWarning(feature: String, query: String): String? =
      if (entry(feature, query)?.lastFailed == true) "更新失败，当前显示上次保存的数据" else null

  fun invalidate(feature: String): Unit =
      synchronized(lock) {
        val owner = ScheduleStore.account() ?: return
        runCatching {
          val prefix = "business:${hash(owner)}:$feature:"
          val known = ensureIndex().keys.filter { it.startsWith(prefix) }
          known.forEach {
            settings.remove(it)
            index?.remove(it)
            forget(it)
          }
          entries.keys.filter { it.startsWith(prefix) }.toList().forEach(::forget)
        }
        Unit
      }

  fun encodedCharacters(): Long =
      synchronized(lock) {
        runCatching { ensureIndex().values.sumOf { it.size.toLong() } }.getOrDefault(0)
      }

  fun clearChecked(): Unit =
      synchronized(lock) {
        cacheGeneration++
        settings.keys.filter { it.startsWith("business:") }.forEach(settings::remove)
        entries.clear()
        decoded.clear()
        index = null
        keys.clear()
        revision.value++
        scheduleRevision.value++
      }

  fun clear(): Unit {
    runCatching { clearChecked() }
  }
}
