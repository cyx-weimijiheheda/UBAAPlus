package cn.edu.ubaa.plus.ui.screens.diagnostics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.diagnostics.*
import cn.edu.ubaa.plus.api.storage.LocalDiagnostics
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.ui.common.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NetworkDiagnosticsRoute(mode: ConnectionMode, authenticated: Boolean) {
  val model: NetworkDiagnosticsViewModel =
      viewModel(key = "network-diagnostics-${ScheduleStore.account()}-$mode-$authenticated") {
        NetworkDiagnosticsViewModel()
      }
  val state by model.state.collectAsState()
  LaunchedEffect(model, mode) { model.initialize(mode) }
  DisposableEffect(model) { onDispose { model.stop() } }
  NetworkDiagnosticsScreen(
      state,
      model.targets,
      mode,
      authenticated,
      { model.start(mode, authenticated, it) },
      model::stop,
  )
}

@Composable
private fun NetworkDiagnosticsScreen(
    state: NetworkDiagnosticsState,
    targets: List<DiagnosticTarget>,
    mode: ConnectionMode,
    authenticated: Boolean,
    onStart: (String?) -> Unit,
    onStop: () -> Unit,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  var filter by remember { mutableIntStateOf(0) }
  var issueCount by remember { mutableIntStateOf(LocalDiagnostics.issues().size) }
  var clearIssues by remember { mutableStateOf(false) }
  LaunchedEffect(state.running) { if (!state.running) issueCount = LocalDiagnostics.issues().size }
  val report = { exportNetworkDiagnostics(state.rows) }
  val export =
      rememberLauncherForActivityResult(
          ActivityResultContracts.CreateDocument("application/json")
      ) { uri ->
        if (uri != null) {
          val snapshot = report()
          scope.launch {
            val ok =
                withContext(Dispatchers.IO) {
                  runCatching {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                          it.write(snapshot)
                        } ?: error("Unable to open export")
                      }
                      .isSuccess
                }
            UbaaToast.show(if (ok) "诊断报告已导出" else "导出失败，请重试")
          }
        }
      }
  if (clearIssues)
      UbaaAlertDialog(
          onDismissRequest = { clearIssues = false },
          title = { Text("清除问题记录？") },
          text = { Text("仅清除已收集的脱敏错误记录，当前网络检测结果保留。") },
          confirmButton = {
            UbaaConfirmAction(
                onClick = {
                  LocalDiagnostics.clear()
                  issueCount = 0
                  clearIssues = false
                },
                description = "清除问题记录",
            )
          },
      )
  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      UbaaSectionCard(
          title = "检测概况",
          trailing = {
            UbaaIconAction(
                onClick = {
                  clipboard.setText(AnnotatedString(report()))
                  UbaaToast.show("脱敏诊断已复制")
                },
                description = "复制脱敏报告",
                icon = Icons.Default.ContentCopy,
                enabled = !state.running,
            )
            UbaaIconAction(
                onClick = { export.launch("UBAAPlus-network-diagnostics.json") },
                description = "导出脱敏报告",
                icon = Icons.Default.FileDownload,
                enabled = !state.running,
            )
          },
      ) {
        Text("当前连接：${mode.displayName}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "先检查服务响应，再抽样验证主要业务接口，不使用离线回退。服务已响应不代表业务已登录；部分功能始终直连。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!authenticated)
            Text(
                "当前未登录，仅检测服务响应。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              if (state.running) "已完成 ${state.completedIds.size} / ${state.activeIds.size}"
              else "${state.rows.count{it.isProblem()}} 项需关注 · ${targets.size} 个项目",
              style = MaterialTheme.typography.bodyMedium,
          )
          if (state.running)
              UbaaIconAction(onClick = onStop, description = "停止检测", icon = Icons.Default.Stop)
          else UbaaTextButton(onClick = { onStart(null) }) { Text("检测全部") }
        }
        if (state.running)
            LinearProgressIndicator(
                progress = {
                  state.completedIds.size.toFloat() / state.activeIds.size.coerceAtLeast(1)
                },
                modifier = Modifier.fillMaxWidth(),
            )
      }
    }
    item {
      UbaaSegmentedTabs(
          labels = listOf("全部", "需关注", "未检测"),
          selectedIndex = filter,
          onSelected = { filter = it },
      )
    }
    for ((title, group) in listOf("基础连接" to targets.take(2), "功能页面" to targets.drop(2))) {
      val rows =
          state.rows.filter { row ->
            group.any { it.id == row.id } &&
                when (filter) {
                  1 -> row.isProblem()
                  2 ->
                      row.network.status == ProbeStatus.IDLE ||
                          row.business.status == ProbeStatus.SKIPPED
                  else -> true
                }
          }
      if (rows.isNotEmpty())
          item {
            UbaaSectionCard(title = title) {
              rows.forEachIndexed { index, row ->
                DiagnosticResultRow(row, state.running) { onStart(row.id) }
                if (index < rows.lastIndex)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
              }
            }
          }
    }
    if (
        filter != 0 &&
            state.rows.none {
              when (filter) {
                1 -> it.isProblem()
                else ->
                    it.network.status == ProbeStatus.IDLE ||
                        it.business.status == ProbeStatus.SKIPPED
              }
            }
    )
        item { UbaaEmptyState("没有符合条件的项目", modifier = Modifier.fillMaxWidth()) }
    item {
      UbaaSectionCard(
          title = "问题记录",
          trailing = {
            UbaaIconAction(
                onClick = { clearIssues = true },
                description = "清除问题记录",
                icon = Icons.Default.DeleteOutline,
                enabled = issueCount > 0,
            )
          },
      ) {
        Text("本机已记录 $issueCount 条脱敏错误，导出报告时一并包含。", style = MaterialTheme.typography.bodyMedium)
        Text(
            "报告只包含功能、连接方式、状态、耗时及错误分类，不含账号、密码、余额、学校正文或登录凭据。预约、签到、支付等提交操作不参与检测。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun DiagnosticResultRow(row: DiagnosticRow, running: Boolean, onStart: () -> Unit) {
  Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(row.title, style = MaterialTheme.typography.titleMedium)
        Text(
            if (row.route == "webvpn") "经 WebVPN" else "直连",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      UbaaIconAction(
          onClick = onStart,
          description = "检测${row.title}",
          icon = Icons.Default.Refresh,
          enabled = !running,
      )
    }
    ProbeResultLine("服务响应", row.network)
    ProbeResultLine("业务读取", row.business)
  }
}

@Composable
private fun ProbeResultLine(label: String, result: ProbeResult) {
  Row(
      Modifier.fillMaxWidth().padding(vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (result.status == ProbeStatus.RUNNING)
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
    else
        UbaaStatusBadge(
            result.status.label,
            tone =
                when (result.status) {
                  ProbeStatus.SUCCESS -> UbaaStatusTone.SUCCESS
                  ProbeStatus.FAILED,
                  ProbeStatus.TIMEOUT -> UbaaStatusTone.ERROR
                  ProbeStatus.AUTH_REQUIRED -> UbaaStatusTone.WARNING
                  else -> UbaaStatusTone.NEUTRAL
                },
        )
    Text(
        buildString {
          result.httpStatus?.let { append("HTTP $it ") }
          if (result.status != ProbeStatus.IDLE && result.status != ProbeStatus.RUNNING)
              append(result.reason?.text.orEmpty())
          result.elapsedMs?.let {
            append(" · ")
            append(String.format(java.util.Locale.ROOT, "%.1f秒", it / 1000.0))
          }
        },
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
