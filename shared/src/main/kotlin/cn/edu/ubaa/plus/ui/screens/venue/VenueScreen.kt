package cn.edu.ubaa.plus.ui.screens.venue

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.ubaa.plus.api.venue.VenueDayInfo
import cn.edu.ubaa.plus.api.venue.VenueGrabPhase
import cn.edu.ubaa.plus.api.venue.VenueSpace
import cn.edu.ubaa.plus.api.venue.VenueSpaceMode
import cn.edu.ubaa.plus.api.venue.VenueSpaceSlot
import cn.edu.ubaa.plus.api.venue.VenueTimeSlot
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

/** 运动场地页面：登录、选择场地与日期、查看可用性、配置抢场地参数并启动任务。 */
@Composable
fun VenueScreen(viewModel: VenueViewModel) {
  val state by viewModel.state.collectAsState()
  val grabState by viewModel.grabState.collectAsState()
  val foreground by cn.edu.ubaa.plus.AppForegroundMonitor.foreground.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  var showBuddyDialog by remember { mutableStateOf(false) }

  val grabbing =
      grabState.phase == VenueGrabPhase.WAITING ||
          grabState.phase == VenueGrabPhase.RUNNING ||
          grabState.phase == VenueGrabPhase.NEEDS_CAPTCHA

  LaunchedEffect(state.message) {
    state.message?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearMessage()
    }
  }

  LaunchedEffect(
      state.authPhase,
      state.selectedSiteId,
      state.selectedDate,
      grabbing,
      foreground,
  ) {
    if (state.authPhase == VenueAuthPhase.READY && !grabbing && foreground) {
      while (true) {
        delay(30_000)
        viewModel.refreshDayInfo()
      }
    }
  }

  Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { padding ->
    when (state.authPhase) {
      VenueAuthPhase.CHECKING ->
          Box(
              modifier = Modifier.fillMaxSize().padding(padding),
              contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator()
              Spacer(modifier = Modifier.height(8.dp))
              Text("正在建立运动场地会话…")
            }
          }
      VenueAuthPhase.NEEDS_LOGIN,
      VenueAuthPhase.NEEDS_CAPTCHA ->
          VenueLoginCard(
              state = state,
              onUsernameChange = { viewModel.updateLoginForm(username = it) },
              onPasswordChange = { viewModel.updateLoginForm(password = it) },
              onCaptchaChange = { viewModel.updateLoginForm(captcha = it) },
              onRememberChange = { viewModel.updateLoginForm(remember = it) },
              onLogin = { viewModel.login() },
              modifier = Modifier.padding(padding),
          )
      VenueAuthPhase.FAILED ->
          Box(
              modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
              contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                  text = state.authMessage ?: "运动场地初始化失败",
                  color = MaterialTheme.colorScheme.error,
                  textAlign = TextAlign.Center,
              )
              Spacer(modifier = Modifier.height(12.dp))
              Button(
                  onClick = {
                    viewModel.resetLoadedState()
                    viewModel.ensureInitialized()
                  }
              ) {
                Text("重试")
              }
            }
          }
      VenueAuthPhase.READY ->
          LazyColumn(
              modifier = Modifier.fillMaxSize().padding(padding),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            item {
              VenueSiteDateCard(
                  state = state,
                  onSiteSelected = viewModel::selectSite,
                  onDateSelected = viewModel::selectDate,
                  onRefresh = viewModel::refreshDayInfo,
              )
            }
            item {
              VenueAvailabilityCard(
                  state = state,
                  onCellClick = viewModel::selectCell,
                  onTemplateSlotClick = viewModel::selectTemplateSlot,
              )
            }
            item {
              VenueTargetOptionsCard(
                  state = state,
                  onDurationChange = viewModel::setDurationHours,
                  onSpaceModeChange = viewModel::setSpaceMode,
                  onRandomChange = viewModel::setRandomSpace,
                  onFallbackChange = viewModel::setFallbackAnySpace,
              )
            }
            item {
              VenueBuddiesCard(
                  state = state,
                  onToggleBuddy = viewModel::toggleBuddy,
                  onManageClick = { showBuddyDialog = true },
              )
            }
            item {
              VenueGrabPanel(
                  state = state,
                  grabState = grabState,
                  onUseScheduleChange = viewModel::setUseSchedule,
                  onGrabAtChange = viewModel::updateGrabAt,
                  onPollIntervalChange = viewModel::updatePollInterval,
                  onStart = viewModel::startGrab,
                  onStop = viewModel::stopGrab,
              )
            }
          }
    }
  }

  grabState.pendingCaptcha?.let { challenge ->
    VenueCaptchaDialog(
        challenge = challenge,
        onCancel = viewModel::cancelCaptcha,
        onSubmit = viewModel::submitCaptcha,
    )
  }

  if (showBuddyDialog) {
    VenueBuddyDialog(
        buddies = state.buddies,
        selectedIds = state.selectedBuddyIds,
        limit = state.buddyLimit,
        onAdd = viewModel::addBuddy,
        onDelete = viewModel::deleteBuddy,
        onDismiss = { showBuddyDialog = false },
    )
  }
}

