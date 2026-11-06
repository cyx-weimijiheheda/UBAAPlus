package cn.edu.ubaa.plus.ui.screens.libbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.model.dto.canSignIn
import cn.edu.ubaa.plus.model.dto.isInUseBooking
import cn.edu.ubaa.plus.model.format.formatUbaaDateWithTimeRange
import kotlinx.datetime.LocalDateTime

/** 首页“待办”区里的图书馆座位卡片；操作与「我的预约」一致。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibBookHomeSeatCard(
    booking: LibBookBookingDto,
    now: LocalDateTime,
    isCanceling: Boolean,
    isSubmitting: Boolean,
    onAction: (LibBookCardAction) -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text =
                booking.nameMerge
                    .ifBlank {
                      listOf(booking.areaName, booking.seatNo)
                          .filter { it.isNotBlank() }
                          .joinToString(" / ")
                    }
                    .ifBlank { "图书馆座位" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = booking.statusName.ifBlank { booking.status.ifBlank { "已预约" } },
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
      }
      Text(
          text =
              formatUbaaDateWithTimeRange(booking.day, booking.beginTime, booking.endTime).ifBlank {
                "时间未知"
              },
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (booking.seatNo.isNotBlank()) {
        Text("座位：${booking.seatNo}", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      val deadline = libBookCheckInDeadline(booking)
      if (!booking.isInUseBooking() && deadline != null) {
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

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        booking.cardActions().forEach { action ->
          AssistChip(
              onClick = { onAction(action) },
              enabled = !isCanceling && !isSubmitting,
              label = { Text(actionLabel(booking, action, isCanceling)) },
          )
        }
      }
    }
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
