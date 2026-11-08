package cn.edu.ubaa.plus.ui.screens.libbook

import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.model.dto.LibBookQrScanResultDto

/**
 * 座位二维码返回值解释。
 *
 * 上游 /v4/space/qr_scan 只认 1 = 已签到落座，其余码含义未公开。之前界面只把原始返回值当文字丢出来，没用。这里把 已知码翻译成人话，未知码显式说明「未确认」而不是假装懂。
 */
internal const val LIB_BOOK_SCAN_CODE_LANDED = "1"

/** 已知码 → 文案。只写确实验证过的。 */
private val libBookScanCodeLabels: Map<String, String> =
    mapOf(
        LIB_BOOK_SCAN_CODE_LANDED to "已签到落座",
        "0" to "操作已提交",
        "2" to "座位状态异常，请到座位屏确认",
        "3" to "当前不在可签到时间段",
        "7" to "处于临时离开状态",
    )

/** 扫码结果是否意味着已经落座（签到成功）。 */
fun LibBookQrScanResultDto.qrScanLanded(): Boolean {
  val raw = code.ifBlank { status }.trim()
  return raw == LIB_BOOK_SCAN_CODE_LANDED ||
      statusName.contains("使用中") ||
      statusName.contains("已签到") ||
      statusName.contains("签到成功")
}

/**
 * 扫码后要展示给人的一句话。
 *
 * 优先级：上游文案 > 上方已知码解释 > 已签到意味着落座 > 明确告知这是未确认的返回值。
 */
fun LibBookQrScanResultDto.scanResultMessage(): String {
  val raw = code.ifBlank { status }.trim()
  val upstream = message.trim()
  if (upstream.isNotEmpty()) return upstream
  libBookScanCodeLabels[raw]?.let {
    return it
  }
  if (qrScanLanded()) return "已签到落座"
  val statusText = statusName.trim()
  if (statusText.isNotEmpty()) return statusText
  return if (raw.isEmpty()) {
    "未返回状态信息，请到「我的预约」确认座位状态"
  } else {
    "返回码 $raw（含义未确认），请到「我的预约」确认座位状态"
  }
}

/** 把扫码结果转成一条预约快照，用于通知正文里的座位与时段。 */
fun LibBookQrScanResultDto.toBookingSnapshot(): LibBookBookingDto =
    LibBookBookingDto(
        id = bookId,
        areaName = areaName,
        seatNo = seatNo,
        day = day,
        beginTime = beginTime,
        endTime = endTime,
        status = status,
        statusName = statusName.ifBlank { scanResultMessage() },
        seatId = seatId,
        query = query,
    )
