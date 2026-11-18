package cn.edu.ubaa.plus.ui.screens.cgyy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.CgyyOrderDisplayColor
import cn.edu.ubaa.plus.model.dto.CgyyOrderDto
import cn.edu.ubaa.plus.model.dto.CgyyPurposeTypeDto
import cn.edu.ubaa.plus.model.dto.canCancelAt
import cn.edu.ubaa.plus.model.dto.displayReservationDateText
import cn.edu.ubaa.plus.model.dto.displayStatus
import cn.edu.ubaa.plus.ui.common.components.UbaaAlertDialog
import cn.edu.ubaa.plus.ui.common.components.UbaaEmptyState
import cn.edu.ubaa.plus.ui.common.components.UbaaErrorState
import cn.edu.ubaa.plus.ui.common.components.UbaaLoadingState
import cn.edu.ubaa.plus.ui.common.components.UbaaSectionCard
import cn.edu.ubaa.plus.ui.common.components.UbaaSegmentedTabs
import cn.edu.ubaa.plus.ui.common.components.UbaaStatusBadge
import cn.edu.ubaa.plus.ui.common.components.UbaaTextButton
import cn.edu.ubaa.plus.ui.common.components.UbaaToggleRow
import cn.edu.ubaa.plus.ui.theme.UbaaColors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun shouldShowCancelAction(order: CgyyOrderDto): Boolean = order.canCancelAt()

@Composable
internal fun CgyyOrderDisplayColor.toComposeColor(): Color =
    when (this) {
      CgyyOrderDisplayColor.SUCCESS -> UbaaColors.current.success
      CgyyOrderDisplayColor.ERROR -> MaterialTheme.colorScheme.error
      CgyyOrderDisplayColor.INFO -> UbaaColors.current.info
      CgyyOrderDisplayColor.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
internal fun CgyyMessageBanner(message: String, modifier: Modifier = Modifier) {
  Surface(
      modifier = modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
  ) {
    Text(
        text = message,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
  }
}

@Composable
internal fun CgyySectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
  UbaaSectionCard(modifier = modifier, title = title, subtitle = subtitle) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CgyyChipFlowRow(content: @Composable () -> Unit) {
  FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      content = { content() },
  )
}

@Composable
internal fun CgyyLoadingState(message: String, modifier: Modifier = Modifier) {
  UbaaLoadingState(modifier = modifier.fillMaxSize(), text = message)
}

@Composable
internal fun CgyyErrorState(message: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  UbaaErrorState(message = message ?: "加载失败", onRetry = onRetry, modifier = modifier.fillMaxSize())
}

@Composable
internal fun CgyyToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
  UbaaToggleRow(label, checked, onCheckedChange)
}

@Composable
internal fun CgyyPurposeTypeDialog(
    options: List<CgyyPurposeTypeDto>,
    selected: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
  UbaaAlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("选择活动类型") },
      text = {
        UbaaSegmentedTabs(
            labels = options.map { it.name },
            selectedIndex = options.indexOfFirst { it.key == selected },
            onSelected = { onSelect(options[it].key) },
        )
      },
      confirmButton = {},
  )
}

@Composable
internal fun CgyyOrderDetailDialog(
    order: CgyyOrderDto,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
) {
  val status = order.displayStatus()
  val detailText = status.detailText
  val rejectionReason = order.checkContent
  UbaaAlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(order.venueSpaceName ?: order.venueName.orEmpty()) },
      text = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CgyyDetailRow("订单号", order.tradeNo ?: order.id.toString())
          CgyyDetailRow("状态", status.primaryText, status.color.toComposeColor())
          if (!detailText.isNullOrBlank() && detailText != status.primaryText) {
            CgyyDetailRow("审批阶段", detailText)
          }
          if (!rejectionReason.isNullOrBlank() && (order.checkStatus ?: 0) < 0) {
            CgyyDetailRow("驳回原因", rejectionReason, MaterialTheme.colorScheme.error)
          }
          CgyyDetailRow("日期", order.displayReservationDateText())
          CgyyDetailRow(
              "场地",
              listOf(order.venueName, order.siteName).filterNotNull().joinToString(" "),
          )
          CgyyDetailRow("房间", order.venueSpaceName.orEmpty())
          CgyyDetailRow("主题", order.theme.orEmpty())
          CgyyDetailRow("活动类型", order.purposeTypeName ?: order.purposeType?.toString().orEmpty())
          CgyyDetailRow("参与人数", order.joinerNum?.toString() ?: "-")
          CgyyDetailRow("活动内容", order.activityContent.orEmpty())
          CgyyDetailRow("参与人", order.joiners.orEmpty())
        }
      },
      confirmButton = {
        if (shouldShowCancelAction(order)) {
          UbaaTextButton(
              onClick = onCancel,
              colors =
                  androidx.compose.material3.ButtonDefaults.textButtonColors(
                      contentColor = MaterialTheme.colorScheme.error
                  ),
          ) {
            Text("取消预约")
          }
        }
      },
      dismissButton = {},
  )
}

@Composable
private fun CgyyDetailRow(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
  }
}

internal fun JsonElement?.toRawDisplayText(): String =
    when (this) {
      null -> "null"
      is JsonPrimitive -> this.contentOrNull ?: this.toString()
      else -> this.toString()
    }

@Composable
internal fun CgyyEmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
  UbaaEmptyState(title = title, subtitle = subtitle, modifier = modifier.fillMaxSize())
}

@Composable
internal fun CgyyStatusChip(text: String, color: Color) {
  UbaaStatusBadge(text = text, containerColor = color.copy(alpha = 0.12f), contentColor = color)
}
