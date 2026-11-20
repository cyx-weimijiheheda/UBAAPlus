package cn.edu.ubaa.plus.api.feature

/**
 * 课堂签到链接。
 *
 * 学校在每节课前会放出可扫码签到的二维码，二维码内容就是本链接；除时间戳外全部固定。 这里负责把它拼出来，交给剪贴板或浏览器使用——打开即走学校的原始签到入口，
 * 不伪造签名，也不代替学校做身份校验。
 */
object IclassSigninLink {
  /** 二维码里使用的真实入口：8081 的 HTTP 地址。 */
  const val BASE_URL = "http://iclass.buaa.edu.cn:8081/app/course/stu_scan_sign.action"

  /**
   * 拼出签到链接。
   *
   * @param courseSchedId 排课 ID，来自 get_socket_info 的 result.courseSchedId；`0` 表示当前不在课时。
   * @param timestamp 服务端毫秒时间戳，必须是整数：传成字符串会被上游静默丢弃。
   * @param userId 业务会话里的用户 ID，链接里带上它才能在浏览器直接打开。
   */
  fun build(courseSchedId: String, timestamp: Long, userId: String? = null): String {
    val params = buildList {
      add("courseSchedId=" + encode(courseSchedId))
      add("timestamp=" + timestamp)
      userId?.takeIf { it.isNotBlank() }?.let { add("id=" + encode(it)) }
    }
    return BASE_URL + "?" + params.joinToString("&")
  }

  /** 排课 ID 是否指向一节课；`0`、空串都表示当前没有课。 */
  fun isUsableCourseSchedId(value: String?): Boolean {
    val text = value?.trim().orEmpty()
    return text.isNotEmpty() && text != "0"
  }

  private fun encode(value: String): String =
      java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
