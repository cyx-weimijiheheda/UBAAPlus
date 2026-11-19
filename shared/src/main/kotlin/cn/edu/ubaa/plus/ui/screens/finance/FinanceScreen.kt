package cn.edu.ubaa.plus.ui.screens.finance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.feature.FinanceIncome
import cn.edu.ubaa.plus.ui.common.components.*
import java.math.BigDecimal

internal fun financeYuan(value: Long?): String =
    value?.let { BigDecimal.valueOf(it, 2).toPlainString() + " 元" } ?: "未提供"

internal fun financeMonth(entry: FinanceIncome): String {
  val y = entry.year?.toIntOrNull()
  val m = entry.month?.toIntOrNull()
  return if (y != null && m in 1..12) "%04d-%02d".format(java.util.Locale.ROOT, y, m) else "未提供所属年月"
}

@Composable
internal fun FinanceScreen(model: FinanceViewModel, onOpenUrl: (String) -> Unit) {
  val state by model.state.collectAsState()
  var selectedYear by remember { mutableStateOf<String?>(null) }
  var selectedMonth by remember { mutableStateOf<String?>(null) }
  var selected by remember { mutableStateOf<FinanceIncome?>(null) }
  LaunchedEffect(Unit) { model.ensureLoaded() }
  val entries = state.snapshot?.entries.orEmpty()
  val years = entries.mapNotNull { it.year }.distinct().sortedDescending()
  val months =
      entries
          .filter { selectedYear == null || it.year == selectedYear }
          .mapNotNull { it.month }
          .distinct()
          .sortedBy { it.toIntOrNull() ?: 99 }
  val filtered =
      entries.filter {
        (selectedYear == null || it.year == selectedYear) &&
            (selectedMonth == null || it.month == selectedMonth)
      }
  Scaffold(
      containerColor = androidx.compose.ui.graphics.Color.Transparent,
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
  ) { padding ->
    UbaaPullToRefresh(state.loading, model::refresh, Modifier.padding(padding)) {
      LazyColumn(
          Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UbaaDropdownSelector(
                selectedText = selectedYear ?: "全部年份",
                options = listOf("全部年份") + years,
                onSelected = {
                  selectedYear = years.getOrNull(it - 1)
                  selectedMonth = null
                },
                modifier = Modifier.weight(1f),
            )
            UbaaDropdownSelector(
                selectedText = selectedMonth?.let { "${it}月" } ?: "全部月份",
                options = listOf("全部月份") + months.map { "${it}月" },
                onSelected = { selectedMonth = months.getOrNull(it - 1) },
                modifier = Modifier.weight(1f),
            )
          }
        }
        state.error?.let { message ->
          item {
            UbaaErrorState(message, modifier = Modifier.fillMaxWidth(), onRetry = model::refresh)
          }
        }
        if (state.loading && state.snapshot == null)
            item { UbaaLoadingState(modifier = Modifier.fillMaxWidth()) }
        if (state.snapshot != null) {
          item {
            Text(
                "${filtered.size} 条收入记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (filtered.isEmpty())
              item { UbaaEmptyState("该范围内没有收入记录", modifier = Modifier.fillMaxWidth()) }
          filtered
              .groupBy(::financeMonth)
              .toList()
              .sortedWith(
                  compareBy<Pair<String, List<FinanceIncome>>> { it.first == "未提供所属年月" }
                      .thenByDescending { it.first }
              )
              .forEach { (month, rows) ->
                item { UbaaSectionHeader(month) }
                items(rows.sortedByDescending { it.recordedDate.orEmpty() }, key = { it.id }) {
                    entry ->
                  UbaaSectionCard(
                      title = entry.project ?: "收入记录",
                      trailing = {
                        UbaaIconAction(
                            onClick = { selected = entry },
                            description = "查看收入详情",
                            icon = androidx.compose.material.icons.Icons.Default.Info,
                        )
                      },
                  ) {
                    entry.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        "实发 ${financeYuan(entry.paidCents)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "应发 ${financeYuan(entry.amountCents)} · 扣税 ${financeYuan(entry.taxCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entry.recordedDate?.let {
                      Text(
                          "录入 $it",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
              }
        }
        item {
          UbaaTextButton(onClick = { onOpenUrl("https://chaxun.icw.buaa.edu.cn/zl/tjtb") }) {
            Text("打开学校财务查询")
          }
        }
      }
    }
  }
  selected?.let { entry ->
    UbaaAlertDialog(
        onDismissRequest = { selected = null },
        title = { Text(entry.project ?: "收入详情") },
        confirmButton = {},
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.description?.let { Text(it) }
            Text("应发金额：${financeYuan(entry.amountCents)}")
            Text("扣税：${financeYuan(entry.taxCents)}")
            Text("实发金额：${financeYuan(entry.paidCents)}")
            entry.taxRate?.let { Text("税率：$it") }
            entry.recordedDate?.let { Text("录入日期：$it") }
            Text("所属年月：${financeMonth(entry)}")
          }
        },
    )
  }
}
