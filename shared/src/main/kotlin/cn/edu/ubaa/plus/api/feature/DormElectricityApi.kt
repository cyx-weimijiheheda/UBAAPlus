package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.model.dto.DormMeterInfoDto
import cn.edu.ubaa.plus.model.dto.DormRoomDto
import cn.edu.ubaa.plus.model.dto.DormTopUpDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

interface DormElectricityApiBackend {
  suspend fun getRooms(): Result<List<DormRoomDto>>

  suspend fun getMeterInfo(id: Int): Result<DormMeterInfoDto>

  suspend fun getTopUps(id: Int): Result<List<DormTopUpDto>>

  /** @return 校园支付平台 URL。 */
  suspend fun recharge(id: Int, writePower: Int): Result<String>
}

/** 寝室用电公开接口门面。 */
class DormElectricityApi internal constructor(private val backend: DormElectricityApiBackend) {
  constructor() : this(LocalDormElectricityApiBackend())

  suspend fun getRooms(): Result<List<DormRoomDto>> = backend.getRooms()

  suspend fun getMeterInfo(id: Int): Result<DormMeterInfoDto> = backend.getMeterInfo(id)

  suspend fun getTopUps(id: Int): Result<List<DormTopUpDto>> = backend.getTopUps(id)

  suspend fun recharge(id: Int, writePower: Int): Result<String> = backend.recharge(id, writePower)
}

internal class LocalDormElectricityApiBackend(
    private val clientProvider: () -> HttpClient = {
      HttpClient(OkHttp) {
        followRedirects = false
        expectSuccess = false
      }
    },
) : DormElectricityApiBackend {
  private val json = Json { ignoreUnknownKeys = true }
  private val client by lazy { clientProvider() }

  override suspend fun getRooms(): Result<List<DormRoomDto>> =
      execute("宿舍电表列表加载失败") {
        val cached = synchronized(RoomCache) { RoomCache.rooms }
        if (cached != null) return@execute cached
        val body = getText("/PubBuaa/QueryIdData?refresh=false")
        val rooms = parseRooms(body)
        synchronized(RoomCache) { RoomCache.rooms = rooms }
        rooms
      }

  override suspend fun getMeterInfo(id: Int): Result<DormMeterInfoDto> =
      execute("电表信息加载失败") { parseMeter(getText("/BuaaPay/Meter?id=$id")) }

  override suspend fun getTopUps(id: Int): Result<List<DormTopUpDto>> =
      execute("购电记录加载失败") { parseTopUps(getText("/PubBuaa?id=$id")) }

  override suspend fun recharge(id: Int, writePower: Int): Result<String> =
      execute("购电请求失败") {
        val response =
            client.post("$BASE_URL/BuaaPay/Pay") {
              setBody(
                  FormDataContent(
                      Parameters.build {
                        append("id", id.toString())
                        append("writePower", writePower.toString())
                      }
                  )
              )
            }
        val body = response.bodyAsText().trim()
        val location = response.headers[HttpHeaders.Location]
        extractPayUrl(body = body, location = location, status = response.status)
      }

  private suspend fun getText(path: String): String {
    val response = client.get("$BASE_URL${path}")
    val body = response.bodyAsText()
    if (!response.status.isSuccess()) {
      throw ApiCallException(
          message = "寝室用电服务返回 ${response.status.value}",
          status = response.status,
          code = "dorm_http_error",
      )
    }
    return body
  }

  private fun parseRooms(body: String): List<DormRoomDto> {
    val array = json.parseToJsonElement(extractArray(body)).jsonArray
    return array.mapNotNull { element ->
      val item = element as? JsonObject ?: return@mapNotNull null
      val id = (item["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
      DormRoomDto(
          id = id,
          campus = item.string("campus"),
          building = item.string("building"),
          floor = item.string("floor"),
          room = item.string("room"),
          name = item.string("name"),
          meterNo = item.string("meterNo"),
          address = item.string("address"),
      )
    }
  }

  private fun parseMeter(body: String): DormMeterInfoDto {
    val item = json.parseToJsonElement(extractObject(body)).jsonObject
    return DormMeterInfoDto(
        id = (item["id"] as? JsonPrimitive)?.intOrNull ?: 0,
        campus = item.string("compus").ifBlank { item.string("campus") },
        building = item.string("building"),
        room = item.string("room"),
        address = item.string("address"),
        remain = (item["remain"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        price = (item["price"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        payStatus = (item["payStatus"] as? JsonPrimitive)?.intOrNull ?: 0,
        readingTime = item.string("readingTime"),
        ct = ((item["ct"] as? JsonPrimitive)?.intOrNull ?: 1).coerceAtLeast(1),
        payUrl = item.string("payUrl").ifBlank { null },
        serial = item.string("serial").ifBlank { null },
        money = (item["money"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
    )
  }

  private fun parseTopUps(body: String): List<DormTopUpDto> {
    val start = body.indexOf("最近 5 次购电")
    val section = if (start >= 0) body.substring(start) else body
    val rowRegex =
        Regex(
            """<tr[^>]*>(.*?)</tr>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
    val cellRegex =
        Regex(
            """<td[^>]*>(.*?)</td>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
    return rowRegex
        .findAll(section)
        .mapNotNull { row ->
          val cells =
              cellRegex.findAll(row.groupValues[1]).map { stripHtml(it.groupValues[1]) }.toList()
          if (cells.size < 4 || cells.first() == "日期") return@mapNotNull null
          DormTopUpDto(
              date = cells[0],
              power = cells[1],
              amount = cells[2],
              operator = cells[3],
          )
        }
        .toList()
  }

  private fun extractPayUrl(body: String, location: String?, status: HttpStatusCode): String {
    location
        ?.takeIf { it.startsWith("http") }
        ?.let {
          return it
        }
    if (body.startsWith("http")) return body
    val fromJson =
        runCatching {
              val root = json.parseToJsonElement(body).jsonObject
              (root["payUrl"] ?: root["url"] ?: root["data"])
                  ?.let { it as? JsonPrimitive }
                  ?.contentOrNull
                  ?.takeIf { it.startsWith("http") }
            }
            .getOrNull()
    if (fromJson != null) return fromJson
    throw ApiCallException(
        message = "支付页面地址获取失败（HTTP ${status.value}）",
        status = status,
        code = "dorm_pay_failed",
    )
  }

  private fun extractArray(body: String): String {
    val trimmed = body.trim()
    val start = trimmed.indexOf('[')
    val end = trimmed.lastIndexOf(']')
    if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
    throw ApiCallException("宿舍电表列表格式异常", code = "dorm_parse_error")
  }

  private fun extractObject(body: String): String {
    val trimmed = body.trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
    throw ApiCallException("电表信息格式异常", code = "dorm_parse_error")
  }

  private fun stripHtml(value: String): String =
      value.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").trim()

  private fun JsonObject.string(key: String): String =
      (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()

  private suspend fun <T> execute(message: String, block: suspend () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: ApiCallException) {
        Result.failure(e)
      } catch (e: Exception) {
        Result.failure(ApiCallException(message = message, code = "dorm_error", cause = e))
      }

  internal fun clearRoomCache() {
    synchronized(RoomCache) { RoomCache.rooms = null }
  }

  private object RoomCache {
    var rooms: List<DormRoomDto>? = null
  }

  private companion object {
    const val BASE_URL = "http://shsd.buaa.edu.cn"
  }
}
