package cn.edu.ubaa.plus.api.venue

import cn.edu.ubaa.plus.model.dto.CaptchaInfo

/** 运动场地的某个可预约项目（校区 · 场馆 · 项目）。 */
data class VenueSite(
    val id: Int,
    val siteName: String,
    val venueId: Int,
    val venueName: String,
    val campusName: String,
    val sportName: String,
    val openStart: String,
    val openEnd: String,
    val isFee: Boolean,
) {
  val displayName: String
    get() =
        listOf(campusName, venueName, siteName)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")

  val openRange: String
    get() = if (openStart.isBlank() || openEnd.isBlank()) "" else "（$openStart-$openEnd）"
}

/** 场地列表与可提前预约天数。 */
data class VenueSiteList(val sites: List<VenueSite>, val advanceReservationDays: Int)

/** 场地的真实时段（一天可能有非整点时段，不能假设固定小时）。 */
data class VenueTimeSlot(val timeId: Int, val beginTime: String, val endTime: String) {
  val label: String
    get() = "$beginTime-$endTime"
}

/** 单个场地在某个时段的占用状态。status: 1 空闲 / 2 停用 / 3 未支付 / 4 已预约。 */
data class VenueSpaceSlot(val status: Int, val fee: Double?, val remark: String?) {
  val isFree: Boolean
    get() = status == STATUS_FREE

  companion object {
    const val STATUS_FREE = 1
    const val STATUS_DISABLED = 2
    const val STATUS_UNPAID = 3
    const val STATUS_RESERVED = 4
  }
}

/** 场地（例如某片羽毛球场）。slots 以 timeId 为键。 */
data class VenueSpace(
    val spaceId: Int,
    val spaceName: String,
    val groupId: Int?,
    val slots: Map<Int, VenueSpaceSlot>,
) {
  fun slot(timeId: Int): VenueSpaceSlot? = slots[timeId]
}

/** 同伴。 */
data class VenueBuddy(
    val id: Int,
    val name: String,
    val userUid: String,
    val userId: Int?,
    val roleId: Int?,
)

/** 某天某个场地的完整可用信息。 */
data class VenueDayInfo(
    val siteId: Int,
    val date: String,
    val orderToken: String,
    val isFee: Boolean,
    val slots: List<VenueTimeSlot>,
    val spaces: List<VenueSpace>,
    val phone: String?,
    val isCaptchaCheck: Boolean,
    val buddies: List<VenueBuddy>,
    val buddyNumMin: Int,
    val buddyNumMax: Int,
    val reservationHour: Int,
)

/** 验证码上的一个点击坐标（原始图片像素）。 */
data class VenuePoint(val x: Float, val y: Float)

/** 点击文字验证码挑战。 */
data class VenueCaptchaChallenge(
    val token: String,
    val secretKey: String,
    val wordList: List<String>,
    val imageBase64: String,
)

/** 验证码校验通过后用于下单的凭证。 */
data class VenueCaptchaResult(val verification: String, val captchaToken: String)

/** 下单目标：某个场地 + 某个时段。 */
data class VenueSelection(val spaceId: Int, val timeId: Int, val groupId: Int?)

/** 下单请求。 */
data class VenueOrderRequest(
    val siteId: Int,
    val date: String,
    val selections: List<VenueSelection>,
    val phone: String?,
    val buddyIds: List<Int>,
    val orderPrice: Double?,
    val captchaVerification: String?,
    val captchaToken: String?,
    val orderToken: String?,
)

/** 下单结果。 */
data class VenueOrderResult(
    val orderId: Long?,
    val tradeNo: String?,
    val startAt: String?,
    val endAt: String?,
    val message: String,
)

/** 支付结果。无法自动付款，最多打开学校返回的支付页。 */
data class VenuePayResult(
    val payUrl: String?,
    val payFee: Double?,
    val payCode: String?,
    val message: String,
)

/** 会话建立结果。 */
sealed interface VenueAuthOutcome {
  /** 已有可用业务 token。 */
  data object Ready : VenueAuthOutcome

  /** 没有 SSO 会话，也没有可用凭据。 */
  data class NeedsCredentials(val message: String? = null) : VenueAuthOutcome

  /** CAS 登录需要验证码。 */
  data class NeedsCaptcha(
      val captcha: CaptchaInfo,
      val execution: String,
      val username: String,
  ) : VenueAuthOutcome

  data class Failed(val message: String) : VenueAuthOutcome
}

/** 运动场地 API 后端。当前只有直连实现。 */
internal interface VenueApiBackend {
  suspend fun resumeSession(): VenueAuthOutcome

  suspend fun login(
      username: String,
      password: String,
      captcha: String? = null,
      execution: String? = null,
  ): VenueAuthOutcome

  fun logout()

  suspend fun getSites(): Result<VenueSiteList>

  /** 尚未放票时返回 success(null)。 */
  suspend fun getDayInfo(siteId: Int, date: String): Result<VenueDayInfo?>

  suspend fun getBuddies(): Result<List<VenueBuddy>>

  suspend fun addBuddy(userUid: String): Result<Unit>

  suspend fun deleteBuddy(buddyId: Int): Result<Unit>

  suspend fun getCaptcha(): Result<VenueCaptchaChallenge>

  suspend fun verifyCaptcha(
      challenge: VenueCaptchaChallenge,
      points: List<VenuePoint>,
      imageWidth: Int,
      imageHeight: Int,
  ): Result<VenueCaptchaResult>

  suspend fun submitOrder(request: VenueOrderRequest): Result<VenueOrderResult>

  suspend fun pay(tradeNo: String, payType: Int = 13): Result<VenuePayResult>
}

/** 运动场地 API 门面，始终使用直连后端。 */
class VenueApi internal constructor(private val backend: VenueApiBackend) {
  constructor() : this(LocalVenueApi())

  suspend fun resumeSession(): VenueAuthOutcome = backend.resumeSession()

  suspend fun login(
      username: String,
      password: String,
      captcha: String? = null,
      execution: String? = null,
  ): VenueAuthOutcome = backend.login(username, password, captcha, execution)

  fun logout() = backend.logout()

  suspend fun getSites(): Result<VenueSiteList> = backend.getSites()

  suspend fun getDayInfo(siteId: Int, date: String): Result<VenueDayInfo?> =
      backend.getDayInfo(siteId, date)

  suspend fun getBuddies(): Result<List<VenueBuddy>> = backend.getBuddies()

  suspend fun addBuddy(userUid: String): Result<Unit> = backend.addBuddy(userUid)

  suspend fun deleteBuddy(buddyId: Int): Result<Unit> = backend.deleteBuddy(buddyId)

  suspend fun getCaptcha(): Result<VenueCaptchaChallenge> = backend.getCaptcha()

  suspend fun verifyCaptcha(
      challenge: VenueCaptchaChallenge,
      points: List<VenuePoint>,
      imageWidth: Int,
      imageHeight: Int,
  ): Result<VenueCaptchaResult> = backend.verifyCaptcha(challenge, points, imageWidth, imageHeight)

  suspend fun submitOrder(request: VenueOrderRequest): Result<VenueOrderResult> =
      backend.submitOrder(request)

  suspend fun pay(tradeNo: String, payType: Int = 13): Result<VenuePayResult> =
      backend.pay(tradeNo, payType)
}
