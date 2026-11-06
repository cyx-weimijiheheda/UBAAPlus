package cn.edu.ubaa.plus.reminder

import cn.edu.ubaa.plus.api.feature.SigninApi
import cn.edu.ubaa.plus.model.dto.SigninStatusResponse
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 签到提醒发出前的轻量校验。
 *
 * 只有“确实查到了这门课且已经签到”才抑制通知；接口失败、超时或课程对不上时照常提醒， 避免因为网络波动漏掉签到。
 */
class ReminderSigninVerifier(
    private val loadTodayClasses: suspend () -> Result<SigninStatusResponse> = {
      SigninApi().getTodayClassesStrict()
    }
) {
  suspend fun shouldNotify(event: ReminderEvent): Boolean {
    if (event.kind != ReminderEventKind.ICLASS_SIGN_IN) return true
    val name = event.courseName?.trim().orEmpty()
    if (name.isEmpty()) return true

    val response =
        withTimeoutOrNull(8.seconds) { runCatching { loadTodayClasses() }.getOrNull()?.getOrNull() }
            ?: return true
    val matched = response.data.firstOrNull { it.courseName.trim() == name } ?: return true
    return matched.signStatus == 0
  }
}
