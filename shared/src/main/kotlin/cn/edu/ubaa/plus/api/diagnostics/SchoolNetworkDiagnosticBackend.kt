package cn.edu.ubaa.plus.api.diagnostics

import cn.edu.ubaa.plus.BuildKonfig
import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.auth.*
import cn.edu.ubaa.plus.api.core.getDefaultEngine
import cn.edu.ubaa.plus.api.feature.*
import cn.edu.ubaa.plus.api.local.*
import cn.edu.ubaa.plus.api.venue.VenueApi
import cn.edu.ubaa.plus.api.venue.VenueAuthOutcome
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

/** Explicit allow-list of reads. Do not dispatch writes based on labels or arbitrary URLs. */
internal class SchoolNetworkDiagnosticBackend : NetworkDiagnosticBackend {
  override suspend fun transport(target: DiagnosticTarget, mode: ConnectionMode): Int {
    val actual = target.actualMode(mode)
    val url =
        if (actual == ConnectionMode.WEBVPN) LocalWebVpnSupport.toWebVpnUrl(target.url)
        else target.url
    val engine =
        if (target.route in setOf(DiagnosticRoute.LIBRARY, DiagnosticRoute.LIBRARY_DIRECT))
            getLibBookHttpClientEngine()
        else getDefaultEngine()
    val client =
        HttpClient(engine) {
          followRedirects = false
          if (target.route != DiagnosticRoute.PUBLIC)
              install(io.ktor.client.plugins.cookies.HttpCookies) {
                storage = LocalCookieStore.storage(actual)
              }
          install(HttpTimeout) {
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 8_000
          }
        }
    return client.use { it.get(url) { attributes.put(LocalPrivateRead, true) }.status.value }
  }

  override suspend fun read(target: DiagnosticTarget) {
    val today = campusToday()
    when (target.id) {
      "auth" -> LocalAuthServiceBackend().getAuthStatus().getOrThrow()
      "app_service" -> readPublicService()
      "schedule" -> ScheduleApi().getTodaySchedule().getOrThrow()
      "classroom" -> ClassroomApi().queryClassrooms(1, today.toString()).getOrThrow()
      "iclass" -> SigninApi().getTodayClassesStrict().getOrThrow()
      "libbook" -> LibBookApi().getBookings(1, 1).getOrThrow()
      "libservice" -> LibraryBorrowApi().getCurrentLoans().getOrThrow()
      "spoc" -> SpocApi().getAssignments().getOrThrow()
      "bykc" -> BykcApi().getCourses(page = 1, size = 1, all = false).getOrThrow()
      "venue" -> {
        val api = VenueApi()
        if (api.resumeSession() != VenueAuthOutcome.Ready)
            throw DiagnosticSkip(ProbeReason.SUBSYSTEM_LOGIN)
        api.getSites().getOrThrow()
      }
      "ygdk" -> YgdkApi().getOverview().getOrThrow()
      "cgyy" -> CgyyApi().getVenueSites().getOrThrow()
      "dorm" -> DormElectricityApi().getRooms().getOrThrow()
      "network" -> {
        CampusNetworkApi().getTraffic().getOrThrow()
        ItPortalApi().findPersonalValue("net.netFee").getOrThrow()
      }
      "card" -> {
        CampusCardApi().getBalance().getOrThrow()
        CampusCardConsumptionApi().query(today, today).getOrThrow()
      }
      "finance" -> FinanceApi().incomes().getOrThrow()
      "evaluation" -> EvaluationService().getAllEvaluations().getOrThrow()
      "exam" -> {
        val api = ScheduleApi()
        api.getExamArrangement(currentTerm(api)).getOrThrow()
      }
      "grade" -> GradeApi().getGrades(currentTerm(ScheduleApi())).getOrThrow()
      "judge" -> JudgeApi().getAssignments().getOrThrow()
      else -> throw DiagnosticSkip(ProbeReason.UNSUPPORTED)
    }
  }

  private suspend fun currentTerm(api: ScheduleApi): String {
    val terms = api.getTerms().getOrThrow()
    return (terms.firstOrNull { it.selected } ?: terms.firstOrNull())?.itemCode
        ?: throw DiagnosticSkip(ProbeReason.NO_DATA)
  }

  private suspend fun readPublicService() {
    publicClient().use { client ->
      val base = BuildKonfig.API_ENDPOINT.trimEnd('/')
      val version =
          client.get("$base/api/v1/app/version") { parameter("clientVersion", BuildKonfig.VERSION) }
      if (version.status != HttpStatusCode.OK)
          throw ApiCallException("服务响应异常", status = version.status)
      Json { ignoreUnknownKeys = true }
          .decodeFromString<AppVersionCheckResponse>(version.bodyAsText())
      val announcement = client.get("$base/api/v1/app/announcement")
      when (announcement.status) {
        HttpStatusCode.OK ->
            Json { ignoreUnknownKeys = true }
                .decodeFromString<AppAnnouncement>(announcement.bodyAsText())
        HttpStatusCode.NoContent,
        HttpStatusCode.NotFound -> Unit
        else -> throw ApiCallException("服务响应异常", status = announcement.status)
      }
    }
  }

  private fun publicClient() =
      HttpClient(getDefaultEngine()) {
        followRedirects = false
        install(HttpTimeout) {
          requestTimeoutMillis = 15_000
          connectTimeoutMillis = 8_000
          socketTimeoutMillis = 15_000
        }
      }
}
