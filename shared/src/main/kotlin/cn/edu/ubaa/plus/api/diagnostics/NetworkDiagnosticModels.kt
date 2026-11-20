package cn.edu.ubaa.plus.api.diagnostics

import cn.edu.ubaa.plus.api.ConnectionMode
import kotlinx.serialization.Serializable

internal enum class DiagnosticRoute {
  SELECTED,
  DIRECT,
  LIBRARY,
  LIBRARY_DIRECT,
  PUBLIC,
}

internal data class DiagnosticTarget(
    val id: String,
    val title: String,
    val url: String,
    val route: DiagnosticRoute = DiagnosticRoute.SELECTED,
)

internal fun diagnosticTargets(
    serviceBase: String = cn.edu.ubaa.plus.BuildKonfig.API_ENDPOINT
): List<DiagnosticTarget> =
    listOf(
        DiagnosticTarget("auth", "统一认证", "https://uc.buaa.edu.cn/api/uc/status"),
        DiagnosticTarget(
            "app_service",
            "更新与公告",
            serviceBase.trimEnd('/') + "/health/live",
            DiagnosticRoute.PUBLIC,
        ),
        DiagnosticTarget(
            "schedule",
            "课表查询",
            "https://byxt.buaa.edu.cn/jwapp/sys/homeapp/index.html",
        ),
        DiagnosticTarget("classroom", "空教室查询", "https://app.buaa.edu.cn/site/classRoomQuery/index"),
        DiagnosticTarget("iclass", "智慧教室", "https://iclass.buaa.edu.cn:8347/"),
        DiagnosticTarget(
            "libbook",
            "图书馆座位",
            "https://booking.lib.buaa.edu.cn/",
            DiagnosticRoute.LIBRARY,
        ),
        DiagnosticTarget(
            "libservice",
            "图书借阅",
            "https://space.lib.buaa.edu.cn/space/",
            DiagnosticRoute.LIBRARY_DIRECT,
        ),
        DiagnosticTarget("spoc", "SPOC作业", "https://spoc.buaa.edu.cn/"),
        DiagnosticTarget("bykc", "博雅课程", "https://bykc.buaa.edu.cn/"),
        DiagnosticTarget(
            "venue",
            "运动场地",
            "https://cgyy.buaa.edu.cn/venue/",
            DiagnosticRoute.DIRECT,
        ),
        DiagnosticTarget("ygdk", "阳光打卡", "https://ygdk.buaa.edu.cn/"),
        DiagnosticTarget(
            "cgyy",
            "研讨室预约",
            "https://cgyy.buaa.edu.cn/venue-zhjs/",
            DiagnosticRoute.DIRECT,
        ),
        DiagnosticTarget("dorm", "寝室用电", "http://shsd.buaa.edu.cn/PubBuaa", DiagnosticRoute.DIRECT),
        DiagnosticTarget("network", "校园网络", "https://zfw.buaa.edu.cn/home"),
        DiagnosticTarget("card", "校园卡", "https://pass.cc-pay.cn/mcampcard"),
        DiagnosticTarget("finance", "财务查询", "https://chaxun.icw.buaa.edu.cn/zl/tjtb"),
        DiagnosticTarget("evaluation", "自动评教", "https://byxt.buaa.edu.cn/"),
        DiagnosticTarget("exam", "考试查询", "https://byxt.buaa.edu.cn/"),
        DiagnosticTarget("grade", "成绩查询", "https://byxt.buaa.edu.cn/"),
        DiagnosticTarget("judge", "希冀作业", "https://judge.buaa.edu.cn/"),
    )

@Serializable
internal enum class ProbeStatus(val label: String) {
  IDLE("未检测"),
  RUNNING("检测中"),
  SUCCESS("通过"),
  RESPONSE("已响应"),
  AUTH_REQUIRED("需登录"),
  SKIPPED("未检测"),
  TIMEOUT("超时"),
  FAILED("失败"),
  CANCELED("已停止"),
}

@Serializable
internal enum class ProbeReason(val text: String) {
  OK("读取成功"),
  HTTP_RESPONSE("服务已响应"),
  LOGIN_REDIRECT("服务已响应登录跳转"),
  HTTP_ERROR("服务返回非成功状态"),
  LOGIN_REQUIRED("登录后可检测业务读取"),
  SUBSYSTEM_LOGIN("请先进入该功能完成登录"),
  UNSUPPORTED("当前账号不支持此接口"),
  NO_DATA("缺少可用于查询的数据"),
  TIMEOUT("请求超时"),
  DNS("域名解析失败"),
  TLS("TLS连接失败"),
  NETWORK("网络连接失败"),
  AUTH("认证已失效"),
  PERMISSION("无访问权限"),
  PARSE("响应格式异常"),
  UPSTREAM("服务暂不可用"),
  UNKNOWN("读取未完成"),
  STOPPED("检测已停止"),
}

@Serializable
internal data class ProbeResult(
    val status: ProbeStatus = ProbeStatus.IDLE,
    val reason: ProbeReason? = null,
    val elapsedMs: Long? = null,
    val httpStatus: Int? = null,
)

@Serializable
internal data class DiagnosticRow(
    val id: String,
    val title: String,
    val route: String,
    val network: ProbeResult = ProbeResult(),
    val business: ProbeResult = ProbeResult(),
    val checkedAt: Long? = null,
)

internal fun DiagnosticTarget.actualMode(mode: ConnectionMode): ConnectionMode =
    when (route) {
      DiagnosticRoute.DIRECT,
      DiagnosticRoute.LIBRARY_DIRECT,
      DiagnosticRoute.PUBLIC -> ConnectionMode.DIRECT
      else -> mode
    }

internal fun DiagnosticRow.isProblem() =
    listOf(network, business).any {
      it.status in setOf(ProbeStatus.FAILED, ProbeStatus.TIMEOUT, ProbeStatus.AUTH_REQUIRED)
    }
