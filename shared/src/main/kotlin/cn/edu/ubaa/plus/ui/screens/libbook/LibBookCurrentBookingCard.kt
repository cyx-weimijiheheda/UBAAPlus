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
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.model.dto.canSignIn
import cn.edu.ubaa.plus.model.dto.isInUseBooking
import cn.edu.ubaa.plus.model.format.formatUbaaDateWithTimeRange
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface
import kotlinx.datetime.LocalDateTime

/**
 * 图书馆座位主界面顶部的「当前预约」卡片。
 *
 * 只显示一条尚未结束的预约；状态、签到倒计时与可执行操作和首页、历史页保持一致。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibBookCurrentBookingCard(
    booking: LibBookBookingDto,
    now: LocalDateTime,
    isCanceling: Boolean,
    isSubmitting: Boolean,
    onAction: (LibBookCardAction, String) -> Unit,
    onShowRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val place =
      booking.nameMerge.ifBlank {
        listOf(booking.areaName, booking.seatNo).filter { it.isNotBlank() }.joinToString(" / ")
      }
  val deadline = libBookCheckInDeadline(booking)
  val inUse = booking.isInUseBooking()

  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .ubaaGlassSurface(shape = MaterialTheme.shapes.large)
              .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            modifier = Modifier.padding(8.dp).size(20.dp),
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
            text = "当前预约 · ${libBookHomeStatusLabel(booking)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
      }
      // 规则全文只在确认预约对话框里展示，这里只留一个不显眼的入口。
      IconButton(onClick = onShowRules) {
        Icon(
            imageVector = Icons.Default.HelpOutline,
            contentDescription = "座位规则",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Text(
        text =
            formatUbaaDateWithTimeRange(booking.day, booking.beginTime, booking.endTime).ifBlank {
              "时间未知"
            },
        style = MaterialTheme.typography.bodySmall,
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
              onClick = { onAction(action, booking.id) },
              enabled = !isCanceling && !isSubmitting,
              label = {
                Text(
                    when (action) {
                      LibBookCardAction.CANCEL -> if (isCanceling) "取消中..." else "取消预约"
                      LibBookCardAction.RETURN_SIGN_IN -> "返回签到"
                      LibBookCardAction.LEAVE -> "临时离开"
                      LibBookCardAction.CHECKOUT -> "完全离开"
                      LibBookCardAction.SIGN_IN -> if (booking.canSignIn()) "扫码签到" else "扫码操作"
                    }
                )
              },
          )
        }
      }
    }
  }
}
