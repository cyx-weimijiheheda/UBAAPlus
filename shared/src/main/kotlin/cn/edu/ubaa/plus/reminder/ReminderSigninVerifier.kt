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
 * 智慧教室的今日课程列表只包含**可签到**的课；有些课根本不需要签到、也不能在 UBAA 签到，它们不会出现在这个列表里。 所以：
 * - 查到这门课且已签到 → 不发；
 * - 查到这门课且未签到 → 发签到提醒；
 * - 查不到这门课 → 说明不可签到，只提醒开课，不提醒签到；
 * - 接口失败/超时 → 无法判断，保守照发，避免因为网络波动漏掉真签到。
 */
class ReminderSigninVerifier(
    private val loadTodayClasses: suspend () -> Result<SigninStatusResponse> = {
      SigninApi().getTodayClassesStrict()
    }
) {
  suspend fun decide(event: ReminderEvent): ReminderSigninDecision {
    if (event.kind != ReminderEventKind.ICLASS_SIGN_IN) return ReminderSigninDecision.NOTIFY
    val name = event.courseName?.trim().orEmpty()
    if (name.isEmpty()) return ReminderSigninDecision.NOTIFY

    val response = fetch() ?: return ReminderSigninDecision.NOTIFY
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

  /** 接口不可用时返回 null，由调用方决定保守策略。 */
  private suspend fun fetch(): SigninStatusResponse? =
      withTimeoutOrNull(8.seconds) { runCatching { loadTodayClasses() }.getOrNull()?.getOrNull() }

  /**
   * 在签到名单里找这门课。
   *
   * 先按课程名精确匹配；同名课程（例如同一门课一天两段）再用上课时间区分。名字带空格/全角差异时退回包含匹配。
   */
  private fun matchCourse(
      classes: List<SigninClassDto>,
      courseName: String,
      beginTime: String?,
  ): SigninClassDto? {
    val exact = classes.filter { it.courseName.trim() == courseName }
    if (exact.isEmpty()) {
      return classes.firstOrNull {
        val other = it.courseName.trim()
        other.isNotEmpty() && (other.contains(courseName) || courseName.contains(other))
      }
    }
    if (exact.size == 1) return exact.first()
    val begin = beginTime?.trim().orEmpty()
    return exact.firstOrNull { it.classBeginTime.trim() == begin } ?: exact.first()
  }
}
