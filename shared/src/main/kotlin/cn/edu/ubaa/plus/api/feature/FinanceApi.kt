package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.repository.ScheduleStore
import java.math.RoundingMode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jsoup.Jsoup

@Serializable
data class FinanceIncome(
    val id: String,
    val project: String?,
    val description: String?,
    val amountCents: Long?,
    val taxCents: Long?,
    val paidCents: Long?,
    val taxRate: String?,
    val recordedDate: String?,
    val year: String?,
    val month: String?,
)

@Serializable data class FinanceIncomes(val entries: List<FinanceIncome>)

@Serializable
data class FinanceProfile(
    val name: String?,
    val schoolId: String,
    val department: String?,
    val identityNumber: String?,
    val bankCard: String?,
    val phone: String?,
    val email: String?,
)

internal interface FinanceBackend {
  suspend fun incomes(): Result<FinanceIncomes>

  suspend fun profile(): Result<FinanceProfile>
}

class FinanceApi internal constructor(private val backend: FinanceBackend) {
  constructor() : this(LocalFinanceBackend())

  suspend fun incomes(): Result<FinanceIncomes> =
      SchoolReadCoordinator.read("finance", "incomes") { backend.incomes() }

  suspend fun profile(): Result<FinanceProfile> =
      SchoolReadCoordinator.read("finance", "profile") { backend.profile() }
}

internal class LocalFinanceBackend : FinanceBackend {
  override suspend fun profile(): Result<FinanceProfile> = execute {
    FinancePortal().use {
      parseFinanceProfile(it.openHome(), ScheduleStore.account() ?: throw ApiCallException("请先登录"))
    }
  }

  override suspend fun incomes(): Result<FinanceIncomes> = execute {
    for (attempt in 0..1) {
      try {
        FinancePortal().use { portal ->
          val credential = portal.credential()
          val all = mutableListOf<FinanceIncome>()
          val ids = mutableSetOf<String>()
          var expected: Int? = null
          for (number in 1..200) {
            val page = parseFinancePage(portal.query(credential, number))
            if ((expected != null && expected != page.total) || page.entries.size > 100)
                financeInvalid()
            expected = page.total
            for (entry in page.entries) {
              if (!ids.add(entry.id)) financeInvalid()
              all += entry
            }
            if (all.size == page.total) return@execute FinanceIncomes(all)
            if (page.entries.isEmpty() || all.size > page.total) financeInvalid()
          }
          throw ApiCallException("财务记录较多，暂无法完整读取", code = "finance_limit")
        }
      } catch (e: ApiCallException) {
        if (e.code != "finance_auth" || attempt == 1) throw e
      }
    }
    financeInvalid()
  }

  private suspend fun <T> execute(block: suspend () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: CancellationException) {
        throw e
      } catch (e: ApiCallException) {
        Result.failure(e)
      } catch (_: Exception) {
        Result.failure(ApiCallException("财务服务暂不可用，请稍后重试", code = "finance_error"))
      }
}

internal fun financeInvalid(): Nothing =
    throw ApiCallException("财务响应结构异常，未保存不完整数据", code = "finance_format")

internal fun financeMoney(raw: JsonElement?): Long? {
  if (raw == null || raw is JsonNull) return null
  val p = raw as? JsonPrimitive ?: financeInvalid()
  if (p.isString) financeInvalid()
  return try {
    p.content
        .toBigDecimal()
        .setScale(2, RoundingMode.UNNECESSARY)
        .movePointRight(2)
        .longValueExact()
  } catch (_: Exception) {
    financeInvalid()
  }
}

internal data class FinancePage(val entries: List<FinanceIncome>, val total: Int)

internal fun parseFinancePage(data: JsonObject): FinancePage {
  val modern = data.containsKey("mx") || data.containsKey("total")
  val rows = data[if (modern) "mx" else "data"] as? JsonArray ?: financeInvalid()
  val rawTotal = data[if (modern) "total" else "count"] as? JsonPrimitive ?: financeInvalid()
  val total = rawTotal.intOrNull?.takeIf { it >= 0 && !rawTotal.isString } ?: financeInvalid()
  if (rows.size > total) financeInvalid()
  fun JsonObject.text(key: String): String? {
    val raw = this[key]
    if (raw == null || raw is JsonNull) return null
    val p = raw as? JsonPrimitive ?: financeInvalid()
    if (!p.isString) financeInvalid()
    return p.content.trim().takeIf { it.isNotEmpty() }
  }
  return FinancePage(
      rows.map {
        val row = it as? JsonObject ?: financeInvalid()
        FinanceIncome(
            row.text("lsh") ?: financeInvalid(),
            row.text("ffxmmc"),
            row.text("zy"),
            financeMoney(row["je"]),
            financeMoney(row["se"]),
            financeMoney(row["sfje"]),
            row.text("slbfb"),
            row.text("lrrq"),
            row.text("nian"),
            row.text("yue"),
        )
      },
      total,
  )
}

internal fun parseFinanceProfile(html: String, owner: String): FinanceProfile {
  val doc = Jsoup.parse(html)
  fun text(id: String) =
      doc.getElementById(id)?.text()?.trim()?.takeIf { it.isNotEmpty() && it != "&nbsp;" }
  val account = text("Lab_Num") ?: financeInvalid()
  if (account != owner) throw ApiCallException("财务账户与当前账户不一致", code = "finance_account_mismatch")
  return FinanceProfile(
      text("Lab_Name"),
      account,
      text("Lab_Job"),
      text("Lab_Zjhm"),
      text("Lab_Yhkh"),
      text("Lab_Mob"),
      text("Lab_Email"),
  )
}