@Composable
private fun VenueLoginCard(
    state: VenueUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCaptchaChange: (String) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val form = state.loginForm
  Column(
      modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        text = "运动场地登录",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "运动场地系统需要统一身份认证。登录信息只用于建立直连 SSO 会话；勾选“记住密码”后会使用加密存储保存。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = form.username,
        onValueChange = onUsernameChange,
        label = { Text("学号") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.password,
        onValueChange = onPasswordChange,
        label = { Text("密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.authPhase == VenueAuthPhase.NEEDS_CAPTCHA) {
      decodeVenueImage(form.captchaImage)?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "统一认证验证码",
            modifier = Modifier.fillMaxWidth().height(90.dp),
        )
      }
      OutlinedTextField(
          value = form.captcha,
          onValueChange = onCaptchaChange,
          label = { Text("验证码") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
      )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(checked = form.rememberPassword, onCheckedChange = onRememberChange)
      Text("记住密码", style = MaterialTheme.typography.bodyMedium)
    }

    (form.error ?: state.authMessage)?.let {
      Text(text = it, color = MaterialTheme.colorScheme.error)
    }

    Button(
        onClick = onLogin,
        enabled = !state.isLoggingIn,
        modifier = Modifier.fillMaxWidth(),
    ) {
      if (state.isLoggingIn) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
      } else {
        Text("登录")
      }
    }
  }
}

@Composable
private fun VenueSiteDateCard(
    state: VenueUiState,
    onSiteSelected: (Int) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "场地与日期",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
      }

      var expanded by remember { mutableStateOf(false) }
      Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
          Text(
              text = state.selectedSite?.let { "${it.displayName}${it.openRange}" } ?: "选择场地",
              maxLines = 1,
          )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
          state.sites.forEach { site ->
            DropdownMenuItem(
                text = { Text("${site.displayName}${site.openRange}") },
                onClick = {
                  expanded = false
                  onSiteSelected(site.id)
                },
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        state.availableDates.forEach { date ->
          val selected = date.toString() == state.selectedDate
          FilterChip(
              selected = selected,
              onClick = { onDateSelected(date) },
              label = { Text(date.toString().substring(5)) },
          )
        }
      }

      state.dayInfoMessage?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (state.isLoadingDayInfo) {
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
    }
  }
}

@Composable
private fun VenueAvailabilityCard(
    state: VenueUiState,
    onCellClick: (VenueSpace, VenueTimeSlot) -> Unit,
    onTemplateSlotClick: (VenueTimeSlot) -> Unit,
) {
  val day = state.dayInfo
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = "可用时段",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(8.dp))

      if (day == null) {
        Text(
            text = "该日期暂无场地数据。尚未放票时也可以直接设置时间并开始抢场地。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "可选时段（点击选择目标开始时间）：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          state.slotTemplate.forEach { slot ->
            FilterChip(
                selected = state.selectedStartBegin == slot.beginTime,
                onClick = { onTemplateSlotClick(slot) },
                label = { Text(slot.label) },
            )
          }
        }
      } else {
        AvailabilityTable(day = day, state = state, onCellClick = onCellClick)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "绿色=空闲，橙色=未支付，红色=已预约，灰色=不可用。点击空闲格子设置目标时段。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun AvailabilityTable(
    day: VenueDayInfo,
    state: VenueUiState,
    onCellClick: (VenueSpace, VenueTimeSlot) -> Unit,
) {
  Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
      Box(modifier = Modifier.width(80.dp)) {
        Text(
            text = "时段",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
      }
      day.spaces.forEach { space ->
        Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
          Text(
              text = space.spaceName.ifBlank { "${space.spaceId}号" },
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
          )
        }
      }
    }

    day.slots.forEach { slot ->
      Row(
          modifier = Modifier.padding(vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.width(80.dp)) {
          Text(text = slot.label, style = MaterialTheme.typography.labelMedium)
        }
        day.spaces.forEach { space ->
          val cell = space.slot(slot.timeId)
          val selected =
              state.selectedStartBegin == slot.beginTime &&
                  (state.spaceMode == VenueSpaceMode.AUTO || state.selectedSpaceId == space.spaceId)
          VenueSlotCell(
              cell = cell,
              selected = selected,
              onClick = { onCellClick(space, slot) },
          )
        }
      }
    }
  }
}

