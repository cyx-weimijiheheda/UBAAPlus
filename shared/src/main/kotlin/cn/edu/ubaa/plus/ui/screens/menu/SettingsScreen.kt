package cn.edu.ubaa.plus.ui.screens.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.feature.DormElectricityApi
import cn.edu.ubaa.plus.api.storage.AppPreferencesStore
import cn.edu.ubaa.plus.api.storage.FeatureLayout
import cn.edu.ubaa.plus.api.storage.ThemeMode
import cn.edu.ubaa.plus.api.storage.WidgetColorMode
import cn.edu.ubaa.plus.model.dto.DormRoomDto
import cn.edu.ubaa.plus.reminder.ReminderServiceController
import cn.edu.ubaa.plus.ui.common.components.CustomBrandColorDialog
import cn.edu.ubaa.plus.ui.common.components.ThemePalettePicker
import cn.edu.ubaa.plus.ui.common.components.UbaaAlertDialog
import cn.edu.ubaa.plus.ui.common.components.UbaaConfirmAction
import cn.edu.ubaa.plus.ui.common.components.UbaaDropdownSelector
import cn.edu.ubaa.plus.ui.common.components.UbaaIconAction
import cn.edu.ubaa.plus.ui.common.components.UbaaSectionCard
import cn.edu.ubaa.plus.ui.common.components.UbaaSegmentedTabs
import cn.edu.ubaa.plus.ui.common.components.UbaaTextButton
import cn.edu.ubaa.plus.ui.common.components.UbaaTextField
import cn.edu.ubaa.plus.ui.common.components.UbaaToggleRow
import kotlinx.coroutines.launch

private sealed interface DormRoomsState {
  data object Loading : DormRoomsState

  data class Ready(val rooms: List<DormRoomDto>) : DormRoomsState

  data class Error(val message: String) : DormRoomsState
}

