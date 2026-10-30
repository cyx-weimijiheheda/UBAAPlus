package cn.edu.ubaa.plus.ui.screens.libbook

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.platform.currentLocationPoint
import cn.edu.ubaa.plus.api.platform.hasLocationPermission
import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.model.dto.LibBookGeoPoint
import cn.edu.ubaa.plus.model.dto.LibBookQrScanResultDto
import cn.edu.ubaa.plus.model.dto.LibBookSeatAction
import cn.edu.ubaa.plus.model.dto.canCancelBooking
import cn.edu.ubaa.plus.model.dto.canCheckout
import cn.edu.ubaa.plus.model.dto.canSignIn
import cn.edu.ubaa.plus.model.dto.canTemporaryLeave
import cn.edu.ubaa.plus.model.dto.isInUseBooking
import cn.edu.ubaa.plus.model.dto.isTerminalBooking
import cn.edu.ubaa.plus.model.format.formatUbaaDateWithTimeRange
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaToast
import cn.edu.ubaa.plus.ui.common.components.UbaaToastEffect
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalTime::class)
@Composable
fun LibBookBookingsScreen(viewModel: LibBookViewModel) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var selectedTab by remember { mutableIntStateOf(0) }
  var showRules by remember { mutableStateOf(false) }
  var pendingCancel by remember { mutableStateOf<LibBookBookingDto?>(null) }
  var now by remember { mutableStateOf(currentLibBookNow()) }
  var pendingScanPoint by remember { mutableStateOf<LibBookGeoPoint?>(null) }
  var actionAwaitingLocation by remember { mutableStateOf<LibBookSeatAction?>(null) }
  var scanAwaitingLocation by remember { mutableStateOf(false) }
  var isLocating by remember { mutableStateOf(false) }
  val scanOptions = remember {
    ScanOptions()
        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        .setPrompt("将座位二维码放入框内")
        .setBeepEnabled(false)
        .setOrientationLocked(true)
  }
  val scanLauncher =
      rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> viewModel.handleSeatQrScan(raw, pendingScanPoint) }
      }

  fun fetchLocationThen(onPoint: (LibBookGeoPoint?) -> Unit) {
    if (isLocating) return
    coroutineScope.launch {
      isLocating = true
      val point = currentLocationPoint(context)
      isLocating = false
      if (point == null) UbaaToast.show("未获取到定位，操作可能被平台拒绝")
      onPoint(point)
    }
  }

  val locationPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
          grants ->
        val action = actionAwaitingLocation
        val wantsScan = scanAwaitingLocation
        actionAwaitingLocation = null
        scanAwaitingLocation = false
        val granted = grants.values.any { it } || hasLocationPermission(context)
        when {
          action != null ->
              if (granted) {
                fetchLocationThen { point -> viewModel.submitSeatAction(action, point) }
              } else {
                UbaaToast.show("未授予定位权限，操作可能被平台拒绝")
                viewModel.submitSeatAction(action, null)
              }
          wantsScan ->
              if (granted) {
                fetchLocationThen { point ->
                  pendingScanPoint = point
                  scanLauncher.launch(scanOptions)
                }
              } else {
                pendingScanPoint = null
                scanLauncher.launch(scanOptions)
              }
        }
      }

  fun submitWithLocation(action: LibBookSeatAction) {
    if (isLocating) return
    if (hasLocationPermission(context)) {
      fetchLocationThen { point -> viewModel.submitSeatAction(action, point) }
    } else {
      actionAwaitingLocation = action
      locationPermissionLauncher.launch(LIB_BOOK_LOCATION_PERMISSIONS)
    }
  }

  fun launchScan() {
    if (isLocating) return
    pendingScanPoint = null
    if (hasLocationPermission(context)) {
      fetchLocationThen { point ->
        pendingScanPoint = point
        scanLauncher.launch(scanOptions)
      }
    } else {
      scanAwaitingLocation = true
      locationPermissionLauncher.launch(LIB_BOOK_LOCATION_PERMISSIONS)
    }
  }

  LaunchedEffect(Unit) { viewModel.ensureBookingsLoaded() }
  LaunchedEffect(Unit) {
    while (true) {
      delay(30_000)
      now = currentLibBookNow()
    }
  }
  UbaaToastEffect(uiState.actionMessage) { viewModel.clearActionMessage() }
  UbaaToastEffect(uiState.bookingsError) {}

  val current = uiState.bookings.bookings.filter { !it.isTerminalBooking() }
  val history = uiState.bookings.bookings.filter { it.isTerminalBooking() }
  val shown = if (selectedTab == 0) current else history

  Scaffold(
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
  ) { paddingValues ->
    UbaaPullToRefresh(
        refreshing = uiState.isBookingsLoading,
        onRefresh = { viewModel.loadBookings() },
        modifier = Modifier.padding(paddingValues),
    ) {
      if (uiState.isBookingsLoading && uiState.bookings.bookings.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                  "预约记录",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showRules = true }) { Text("座位规则") }
                AssistChip(onClick = { launchScan() }, label = { Text("扫码操作") })
              }
            }
          }
          item {
            TabRow(selectedTabIndex = selectedTab) {
              Tab(
                  selected = selectedTab == 0,
                  onClick = { selectedTab = 0 },
                  text = { Text("当前预约（${current.size}）") },
              )
              Tab(
                  selected = selectedTab == 1,
                  onClick = { selectedTab = 1 },
                  text = { Text("历史记录（${history.size}）") },
              )
            }
          }
          if (uiState.bookingsError != null && uiState.bookings.bookings.isEmpty()) {
            item {
              LibBookErrorState(
                  message = uiState.bookingsError.orEmpty(),
                  onRetry = viewModel::loadBookings,
              )
            }
          } else if (shown.isEmpty()) {
            item {
              Text(
                  text = if (selectedTab == 0) "当前暂无图书馆座位预约" else "暂无历史预约记录",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            items(shown, key = { it.id.ifBlank { "${it.day}-${it.beginTime}-${it.seatNo}" } }) {
                booking ->
              LibBookBookingCard(
                  booking = booking,
                  isCanceling = uiState.cancelingBookingId == booking.id,
                  now = now,
                  onCancel = { pendingCancel = booking },
                  onSignIn = { launchScan() },
                  onTemporaryLeave = {
                    viewModel.requestSeatActionForBooking(booking, LibBookSeatAction.LEAVE)
                  },
                  onCheckout = {
                    viewModel.requestSeatActionForBooking(booking, LibBookSeatAction.CHECKOUT)
                  },
                  onScan = { launchScan() },
              )
            }
          }
        }
      }
    }
  }

  if (showRules) {
    AlertDialog(
        onDismissRequest = { showRules = false },
        title = { Text("图书馆座位使用规则") },
        text = {
          Column(
              modifier = Modifier.verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            LIB_BOOK_RULES.forEachIndexed { index, rule ->
              Text("${index + 1}. $rule", style = MaterialTheme.typography.bodyMedium)
            }
          }
        },
        confirmButton = { TextButton(onClick = { showRules = false }) { Text("知道了") } },
    )
  }

  pendingCancel?.let { booking ->
    AlertDialog(
        onDismissRequest = { pendingCancel = null },
        title = { Text("取消预约") },
        text = { Text("确认取消 ${booking.seatNo.ifBlank { booking.areaName }} 的预约？签到前每日只能取消 1 次。") },
        confirmButton = {
          TextButton(
              onClick = {
                pendingCancel = null
                viewModel.cancelBooking(booking.id)
              }
          ) {
            Text("确认取消")
          }
        },
        dismissButton = { TextButton(onClick = { pendingCancel = null }) { Text("再想想") } },
    )
  }

  uiState.seatActionContext?.let { context ->
    val pending = uiState.pendingSeatAction
    if (pending == LibBookSeatAction.LEAVE || pending == LibBookSeatAction.CHECKOUT) {
      val checkout = pending == LibBookSeatAction.CHECKOUT
      AlertDialog(
          onDismissRequest = viewModel::dismissSeatAction,
          title = { Text(if (checkout) "完全离开" else "临时离开") },
          text = {
            Text(
                if (checkout) {
                  "确认结束使用并释放座位？释放后需要重新预约。将获取并提交当前位置用于平台校验。"
                } else {
                  "确认临时离开？离馆时间过长会由系统自动签退并释放座位。将获取并提交当前位置用于平台校验。"
                }
            )
          },
          confirmButton = {
            TextButton(
                onClick = { submitWithLocation(pending) },
                enabled = !uiState.isSeatActionSubmitting && !isLocating,
            ) {
              Text(if (checkout) "确认离开" else "确认暂离")
            }
          },
          dismissButton = { TextButton(onClick = viewModel::dismissSeatAction) { Text("取消") } },
      )
    } else {
      SeatActionDialog(
          context = context,
          submitting = uiState.isSeatActionSubmitting || isLocating,
          onAction = { action ->
            if (action == LibBookSeatAction.SIGN_IN) {
              submitWithLocation(action)
            } else {
              viewModel.requestSeatAction(action)
            }
          },
          onDismiss = viewModel::dismissSeatAction,
      )
    }
  }
}

