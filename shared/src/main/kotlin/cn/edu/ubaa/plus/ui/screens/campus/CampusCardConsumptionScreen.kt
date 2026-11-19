package cn.edu.ubaa.plus.ui.screens.campus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.feature.*
import cn.edu.ubaa.plus.ui.common.components.*
import kotlin.time.Instant
import kotlinx.datetime.*

/** Date selection constraints shared by both calendar dialogs and unit tests. */
internal fun consumptionDateSelectable(
    date: LocalDate,
    pickingStart: Boolean,
    start: LocalDate,
    end: LocalDate,
    today: LocalDate,
): Boolean = date <= today && if (pickingStart) date <= end else date >= start

internal fun androidx.compose.foundation.lazy.LazyListScope.campusConsumptionItems(
    state: ConsumptionUiState,
    model: CampusCardConsumptionViewModel,
    onPickDate: (Boolean) -> Unit,
) {
  item { UbaaSectionHeader("消费记录", modifier = Modifier.padding(top = 8.dp)) }
  item {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      ConsumptionDateField(
          "开始日期",
          state.start,
          { onPickDate(true) },
          Modifier.weight(1f),
          !state.loading,
      )
      Text("－", color = MaterialTheme.colorScheme.onSurfaceVariant)
      ConsumptionDateField(
          "截止日期",
          state.end,
          { onPickDate(false) },
          Modifier.weight(1f),
          !state.loading,
      )
    }
  }
  item {
    UbaaButton(
        onClick = model::query,
        loading = state.loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text("查询消费记录")
    }
  }
  state.error?.let { error ->
    item {
      UbaaSectionCard {
        Text(error, color = MaterialTheme.colorScheme.error)
        UbaaTextButton(onClick = model::query) { Text("重试") }
      }
    }
  }
  val snapshot = state.snapshot
  if (snapshot != null) {
    item {
      Text(
          "共 ${snapshot.trades.size} 笔消费 · 按时间从旧到新",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (snapshot.trades.isEmpty())
        item { UbaaEmptyState("该日期范围内没有消费记录", modifier = Modifier.fillMaxWidth()) }
    snapshot.trades
        .groupBy { it.dateTime.substringBefore(' ') }
        .toSortedMap()
        .forEach { (date, trades) ->
          item(key = "date:$date") {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                  date,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.weight(1f),
              )
              Text(
                  "${trades.size} 笔",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          itemsIndexed(trades, key = { i, _ -> "$date:$i" }) { _, trade ->
            ConsumptionTradeCard(trade)
          }
        }
  } else if (!state.loading && state.error == null)
      item { UbaaEmptyState("选择日期后点击查询", modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun ConsumptionDateField(
    label: String,
    date: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
  UbaaSecondaryButton(
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Column(Modifier.weight(1f)) {
      Text(
          label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(date.toString(), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
    Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConsumptionDateDialog(
    pickingStart: Boolean,
    start: LocalDate,
    end: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
  fun fromMillis(value: Long) =
      Instant.fromEpochMilliseconds(value).toLocalDateTime(TimeZone.UTC).date
  val dateState =
      rememberDatePickerState(
          initialSelectedDateMillis =
              (if (pickingStart) start else end).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
          selectableDates =
              object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    consumptionDateSelectable(
                        fromMillis(utcTimeMillis),
                        pickingStart,
                        start,
                        end,
                        today,
                    )

                override fun isSelectableYear(year: Int) =
                    year <= (if (pickingStart) end else today).year &&
                        (pickingStart || year >= start.year)
              },
      )
  val selected = dateState.selectedDateMillis?.let(::fromMillis)
  val colors =
      DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
  DatePickerDialog(
      onDismissRequest = onDismiss,
      modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp),
      shape = UbaaDialogShape,
      colors = colors,
      confirmButton = {
        UbaaTextButton(
            onClick = { selected?.let(onSelect) },
            enabled =
                selected != null &&
                    consumptionDateSelectable(selected, pickingStart, start, end, today),
        ) {
          Text("确定")
        }
      },
  ) {
    DatePicker(
        state = dateState,
        headline = null,
        showModeToggle = false,
        colors = colors,
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        title = {
          Row(
              Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                if (pickingStart) "选择开始日期" else "选择截止日期",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            UbaaIconAction(onClick = onDismiss, description = "关闭")
          }
        },
    )
  }
}

@Composable
private fun ConsumptionTradeCard(trade: CampusCardTrade) {
  UbaaSectionCard(
      title = trade.merchant.ifBlank { trade.title.ifBlank { "校园卡消费" } },
      trailing = {
        Text(
            "${trade.amount} 元",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
      },
  ) {
    if (trade.title.isNotBlank() && trade.title != trade.merchant)
        Text(trade.title, style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
          trade.dateTime.substringAfter(' '),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      trade.balance?.let {
        Text(
            "余额 $it 元",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