@Composable
fun SettingsScreen(
    currentMode: ConnectionMode,
    availableModes: List<ConnectionMode>,
    onModeSelected: (ConnectionMode) -> Unit,
    onFeatureOrderClick: () -> Unit,
    onNetworkDiagnosticsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    allowSchoolReads: Boolean = true,
    onLoginRequired: () -> Unit = {},
    onBeforeClearCache: () -> Unit = {},
) {
  val diagnosticCacheContext = androidx.compose.ui.platform.LocalContext.current
  val cacheScope = androidx.compose.runtime.rememberCoroutineScope()
  var showMaterial by remember { mutableStateOf(false) }
  var showDormDefaults by remember { mutableStateOf(false) }
  var showClearCache by remember { mutableStateOf(false) }
  var clearingCache by remember { mutableStateOf(false) }
  var cacheBytes by remember { mutableStateOf<Long?>(null) }
  var cacheSizeFailed by remember { mutableStateOf(false) }
  val cacheRevision by cn.edu.ubaa.plus.api.storage.CacheMaintenance.changes.collectAsState()
  LaunchedEffect(cacheRevision) {
    val result = runCatching { cn.edu.ubaa.plus.api.storage.CacheMaintenance.cachedSizeBytes() }
    cacheBytes = result.getOrNull()
    cacheSizeFailed = result.isFailure
  }
  val cacheSizeLabel =
      cacheBytes?.let { bytes ->
        when {
          bytes == 0L -> "0.00 MB"
          bytes < 10_486 -> "小于 0.01 MB"
          else -> String.format(java.util.Locale.CHINA, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
      } ?: if (cacheSizeFailed) "暂时无法计算" else "计算中…"

  if (showClearCache) {
    UbaaAlertDialog(
        dismissEnabled = !clearingCache,
        onDismissRequest = { if (!clearingCache) showClearCache = false },
        title = { Text("清理缓存") },
        text = {
          Text(
              "可清理缓存约 $cacheSizeLabel。清除首页、成绩、考试、作业和余额等临时缓存。登录信息、设置、已本地化课表和待确认操作记录会保留；再次访问功能时重新加载。"
          )
        },
        confirmButton = {
          UbaaConfirmAction(
              enabled = !clearingCache,
              onClick = {
                onBeforeClearCache()
                clearingCache = true
                cacheScope.launch {
                  try {
                    cn.edu.ubaa.plus.api.storage.CacheMaintenance.clear()
                    cn.edu.ubaa.plus.widget.ScheduleWidgetProvider.requestRefresh(
                        diagnosticCacheContext
                    )
                    cn.edu.ubaa.plus.ui.common.components.UbaaToast.show("缓存已清理")
                    showClearCache = false
                  } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                  } catch (_: Exception) {
                    cn.edu.ubaa.plus.ui.common.components.UbaaToast.show("缓存清理失败，请重试")
                  } finally {
                    clearingCache = false
                  }
                }
              },
              description = if (clearingCache) "清理中…" else "清理",
          )
        },
    )
  }
  var showMysteryDisclaimer by remember { mutableStateOf(false) }
  var pendingMode by remember { mutableStateOf<ConnectionMode?>(null) }
  val preferences by AppPreferencesStore.state.collectAsState()
  var showWidgetColorPicker by remember { mutableStateOf(false) }
  if (showMaterial)
      GlassMaterialDialog(
          initial = preferences.glassMaterial,
          onDismiss = { showMaterial = false },
          onApply = {
            AppPreferencesStore.setGlassMaterial(it)
            showMaterial = false
          },
      )
  if (showWidgetColorPicker)
      CustomBrandColorDialog(
          initialArgb = preferences.widgetCustomArgb,
          onDismiss = { showWidgetColorPicker = false },
          onApply = {
            AppPreferencesStore.setWidgetAppearance(argb = it)
            showWidgetColorPicker = false
          },
      )
  if (showMysteryDisclaimer) {
    UbaaAlertDialog(
        onDismissRequest = { showMysteryDisclaimer = false },
        title = { Text("免责声明") },
        text = {
          Text(
              "开启后显示博雅抢课与场地抢单控件。自动任务仅在应用前台运行，不保证成功；请求可能受学校规则、名额和网络状况影响。请遵守学校规定，确认课程时间、预约费用及取消规则。结果未确认时请先查询记录，避免重复操作。关闭开关会停止正在运行的自动任务。"
          )
        },
        confirmButton = {
          UbaaConfirmAction(
              onClick = {
                showMysteryDisclaimer = false
                AppPreferencesStore.setMysterySwitchEnabled(true)
              },
              description = "我已了解，开启",
          )
        },
        dismissButton = {
          UbaaIconAction(
              onClick = { showMysteryDisclaimer = false },
              description = "取消",
              icon = Icons.Default.Close,
          )
        },
    )
  }
  var dormState by
      remember(allowSchoolReads) {
        mutableStateOf<DormRoomsState>(
            if (allowSchoolReads) DormRoomsState.Loading else DormRoomsState.Error("登录后可更新宿舍信息")
        )
      }
  val dormApi = remember { DormElectricityApi() }

  fun loadDormRooms() {
    if (!allowSchoolReads) {
      onLoginRequired()
      return
    }
    dormState = DormRoomsState.Loading
  }

  LaunchedEffect(dormState, showDormDefaults) {
    if (showDormDefaults && allowSchoolReads && dormState is DormRoomsState.Loading) {
      dormApi
          .getRooms()
          .onSuccess { dormState = DormRoomsState.Ready(it) }
          .onFailure { dormState = DormRoomsState.Error(it.message ?: "宿舍列表加载失败") }
    }
  }

  LazyColumn(
      modifier = modifier.fillMaxSize(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SettingsSection(title = "外观") {
        Text("显示模式", style = MaterialTheme.typography.bodyLarge)
        UbaaSegmentedTabs(
            labels = ThemeMode.entries.map { it.displayName },
            selectedIndex = preferences.themeMode.ordinal,
            onSelected = { AppPreferencesStore.setThemeMode(ThemeMode.entries[it]) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        ThemePalettePicker(
            selected = preferences.themePalette,
            followWallpaper = preferences.followWallpaper,
            customBrandArgb = preferences.customBrandArgb,
            onPaletteSelected = AppPreferencesStore::setThemePalette,
            onCustomColorSelected = AppPreferencesStore::setCustomBrandColor,
            onFollowWallpaperChange = AppPreferencesStore::setFollowWallpaper,
        )
        Spacer(modifier = Modifier.height(12.dp))
        UbaaToggleRow(
            label = "玻璃与磨砂效果",
            checked = preferences.glassEnabled,
            onCheckedChange = AppPreferencesStore::setGlassEnabled,
            supportingText = "底部导航使用透亮玻璃，内容卡片使用柔和磨砂",
        )

        SettingsOptionRow(
            "磨砂材质",
            "对比度、饱和度、亮度与透明度",
            enabled = preferences.glassEnabled,
            onClick = { showMaterial = true },
        )
      }
    }
    item {
      SettingsSection(title = "连接与诊断") {
        Text(
            text = "当前模式：${currentMode.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        UbaaSegmentedTabs(
            labels = availableModes.map { it.displayName },
            selectedIndex = availableModes.indexOf(currentMode),
            onSelected = { index ->
              availableModes[index].takeIf { it != currentMode }?.let { pendingMode = it }
            },
        )
        Text(
            currentMode.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsOptionRow("网络诊断", "逐项检查全部功能，查看并导出脱敏结果", onClick = onNetworkDiagnosticsClick)
      }
    }
    item {
      SettingsSection(title = "功能与提醒") {
        Text("功能页布局", style = MaterialTheme.typography.bodyLarge)
        UbaaSegmentedTabs(
            labels = FeatureLayout.entries.map { it.displayName },
            selectedIndex = preferences.featureLayout.ordinal,
            onSelected = { AppPreferencesStore.setFeatureLayout(FeatureLayout.entries[it]) },
        )

        SettingsOptionRow("功能排序与显示", "调整入口顺序及显示状态", onClick = onFeatureOrderClick)

        val context = LocalContext.current
        UbaaToggleRow(
            label = "后台提醒",
            checked = preferences.backgroundRemindersEnabled,
            onCheckedChange = AppPreferencesStore::setBackgroundRemindersEnabled,
            supportingText = "仅在课程开课、签到/签退开放、新作业发布或作业将截止时提醒。关闭后首页课程、考勤和座位仍照常显示。",
        )
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
                !ReminderServiceController.notificationsAllowed(context)
        ) {
          Text(
              text = "通知权限未开启，后台提醒不会显示。请在系统设置中允许 UBAAPlus 发送通知。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
          )
        }
        SettingsOptionRow(
            "寝室用电默认值",
            listOf(preferences.dormCampus, preferences.dormBuilding, preferences.dormRoom)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "尚未设置" },
            expanded = showDormDefaults,
            onClick = { showDormDefaults = !showDormDefaults },
        )
        if (showDormDefaults) {
          Text(
              text = "宿舍列表与用电查询需要连接校园网；校外访问时服务可能返回 302 跳转。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = 8.dp),
          )
          when (val state = dormState) {
            DormRoomsState.Loading -> Text("正在加载宿舍列表…")
            is DormRoomsState.Error -> {
              Text(
                  text = state.message,
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall,
              )
              UbaaTextButton(onClick = ::loadDormRooms) { Text("重试") }
              Text(
                  text = "也可以手动填写，查询页会直接使用这些值。",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              UbaaTextField(
                  shape = RoundedCornerShape(14.dp),
                  value = preferences.dormCampus,
                  onValueChange = {
                    AppPreferencesStore.setDormDefaults(
                        it,
                        preferences.dormBuilding,
                        preferences.dormRoom,
                    )
                  },
                  label = { Text("校区") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
              )
              UbaaTextField(
                  shape = RoundedCornerShape(14.dp),
                  value = preferences.dormBuilding,
                  onValueChange = {
                    AppPreferencesStore.setDormDefaults(
                        preferences.dormCampus,
                        it,
                        preferences.dormRoom,
                    )
                  },
                  label = { Text("宿舍楼") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
              )
              UbaaTextField(
                  shape = RoundedCornerShape(14.dp),
                  value = preferences.dormRoom,
                  onValueChange = {
                    AppPreferencesStore.setDormDefaults(
                        preferences.dormCampus,
                        preferences.dormBuilding,
                        it,
                    )
                  },
                  label = { Text("宿舍号") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
              )
            }
            is DormRoomsState.Ready -> {
              DormDefaultsPicker(
                  rooms = state.rooms,
                  preferences = preferences,
              )
            }
          }
        }
      }
    }
    item {
      SettingsSection(title = "桌面小组件") {
        Text("显示模式", style = MaterialTheme.typography.bodyLarge)
        UbaaSegmentedTabs(
            labels = ThemeMode.entries.map { it.displayName },
            selectedIndex = preferences.widgetThemeMode.ordinal,
            onSelected = { AppPreferencesStore.setWidgetAppearance(mode = ThemeMode.entries[it]) },
        )
        UbaaDropdownSelector(
            selectedText = preferences.widgetColorMode.displayName,
            options = WidgetColorMode.entries.map { it.displayName },
            onSelected = {
              AppPreferencesStore.setWidgetAppearance(color = WidgetColorMode.entries[it])
            },
            label = "配色",
        )
        if (preferences.widgetColorMode == WidgetColorMode.CUSTOM) {
          UbaaTextButton(onClick = { showWidgetColorPicker = true }) { Text("设置小组件颜色") }
        }
        Text(
            "今日课程小组件同时显示待办，也可单独添加待办小组件。待办来自应用同步快照；打开首页可更新。Android 12 以下壁纸配色回退到应用配色。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    item {
      SettingsSection(title = "数据与存储") {
        SettingsOptionRow(
            "清理缓存",
            if (clearingCache) "清理中…" else "$cacheSizeLabel · 保留登录信息、设置与本地课表",
            enabled = !clearingCache,
            onClick = { showClearCache = true },
        )
      }
    }
    item {
      SettingsSection(title = "其他选项") {
        UbaaToggleRow(
            label = "？！！？",
            checked = preferences.mysterySwitchEnabled,
            onCheckedChange = { enabled ->
              if (enabled) showMysteryDisclaimer = true
              else AppPreferencesStore.setMysterySwitchEnabled(false)
            },
        )
      }
    }
  }

  pendingMode?.let { mode ->
    UbaaAlertDialog(
        onDismissRequest = { pendingMode = null },
        title = { Text("切换连接模式") },
        text = { Text("将退出当前登录并清除本模式会话，需重新登录。") },
        confirmButton = {
          UbaaConfirmAction(
              onClick = {
                pendingMode = null
                onModeSelected(mode)
              },
              description = "确认切换",
          )
        },
        dismissButton = {
          UbaaIconAction(
              onClick = { pendingMode = null },
              description = "取消",
              icon = Icons.Default.Close,
          )
        },
    )
  }
}

@Composable
private fun DormDefaultsPicker(
    rooms: List<DormRoomDto>,
    preferences: cn.edu.ubaa.plus.api.storage.AppPreferences,
) {
  val campusOptions = rooms.map { it.campus }.filter { it.isNotBlank() }.distinct().sorted()
  val campus =
      preferences.dormCampus.takeIf { it in campusOptions } ?: campusOptions.firstOrNull().orEmpty()
  val buildingOptions =
      rooms
          .filter { it.campus == campus }
          .map { it.building }
          .filter { it.isNotBlank() }
          .distinct()
          .sorted()
  val building =
      preferences.dormBuilding.takeIf { it in buildingOptions }
          ?: buildingOptions.firstOrNull().orEmpty()
  val roomOptions =
      rooms
          .filter { it.campus == campus && it.building == building }
          .map { it.room }
          .filter { it.isNotBlank() }
          .distinct()
          .sorted()
  val room =
      preferences.dormRoom.takeIf { it in roomOptions } ?: roomOptions.firstOrNull().orEmpty()

  SettingsDropdown(
      label = "校区",
      selectedText = campus.ifBlank { "选择校区" },
      options = campusOptions.map { it to it },
      onSelected = { selected ->
        val firstBuilding =
            rooms.filter { it.campus == selected }.map { it.building }.firstOrNull().orEmpty()
        val firstRoom =
            rooms
                .filter { it.campus == selected && it.building == firstBuilding }
                .map { it.room }
                .firstOrNull()
                .orEmpty()
        AppPreferencesStore.setDormDefaults(selected, firstBuilding, firstRoom)
      },
  )
  Spacer(modifier = Modifier.height(6.dp))
  SettingsDropdown(
      label = "宿舍楼",
      selectedText = building.ifBlank { "选择宿舍楼" },
      options = buildingOptions.map { it to it },
      onSelected = { selected ->
        val firstRoom =
            rooms
                .filter { it.campus == campus && it.building == selected }
                .map { it.room }
                .firstOrNull()
                .orEmpty()
        AppPreferencesStore.setDormDefaults(campus, selected, firstRoom)
      },
  )
  Spacer(modifier = Modifier.height(6.dp))
  SettingsDropdown(
      label = "宿舍号",
      selectedText = room.ifBlank { "选择宿舍号" },
      options = roomOptions.map { it to it },
      onSelected = { selected -> AppPreferencesStore.setDormDefaults(campus, building, selected) },
  )
}

@Composable
private fun <T> SettingsDropdown(
    label: String,
    selectedText: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
  UbaaDropdownSelector(
      selectedText = selectedText,
      label = label,
      options = options.map { it.second },
      onSelected = { index -> options.getOrNull(index)?.let { onSelected(it.first) } },
  )
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
  UbaaSectionCard(title = title, modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun SettingsOptionRow(
    title: String,
    summary: String,
    enabled: Boolean = true,
    expanded: Boolean = false,
    onClick: () -> Unit,
) {
  Row(
      Modifier.fillMaxWidth()
          .clickable(enabled = enabled, onClick = onClick)
          .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(Modifier.weight(1f)) {
      Text(
          title,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
      )
      Text(
          summary,
          style = MaterialTheme.typography.bodySmall,
          color =
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
      )
    }
    Icon(
        if (expanded) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
    )
  }
}
