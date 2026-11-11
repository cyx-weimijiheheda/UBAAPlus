package cn.edu.ubaa.plus.reminder

import cn.edu.ubaa.plus.api.feature.SigninApi
import cn.edu.ubaa.plus.model.dto.SigninClassDto
import cn.edu.ubaa.plus.model.dto.SigninStatusResponse
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/** 签到提醒的判定结果：发什么、还是不发。 */
enum class ReminderSigninDecision {
  /** 照常发签到提醒。 */
  NOTIFY,

  /** 已经签到了，不发。 */
  SUPPRESS_SIGNED_IN,

  /** 这门课不在智慧教室签到名单里，只提醒开课，不提醒签到。 */
  SUPPRESS_NOT_SIGNABLE,
}

/**
 * 签到提醒发出前的校验。
 *
 * 智慧教室的今日课程列表只包含**可签到**的课；不需要签到的课根本不会出现，所以「查不到」就等同于「不该提醒签到」。 这是本判定的核心依据：
 * - 查到这门课且已签到 → 不发；
 * - 查到这门课且未签到 → 发签到提醒；
 * - 查不到这门课 → 不可签到，只提醒开课；
 * - 接口失败/超时 → 也按「不可签到」处理。宁可漏一次签到提醒，也不要给不需要签到的课反复打扰。
 */
class ReminderSigninVerifier(
    private val loadTodayClasses: suspend () -> Result<SigninStatusResponse> = {
      SigninApi().getTodayClassesStrict()
    }
) {
  suspend fun decide(event: ReminderEvent): ReminderSigninDecision {
    if (event.kind != ReminderEventKind.ICLASS_SIGN_IN) return ReminderSigninDecision.NOTIFY

    val response = fetch() ?: return ReminderSigninDecision.SUPPRESS_NOT_SIGNABLE
    // 名单为空同样说明今天没有可签到的课。
    if (response.data.isEmpty()) return ReminderSigninDecision.SUPPRESS_NOT_SIGNABLE

    val name = event.courseName?.normalizeCourseName().orEmpty()
    if (name.isEmpty()) return ReminderSigninDecision.SUPPRESS_NOT_SIGNABLE

    val matched =
        matchCourse(response.data, name, event.beginTime)
            ?: return ReminderSigninDecision.SUPPRESS_NOT_SIGNABLE
    return if (matched.signStatus == 0) {
      ReminderSigninDecision.NOTIFY
    } else {
      ReminderSigninDecision.SUPPRESS_SIGNED_IN
    }
  }

  /** 兼容旧调用：只有「该发」才返回 true。 */
  suspend fun shouldNotify(event: ReminderEvent): Boolean =
      decide(event) == ReminderSigninDecision.NOTIFY

  /** 接口不可用时返回 null，由调用方按「不可签到」处理。 */
  private suspend fun fetch(): SigninStatusResponse? =
      withTimeoutOrNull(8.seconds) { runCatching { loadTodayClasses() }.getOrNull()?.getOrNull() }

  /**
   * 在签到名单里找这门课。
   *
   * 先按课程名精确匹配；同名课程（例如同一门课一天两段）再用上课时间区分。名字带括号后缀、空格或全角差异时退回包含匹配。
   */
  private fun matchCourse(
      classes: List<SigninClassDto>,
      courseName: String,
      beginTime: String?,
  ): SigninClassDto? {
    val exact = classes.filter { it.courseName.normalizeCourseName() == courseName }
    if (exact.isEmpty()) {
      return classes.firstOrNull {
        val other = it.courseName.normalizeCourseName()
        other.isNotEmpty() && (other.contains(courseName) || courseName.contains(other))
      }
    }
    if (exact.size == 1) return exact.first()
    val begin = normalizeClockText(beginTime)
    return exact.firstOrNull { normalizeClockText(it.classBeginTime) == begin } ?: exact.first()
  }
}

/** 去掉空格与全角字符差异，便于跨数据源比对课程名。 */
internal fun String.normalizeCourseName(): String =
    trim().replace(" ", "").replace("\u3000", "").replace("（", "(").replace("）", ")")

/** 把 `8:0`、`08:00:00` 之类统一成 `HH:mm`，便于比对上课时间。 */
internal fun normalizeClockText(raw: String?): String {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return ""
  val parts = text.split(':', '.').filter { it.isNotBlank() }
  val hour = parts.getOrNull(0)?.toIntOrNull() ?: return text
  val minute = parts.getOrNull(1)?.toIntOrNull() ?: return text
  return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}