@Composable
private fun SeatActionDialog(
    context: LibBookQrScanResultDto,
    submitting: Boolean,
    onAction: (LibBookSeatAction) -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("座位操作") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
              listOf(context.areaName, context.seatNo)
                  .filter { it.isNotBlank() }
                  .joinToString(" / ")
                  .ifBlank { "已识别座位" },
              fontWeight = FontWeight.Bold,
          )
          if (context.beginTime.isNotBlank() || context.endTime.isNotBlank()) {
            Text(
                formatUbaaDateWithTimeRange(
                    context.day,
                    context.beginTime,
                    context.endTime,
                )
            )
          }
          Text(
              "状态：${context.statusName.ifBlank { context.status.ifBlank { "未知" } }}",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (context.canSignIn) {
              AssistChip(
                  onClick = { onAction(LibBookSeatAction.SIGN_IN) },
                  enabled = !submitting,
                  label = { Text("签到") },
              )
            }
            if (context.canTemporaryLeave) {
              AssistChip(
                  onClick = { onAction(LibBookSeatAction.LEAVE) },
                  enabled = !submitting,
                  label = { Text("临时离开") },
              )
            }
            if (context.canCheckout) {
              AssistChip(
                  onClick = { onAction(LibBookSeatAction.CHECKOUT) },
                  enabled = !submitting,
                  label = { Text("完全离开") },
              )
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
  )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalTime::class)
