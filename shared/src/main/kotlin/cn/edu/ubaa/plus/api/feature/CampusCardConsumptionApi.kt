package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.local.*
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.repository.ScheduleStore
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import java.math.RoundingMode
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

internal fun campusToday(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai")).date

internal fun validConsumptionRange(
    start: LocalDate,
    end: LocalDate,
    today: LocalDate = campusToday(),
) = start <= end && end <= today

@Serializable
data class CampusCardTrade(
    val dateTime: String,
    val title: String,
    val merchant: String,
    val amount: String,
    val balance: String?,
)

@Serializable
data class CampusCardConsumptionDto(
    val startDate: String,
    val endDate: String,
    val trades: List<CampusCardTrade>,
    val fetchedAt: Long = System.currentTimeMillis(),
)

internal interface CampusCardConsumptionBackend {
  suspend fun query(start: LocalDate, end: LocalDate): Result<CampusCardConsumptionDto>
}

class CampusCardConsumptionApi
internal constructor(
    private val backend: CampusCardConsumptionBackend,
    private val today: () -> LocalDate = ::campusToday,
) {
  constructor() : this(LocalCampusCardConsumptionBackend())

  suspend fun query(start: LocalDate, end: LocalDate): Result<CampusCardConsumptionDto> {
    if (!validConsumptionRange(start, end, today()))
        return Result.failure(
            ApiCallException("日期范围无效：开始日期不能晚于截止日期，且不能选择未来日期", code = "card_date_range")
        )
    return SchoolReadCoordinator.read("card", consumptionCacheKey(start, end)) {
      backend.query(start, end)
    }
  }
}

internal fun consumptionCacheKey(start: LocalDate, end: LocalDate) = "consumption:$start:$end"

internal class LocalCampusCardConsumptionBackend : CampusCardConsumptionBackend {
  override suspend fun query(start: LocalDate, end: LocalDate): Result<CampusCardConsumptionDto> =
      try {
        val owner =
            ScheduleStore.account() ?: throw ApiCallException("请先登录", code = "unauthenticated")
        if (LocalAuthSessionStore.get()?.user?.schoolid != owner)
            throw ApiCallException("登录状态已失效", code = "unauthenticated")
        val shared = LocalUpstreamClientProvider.shared()
        // Same SSO handoff as the existing balance reader and the official card page. Read-only.
        shared.get(
            localUpstreamUrl(
                "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fpass.cc-pay.cn%2Flogin"
            )
        ) {
          attributes.put(LocalPrivateRead, true)
        }
        shared.get(
            localUpstreamUrl(
                "https://mall.cc-pay.cn/cardInquiry?stuNo=${owner.encodeURLParameter()}&linkfrom=pass"
            )
        ) {
          attributes.put(LocalPrivateRead, true)
        }
        val (from, to) = consumptionUtcBounds(start, end)
        val records = mutableListOf<CampusCardTrade>()
        val pageHashes = mutableSetOf<String>()
        LocalUpstreamClientProvider.newNoRedirectClient().use { client ->
          for (number in 1..200) {
            val path =
                "https://mall.cc-pay.cn/api/campus_card/trades?stuNo=${owner.encodeURLParameter()}&tradeType=consume&pageSize=50&pageNum=$number&minTsCreation=${from.encodeURLParameter()}&maxTsCreation=${to.encodeURLParameter()}"
            val response =
                client.get(localUpstreamUrl(path)) {
                  attributes.put(LocalPrivateRead, true)
                  header(HttpHeaders.Accept, "application/json, text/plain, */*")
                  header(
                      HttpHeaders.Referrer,
                      localUpstreamUrl("https://mall.cc-pay.cn/cardInquiry"),
                  )
                }
            if (response.status.value in setOf(301, 302, 303, 307, 308, 401, 403))
                throw ApiCallException("校园卡登录状态失效", code = "card_unauthenticated")
            if (!response.status.isSuccess())
                throw ApiCallException(
                    "消费记录服务暂不可用",
                    code = "card_trades_http",
                    status = response.status,
                )
            val page = parseConsumptionPage(response.bodyAsText(), start, end, number)
            if (page.trades.isNotEmpty() && !pageHashes.add(page.fingerprint))
                throw ApiCallException("消费记录分页异常，未保存不完整结果", code = "card_trades_page")
            records += page.trades
            if (page.trades.size < page.pageSize) {
              return Result.success(
                  CampusCardConsumptionDto(
                      start.toString(),
                      end.toString(),
                      records.sortedBy { it.dateTime },
                  )
              )
            }
          }
        }
        throw ApiCallException("记录较多，请缩小日期范围后查询", code = "card_trades_limit")
      } catch (e: CancellationException) {
        throw e
      } catch (e: ApiCallException) {
        Result.failure(e)
      } catch (_: Exception) {
        Result.failure(ApiCallException("消费记录查询失败，请稍后重试", code = "card_trades_error"))
      }
}

internal fun consumptionUtcBounds(start: LocalDate, end: LocalDate): Pair<String, String> {
  val zone = TimeZone.of("Asia/Shanghai")
  return start.atStartOfDayIn(zone).toString() to
      (end.plus(DatePeriod(days = 1)).atStartOfDayIn(zone) - 1.milliseconds).toString()
}

internal data class ConsumptionPage(
    val trades: List<CampusCardTrade>,
    val pageSize: Int,
    val fingerprint: String,
)

internal fun parseConsumptionPage(
    body: String,
    start: LocalDate,
    end: LocalDate,
    pageNumber: Int,
): ConsumptionPage {
  fun invalid(): Nothing = throw ApiCallException("消费记录格式已变化，未保存不完整结果", code = "card_trades_format")
  val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: invalid()
  if ((root["success"] as? JsonPrimitive)?.booleanOrNull != true) {
    val type = (root["type"] as? JsonPrimitive)?.intOrNull
    throw ApiCallException(
        "消费记录服务返回失败，请重新登录后重试",
        code = if (type in setOf(401, 403)) "card_unauthenticated" else "card_trades_upstream",
    )
  }
  val data = root["data"] as? JsonObject ?: invalid()
  fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()
  if (data.text("pageNum")?.toIntOrNull() != pageNumber) invalid()
  val pageSize = data.text("pageSize")?.toIntOrNull()?.takeIf { it in 1..500 } ?: invalid()
  val array = data["data"] as? JsonArray ?: invalid()
  if (array.size > pageSize) invalid()
  fun cents(raw: String?): String? =
      raw?.toBigDecimalOrNull()
          ?.takeIf { it.precision() <= 20 && kotlin.math.abs(it.scale().toLong()) < 10 }
          ?.movePointLeft(2)
          ?.setScale(2, RoundingMode.HALF_UP)
          ?.toPlainString()
  val trades =
      array.map { row ->
        val obj = row as? JsonObject ?: invalid()
        val rawDate = obj.text("date") ?: invalid()
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}").matches(rawDate))
            invalid()
        val time =
            runCatching { LocalDateTime.parse(rawDate.replace(' ', 'T')) }.getOrNull() ?: invalid()
        val date = time.date
        if (date < start || date > end) invalid()
        val amount = cents(obj.text("amount")) ?: invalid()
        val balance = obj.text("balance")?.let { cents(it) ?: invalid() }
        CampusCardTrade(
            rawDate,
            obj.text("txName").orEmpty(),
            obj.text("merName").orEmpty(),
            amount,
            balance,
        )
      }
  val fingerprint =
      MessageDigest.getInstance("SHA-256").digest(array.toString().toByteArray()).joinToString("") {
        "%02x".format(it)
      }
  return ConsumptionPage(trades, pageSize, fingerprint)
}
