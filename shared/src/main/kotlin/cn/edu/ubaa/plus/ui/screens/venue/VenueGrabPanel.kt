package cn.edu.ubaa.plus.ui.screens.venue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.ubaa.plus.api.venue.VenueGrabPhase
import cn.edu.ubaa.plus.api.venue.VenueGrabState
import cn.edu.ubaa.plus.api.venue.VenueGrabStopReason
import cn.edu.ubaa.plus.api.venue.VenueSpaceMode
import cn.edu.ubaa.plus.model.format.formatUbaaDate
import cn.edu.ubaa.plus.model.format.formatUbaaDateTime
import cn.edu.ubaa.plus.ui.common.components.GrabConfirmDialog

/** 运动场地抢单面板：定时、轮询间隔、开始/停止、日志与结果。 */
@Composable
fun VenueGrabPanel(
    state: VenueUiState,
    grabState: VenueGrabState,
    onUseScheduleChange: (Boolean) -> Unit,
    onGrabAtChange: (String) -> Unit,
    onPollIntervalChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
  val alreadyReleased = state.dayInfo != null
  var showConfirm by remember { mutableStateOf(false) }
  val active =
      grabState.phase == VenueGrabPhase.WAITING ||
          grabState.phase == VenueGrabPhase.RUNNING ||
          grabState.phase == VenueGrabPhase.NEEDS_CAPTCHA

  Card(
      modifier = Modifier.fillMaxWidth(),
      colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "抢场地",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = phaseLabel(grabState),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (active) {
        Text(
            text =
                when (grabState.phase) {
                  VenueGrabPhase.WAITING -> "等待开抢时间…"
                  VenueGrabPhase.NEEDS_CAPTCHA -> "请在上方验证码弹窗中完成点击"
                  else -> "正在尝试（第 ${grabState.attempt} 次）"
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Icon(Icons.Default.Stop, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("停止抢场地")
        }
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(
              checked = state.useSchedule && !alreadyReleased,
              onCheckedChange = onUseScheduleChange,
              enabled = !alreadyReleased,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
              text =
                  if (alreadyReleased) {
                    "该日期已放票，将立即开始抢场地"
                  } else {
                    "定时开抢（每天 ${state.grabAtText}）"
                  },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }

        if (!alreadyReleased && state.useSchedule) {
          OutlinedTextField(
              value = state.grabAtText,
              onValueChange = onGrabAtChange,
              label = { Text("开抢时间 HH:mm[:ss]") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
        }

        OutlinedTextField(
            value = state.pollIntervalText,
            onValueChange = { value ->
              if (value.isEmpty() || value.matches(Regex("^\\d{0,2}(\\.\\d{0,2})?$"))) {
                onPollIntervalChange(value)
              }
            },
            label = { Text("重试间隔（秒，0.2–10）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { showConfirm = true }, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Default.Bolt, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("开始抢场地")
        }
      }

      grabState.success?.let { order ->
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "预约成功",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            order.tradeNo?.let { Text("订单号：$it", style = MaterialTheme.typography.bodySmall) }
            order.startAt?.let {
              Text("开始：${formatUbaaDateTime(it)}", style = MaterialTheme.typography.bodySmall)
            }
            order.endAt?.let {
              Text("结束：${formatUbaaDateTime(it)}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              grabState.payUrl?.let { url -> Button(onClick = { onOpenUrl(url) }) { Text("去支付") } }
              OutlinedButton(onClick = { onOpenUrl("https://cgyy.buaa.edu.cn/venue/orders") }) {
                Text("查看订单")
              }
            }
            if (grabState.payUrl == null) {
              Text(
                  text = "如需付款，请在订单页手动完成支付。",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      if (!active && grabState.phase != VenueGrabPhase.IDLE) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = grabState.message,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (grabState.phase == VenueGrabPhase.FAILED) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.onTertiaryContainer
                },
        )
      }

      if (grabState.logs.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "运行日志",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier =
                Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())
        ) {
          grabState.logs.takeLast(40).forEach { line ->
            Text(
                text = line,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
          }
        }
      }
    }
  }

  if (showConfirm) {
    GrabConfirmDialog(
        title = "确认开始抢场地",
        targetLines = venueTargetLines(state, alreadyReleased),
        onConfirm = {
          showConfirm = false
          onStart()
        },
        onDismiss = { showConfirm = false },
    )
  }
}

private fun venueTargetLines(state: VenueUiState, alreadyReleased: Boolean): List<String> =
    buildList {
      state.selectedSite?.let { add("场地：${it.displayName}") }
      add("日期：${formatUbaaDate(state.selectedDate)}")
      state.selectedStartBegin?.let { add("时段：$it 起 ${state.durationHours} 小时") }
      add("场地模式：${if (state.spaceMode == VenueSpaceMode.FIXED) "指定场地" else "自动选场地"}")
      if (state.selectedBuddyIds.isNotEmpty()) {
        add("同伴：${state.selectedBuddyIds.size} 位")
      }
      add(
          if (!alreadyReleased && state.useSchedule) {
            "开抢时间：每天 ${state.grabAtText}"
          } else {
            "开抢时间：立即开始"
          }
      )
      add("重试间隔：${state.pollIntervalText} 秒")
    }

private fun phaseLabel(state: VenueGrabState): String =
    when (state.phase) {
      VenueGrabPhase.IDLE -> "未开始"
      VenueGrabPhase.WAITING -> "等待开抢"
      VenueGrabPhase.RUNNING -> "抢购中"
      VenueGrabPhase.NEEDS_CAPTCHA -> "等待验证码"
      VenueGrabPhase.SUCCESS -> "已成功"
      VenueGrabPhase.STOPPED ->
          when (state.stopReason) {
            VenueGrabStopReason.BACKGROUND -> "后台停止"
            VenueGrabStopReason.CAPTCHA_CANCELLED -> "已取消"
            VenueGrabStopReason.LOGOUT -> "已注销"
            else -> "已停止"
          }
      VenueGrabPhase.FAILED -> "失败"
    }