@Composable
private fun LibBookBookingCard(
    booking: LibBookBookingDto,
    isCanceling: Boolean,
    now: LocalDateTime,
    onCancel: () -> Unit,
    onSignIn: () -> Unit,
    onTemporaryLeave: () -> Unit,
    onCheckout: () -> Unit,
    onScan: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
              formatUbaaDateWithTimeRange(
                      booking.day,
                      booking.beginTime,
                      booking.endTime,
                  )
                  .ifBlank { "时间未知" },
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

      if (!booking.isTerminalBooking()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (booking.canCancelBooking()) {
            AssistChip(
                onClick = onCancel,
                enabled = !isCanceling,
                label = { Text(if (isCanceling) "取消中..." else "取消预约") },
            )
          }
          if (booking.canTemporaryLeave()) {
            AssistChip(onClick = onTemporaryLeave, label = { Text("临时离开") })
          }
          if (booking.canCheckout()) {
            AssistChip(onClick = onCheckout, label = { Text("完全离开") })
          }
          if (booking.canSignIn()) {
            AssistChip(onClick = onSignIn, label = { Text("扫码签到") })
          } else {
            AssistChip(onClick = onScan, label = { Text("扫码操作") })
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalTime::class)
private fun currentLibBookNow(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private val LIB_BOOK_LOCATION_PERMISSIONS =
    arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
