package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.storage.FeatureReadCache
import cn.edu.ubaa.plus.repository.ScheduleStore
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.*
import kotlinx.serialization.serializer

class FeatureReadCachePerformanceTest {
  private class CountingSettings(private val values: Settings = MapSettings()) :
      Settings by values {
    var scans = 0
    var reads = 0
    override val keys: Set<String>
      get() {
        scans++
        return values.keys
      }

    override fun getString(key: String, defaultValue: String): String {
      reads++
      return values.getString(key, defaultValue)
    }
  }

  @BeforeTest
  fun setup() {
    ScheduleStore.settings = MapSettings()
    ScheduleStore.useAccount("alice")
  }

  @Test
  fun manyResponsesBuildTheCapacityIndexOnlyOnceAndRepeatedReadsReuseDecodedData() {
    val storage = CountingSettings()
    FeatureReadCache.settings = storage
    repeat(30) { i ->
      FeatureReadCache.save(
          "alice",
          "grades",
          "term:$i",
          serializer<List<String>>(),
          listOf("score:$i"),
      )
    }
    assertEquals(1, storage.scans)
    val reads = storage.reads
    val value = FeatureReadCache.get<List<String>>("grades", "term:0")
    repeat(100) { assertSame(value, FeatureReadCache.get<List<String>>("grades", "term:0")) }
    assertEquals(reads, storage.reads)
    repeat(100) { assertNull(FeatureReadCache.get<List<String>>("schedule", "missing-week")) }
    assertEquals(reads + 1, storage.reads)
  }

  @Test
  fun onlyChangedScheduleContentTriggersThePeScheduleObserver() {
    FeatureReadCache.settings = MapSettings()
    val revision = FeatureReadCache.scheduleChanges.value
    FeatureReadCache.save("alice", "grades", "term", serializer<List<String>>(), listOf("grade"))
    assertEquals(revision, FeatureReadCache.scheduleChanges.value)
    FeatureReadCache.save("alice", "schedule", "terms", serializer<List<String>>(), listOf("term"))
    assertEquals(revision + 1, FeatureReadCache.scheduleChanges.value)
    FeatureReadCache.save("alice", "schedule", "terms", serializer<List<String>>(), listOf("term"))
    assertEquals(revision + 1, FeatureReadCache.scheduleChanges.value)
    FeatureReadCache.save(
        "alice",
        "schedule",
        "terms",
        serializer<List<String>>(),
        listOf("next-term"),
    )
    assertEquals(revision + 2, FeatureReadCache.scheduleChanges.value)
  }

  @Test
  fun clearedCacheCannotBeRepopulatedByAnOldRead() {
    FeatureReadCache.settings = MapSettings()
    val old = FeatureReadCache.generation
    FeatureReadCache.clearChecked()
    FeatureReadCache.save("alice", "grades", "term", serializer<List<String>>(), listOf("old"), old)
    assertNull(FeatureReadCache.get<List<String>>("grades", "term"))
    FeatureReadCache.save("alice", "grades", "term", serializer<List<String>>(), listOf("fresh"))
    assertEquals(listOf("fresh"), FeatureReadCache.get<List<String>>("grades", "term"))
  }
}
