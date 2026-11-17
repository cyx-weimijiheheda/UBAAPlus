package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.api.storage.*
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.testing.TestSettings
import io.ktor.http.HttpStatusCode
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

class SchoolReadCoordinatorTest {
  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    ConnectionRuntime.resolveSelectedMode()
    ScheduleStore.useAccount("alice")
  }

  @AfterTest
  fun cleanup() {
    SchoolReadCoordinator.allowOnline(true)
    SchoolReadCoordinator.invalidate()
  }

  @Test
  fun productionWorkDispatcherRunsParsingAwayFromTheCallerThread() = runTest {
    val old = cn.edu.ubaa.plus.util.BusinessDispatchers.io
    cn.edu.ubaa.plus.util.BusinessDispatchers.io = Dispatchers.Default
    val caller = Thread.currentThread()
    try {
      SchoolReadCoordinator.read<List<String>>("grades", "thread-check") {
            assertNotSame(caller, Thread.currentThread())
            Result.success(listOf("ok"))
          }
          .getOrThrow()
    } finally {
      cn.edu.ubaa.plus.util.BusinessDispatchers.io = old
    }
  }

  @Test
  fun sameQueryMergesAndOneRecoveryServesDifferentFailedReads() = runTest {
    val oldRestore = SchoolReadCoordinator.restoreSession
    var recovered = false
    var restores = 0
    var calls = 0
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    SchoolReadCoordinator.restoreSession = {
      restores++
      recovered = true
      true
    }
    try {
      suspend fun read(query: String) =
          SchoolReadCoordinator.read<List<String>>("grades", query) {
            calls++
            entered.complete(Unit)
            release.await()
            if (recovered) Result.success(listOf(query))
            else Result.failure(ApiCallException("expired", HttpStatusCode.Unauthorized))
          }
      val first = async { read("term") }
      entered.await()
      val duplicate = async { read("term") }
      val other = async { read("other") }
      runCurrent()
      release.complete(Unit)
      assertEquals(listOf("term"), first.await().getOrThrow())
      assertEquals(first.await(), duplicate.await())
      assertEquals(listOf("other"), other.await().getOrThrow())
      assertEquals(1, restores)
      assertTrue(calls in 3..4)
    } finally {
      SchoolReadCoordinator.restoreSession = oldRestore
    }
  }

  @Test
  fun successfulEmptyReplacesOldCacheAndOfflineReadsNeverCallSchool() = runTest {
    SchoolReadCoordinator.read<List<String>>("grades", "term") { Result.success(listOf("old")) }
    SchoolReadCoordinator.read<List<String>>("grades", "term") { Result.success(emptyList()) }
    SchoolReadCoordinator.allowOnline(false)
    assertEquals(
        emptyList(),
        SchoolReadCoordinator.read<List<String>>("grades", "term") { error("must not call") }
            .getOrThrow(),
    )
    ScheduleStore.useAccount("bob")
    assertTrue(
        SchoolReadCoordinator.read<List<String>>("grades", "term") { error("must not call") }
            .isFailure
    )
  }

  @Test
  fun lateResultCannotRebuildCacheAfterAccountSwitch() = runTest {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val task = async {
      SchoolReadCoordinator.read<List<String>>("grades", "term") {
        entered.complete(Unit)
        release.await()
        Result.success(listOf("alice"))
      }
    }
    entered.await()
    ScheduleStore.useAccount("bob")
    release.complete(Unit)
    assertFailsWith<CancellationException> { task.await() }
    ScheduleStore.useAccount("alice")
    assertNull(FeatureReadCache.get<List<String>>("grades", "term"))
  }

  @Test
  fun failedRefreshPreservesLastSuccessTimeAndReportsOnlySafeMetadata() = runTest {
    SchoolReadCoordinator.read<List<String>>("grades", "term") { Result.success(listOf("old")) }
    val saved = FeatureReadCache.entry("grades", "term")!!.savedAt
    val result =
        SchoolReadCoordinator.read<List<String>>("grades", "term") {
          Result.failure(
              ApiCallException("secret-phone-cookie-response", HttpStatusCode.ServiceUnavailable)
          )
        }
    assertEquals(listOf("old"), result.getOrThrow())
    assertEquals(saved, FeatureReadCache.entry("grades", "term")!!.savedAt)
    assertNotNull(FeatureReadCache.readWarning("grades", "term"))
    val report = LocalDiagnostics.export()
    assertFalse(report.contains("secret-phone"))
    assertTrue(report.contains("upstream"))
  }
}