@Composable
private fun VenueSlotCell(
    cell: VenueSpaceSlot?,
    selected: Boolean,
    onClick: () -> Unit,
) {
  val isFree = cell?.isFree == true
  val container =
      when {
        selected -> MaterialTheme.colorScheme.primary
        cell == null -> MaterialTheme.colorScheme.surfaceVariant
        cell.status == VenueSpaceSlot.STATUS_FREE -> MaterialTheme.colorScheme.primaryContainer
        cell.status == VenueSpaceSlot.STATUS_UNPAID -> MaterialTheme.colorScheme.tertiaryContainer
        cell.status == VenueSpaceSlot.STATUS_RESERVED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
      }
  val content =
      if (selected) {
        MaterialTheme.colorScheme.onPrimary
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      }
  val label =
      when {
        cell == null -> "—"
        cell.status == VenueSpaceSlot.STATUS_FREE -> cell.fee?.let { "¥${it.toInt()}" } ?: "空闲"
        cell.status == VenueSpaceSlot.STATUS_UNPAID -> "未支付"
        cell.status == VenueSpaceSlot.STATUS_RESERVED -> "已预约"
        else -> "停用"
      }

  Surface(
      onClick = onClick,
      enabled = isFree,
      modifier = Modifier.width(68.dp).height(40.dp).padding(horizontal = 2.dp),
      shape = RoundedCornerShape(6.dp),
      color = container,
      contentColor = content,
  ) {
    Box(contentAlignment = Alignment.Center) { Text(text = label, fontSize = 11.sp) }
  }
}

@Composable
private fun VenueTargetOptionsCard(
    state: VenueUiState,
    onDurationChange: (Int) -> Unit,
    onSpaceModeChange: (VenueSpaceMode) -> Unit,
    onRandomChange: (Boolean) -> Unit,
    onFallbackChange: (Boolean) -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = "抢场地设置",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(8.dp))

      Text("时长", style = MaterialTheme.typography.labelMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.durationHours == 1,
            onClick = { onDurationChange(1) },
            label = { Text("1 小时") },
        )
        FilterChip(
            selected = state.durationHours == 2,
            onClick = { onDurationChange(2) },
            label = { Text("2 小时") },
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      Text("场地模式", style = MaterialTheme.typography.labelMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.spaceMode == VenueSpaceMode.FIXED,
            onClick = { onSpaceModeChange(VenueSpaceMode.FIXED) },
            label = { Text("指定场地") },
        )
        FilterChip(
            selected = state.spaceMode == VenueSpaceMode.AUTO,
            onClick = { onSpaceModeChange(VenueSpaceMode.AUTO) },
            label = { Text("自动选场地") },
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.randomSpace,
            onCheckedChange = onRandomChange,
            enabled = state.spaceMode == VenueSpaceMode.AUTO,
        )
        Text("自动时随机选择场地", style = MaterialTheme.typography.bodySmall)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.fallbackAnySpace,
            onCheckedChange = onFallbackChange,
        )
        Text("指定场地不足时换其它空闲场地", style = MaterialTheme.typography.bodySmall)
      }

      Spacer(modifier = Modifier.height(8.dp))
      val start = state.selectedStartBegin
      val spaceName =
          state.dayInfo?.spaces?.firstOrNull { it.spaceId == state.selectedSpaceId }?.spaceName
      Text(
          text =
              if (start == null) {
                "尚未选择目标时段"
              } else {
                buildString {
                  append("目标：")
                  append(start)
                  append(" · ")
                  append(state.durationHours)
                  append(" 小时")
                  if (state.spaceMode == VenueSpaceMode.FIXED && !spaceName.isNullOrBlank()) {
                    append(" · ")
                    append(spaceName)
                  }
                }
              },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun VenueBuddiesCard(
    state: VenueUiState,
    onToggleBuddy: (Int) -> Unit,
    onManageClick: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "同伴",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onManageClick) { Text("管理") }
      }
      Text(
          text =
              "已选 ${state.selectedBuddyIds.size}/${state.buddyLimit} 位（服务端上限 ${state.buddyLimit}）",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))
      if (state.buddies.isEmpty()) {
        Text(
            text = "还没有同伴，点击“管理”按学工号添加。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          state.buddies.forEach { buddy ->
            FilterChip(
                selected = buddy.id in state.selectedBuddyIds,
                onClick = { onToggleBuddy(buddy.id) },
                label = { Text(buddy.name.ifBlank { buddy.userUid }) },
            )
          }
        }
      }
    }
  }
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
internal fun decodeVenueImage(data: String?): android.graphics.Bitmap? {
  if (data.isNullOrBlank()) return null
  return runCatching {
        val encoded = data.substringAfter("base64,", data)
        val bytes = kotlin.io.encoding.Base64.decode(encoded)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      }
      .getOrNull()
}
