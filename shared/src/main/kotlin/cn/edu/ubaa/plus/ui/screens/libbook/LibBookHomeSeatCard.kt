package cn.edu.ubaa.plus.ui.screens.libbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.model.dto.canSignIn
import cn.edu.ubaa.plus.model.dto.isInUseBooking
import cn.edu.ubaa.plus.model.dto.isTerminalBooking
import cn.edu.ubaa.plus.model.format.formatUbaaDateWithTimeRange
import cn.edu.ubaa.plus.ui.theme.ubaaGlassCardColors
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface
import kotlinx.datetime.LocalDateTime

/**
 * 首页「待办」区里的图书馆座位卡片。
 *
 * 只在当前确实有未结束的预约时渲染；状态、剩余可签到时间与可执行操作都与「我的预约」保持一致， 额外用主色描边 + 「图书馆座位」标签让它在待办列表里一眼可辨。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibBookHomeSeatCard(
    booking: LibBookBookingDto,
    now: LocalDateTime,
    isCanceling: Boolean,
    isSubmitting: Boolean,
    onAction: (LibBookCardAction) -> Unit,
    isLoading: Boolean = false,
    onOpenBookings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
  val place =
      booking.nameMerge.ifBlank {
        listOf(booking.areaName, booking.seatNo).filter { it.isNotBlank() }.joinToString(" / ")
      }
  val deadline = libBookCheckInDeadline(booking)
  val inUse = booking.isInUseBooking()
  val statusText = libBookHomeStatusLabel(booking).ifBlank { booking.status.ifBlank { "已预约" } }

  Card(
      // 磨砂层已经自带描边，这里不再加第二层 border，避免出现双线。
      modifier = modifier.ubaaGlassSurface().fillMaxWidth(),
      colors = ubaaGlassCardColors(),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
          Icon(
              imageVector = Icons.Default.Chair,
              contentDescription = "图书馆座位",
              modifier = Modifier.padding(10.dp).size(22.dp),
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
              text = place.ifBlank { "图书馆座位" },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
          )
          Text(
              text = "图书馆座位 · $statusText",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
          )
        }
        if (isLoading) {
          Text(
              text = "刷新中",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Text(
          text =
              formatUbaaDateWithTimeRange(booking.day, booking.beginTime, booking.endTime).ifBlank {
                "时间未知"
              },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (booking.seatNo.isNotBlank() && place != booking.seatNo) {
        Text(
            text = "座位号 ${booking.seatNo}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (!inUse && deadline != null) {
        val remaining = libBookRemainingMinutes(deadline, now)
        Text(
            text =
                if (remaining > 0) {
                  "请在 ${formatLibBookClock(deadline)} 前签到（剩余 $remaining 分钟）"
                } else {
                  "已超过签到时间，系统可能已自动取消并记违约"
                },
            color =
                if (remaining > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
      }

      val actions = booking.cardActions()
      if (actions.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          actions.forEach { action ->
            AssistChip(
                onClick = { onAction(action) },
                enabled = !isCanceling && !isSubmitting,
                label = { Text(actionLabel(booking, action, isCanceling)) },
            )
          }
        }
      }

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onOpenBookings) { Text("查看我的预约") }
      }
    }
  }
}

/**
 * 首页卡片的状态文案。
 *
 * 只信任接口给的中文文案；接口没给时不要直接把 status 数字码露出来（3/4/6 对用户没有意义）， 而是按可执行状态给出人话。
 */
internal fun libBookHomeStatusLabel(booking: LibBookBookingDto): String {
  val name = booking.statusName.trim()
  if (name.isNotBlank() && name != booking.status.trim()) return name
  return when {
    booking.isTerminalBooking() -> "已结束"
    booking.isInUseBooking() -> "使用中"
    booking.canSignIn() -> "待签到"
    else -> "已预约"
  }
}

private fun actionLabel(
    booking: LibBookBookingDto,
    action: LibBookCardAction,
    isCanceling: Boolean,
): String =
    when (action) {
      LibBookCardAction.CANCEL -> if (isCanceling) "取消中..." else "取消预约"
      LibBookCardAction.RETURN_SIGN_IN -> "返回签到"
      LibBookCardAction.LEAVE -> "临时离开"
      LibBookCardAction.CHECKOUT -> "完全离开"
      LibBookCardAction.SIGN_IN -> if (booking.canSignIn()) "扫码签到" else "扫码操作"
    }
