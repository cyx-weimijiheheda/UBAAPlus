package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.recovery.*
import cn.edu.ubaa.plus.api.storage.*
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.testing.TestSettings
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest

class SchoolWriteCoordinatorTest {
  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    ConnectionRuntime.resolveSelectedMode()
    ScheduleStore.useAccount("alice")
  }

  @Test
  fun ambiguousTimeoutIsNotResubmittedEvenOnTheNextExplicitAttempt() = runTest {
    var sent = 0
    var reads = 0
    suspend fun attempt() =
        SchoolWriteCoordinator.perform<String>(
            "libbook",
            "leave:synthetic-id",
            submit = {
              sent++
              Result.failure(IOException("private-response-secret"))
            },
            verify = {
              reads++
              null
            },
        )
    assertEquals("outcome_unknown", (attempt().exceptionOrNull() as ApiCallException).code)
    assertEquals("outcome_unknown", (attempt().exceptionOrNull() as ApiCallException).code)
    assertEquals(1, sent)
    assertEquals(2, reads)
    assertFalse(LocalDiagnostics.export().contains("private-response-secret"))
    assertTrue(FeatureReadCache.settings.keys.any { it.startsWith("write_pending:") })
    assertTrue(
        FeatureReadCache.settings.keys.none { it.contains("synthetic-id") || it.contains("alice") }
    )
  }

  @Test
  fun successfulReadbackResolvesLostReceiptWithoutAnotherPost() = runTest {
    var sent = 0
    var verified = false
    suspend fun attempt() =
        SchoolWriteCoordinator.perform(
            "libbook",
            "leave:booking",
            submit = {
              sent++
              Result.failure<String>(IOException())
            },
            verify = { if (verified) "confirmed" else null },
        )
    assertTrue(attempt().isFailure)
    verified = true
    assertEquals("confirmed", attempt().getOrThrow())
    assertEquals(1, sent)
  }

  @Test
  fun definitiveBusinessRejectionAllowsANewAttemptButTransportDoesNot() = runTest {
    var sent = 0
    repeat(2) {
      val result =
          SchoolWriteCoordinator.perform<String>(
              "bykc",
              "select:1",
              submit = {
                sent++
                Result.failure(ApiCallException("full", HttpStatusCode.Conflict, "course_full"))
              },
              verify = { error("known rejection needs no verification") },
          )
      assertEquals("course_full", (result.exceptionOrNull() as ApiCallException).code)
    }
    assertEquals(2, sent)
  }

  @Test
  fun concurrentDuplicateIsRejectedBeforeSending() = runTest {
    val entered = CompletableDeferred<Unit>()
    val finish = CompletableDeferred<Unit>()
    var sent = 0
    val first = async {
      SchoolWriteCoordinator.perform(
          "bykc",
          "select:1",
          submit = {
            sent++
            entered.complete(Unit)
            finish.await()
            Result.success("ok")
          },
          verify = { null },
      )
    }
    entered.await()
    val duplicate =
        SchoolWriteCoordinator.perform(
            "bykc",
            "select:1",
            submit = {
              sent++
              Result.success("duplicate")
            },
            verify = { null },
        )
    assertEquals("operation_in_progress", (duplicate.exceptionOrNull() as ApiCallException).code)
    finish.complete(Unit)
    assertEquals("ok", first.await().getOrThrow())
    assertEquals(1, sent)
  }

  @Test
  fun readbackCannotUseAnOldBusinessCacheAsProofOfSuccess() = runTest {
    SchoolReadCoordinator.read<List<String>>("bykc", "chosen") { Result.success(listOf("stale")) }
    val result =
        SchoolWriteCoordinator.perform<String>(
            "bykc",
            "select:1",
            submit = { Result.failure(IOException()) },
            verify = {
              SchoolReadCoordinator.read<List<String>>("bykc", "chosen") {
                    Result.failure(IOException())
                  }
                  .getOrNull()
                  ?.firstOrNull()
            },
        )
    assertTrue(result.isFailure)
  }

  @Test
  fun loggedOutAccessNeverSendsSchoolWrites() = runTest {
    SchoolReadCoordinator.allowOnline(false)
    try {
      val result =
          SchoolWriteCoordinator.perform<String>(
              "bykc",
              "select:1",
              submit = { error("must not send") },
              verify = { error("must not verify") },
          )
      assertEquals("unauthenticated", (result.exceptionOrNull() as ApiCallException).code)
    } finally {
      SchoolReadCoordinator.allowOnline(true)
    }
  }
}
