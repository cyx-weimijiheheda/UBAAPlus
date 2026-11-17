package cn.edu.ubaa.plus.ui.screens.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cn.edu.ubaa.plus.api.storage.LocalDiagnostics
import cn.edu.ubaa.plus.api.storage.ThemeMode
import cn.edu.ubaa.plus.api.storage.WidgetColorMode
import cn.edu.ubaa.plus.model.dto.DormRoomDto
import cn.edu.ubaa.plus.reminder.ReminderServiceController
import cn.edu.ubaa.plus.ui.common.components.CustomBrandColorDialog
import cn.edu.ubaa.plus.ui.common.components.ThemePalettePicker
import cn.edu.ubaa.plus.ui.common.components.UbaaDropdownSelector
import cn.edu.ubaa.plus.ui.common.components.UbaaSectionCard
import cn.edu.ubaa.plus.ui.common.components.UbaaSegmentedTabs
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface

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
    modifier: Modifier = Modifier,
    allowSchoolReads: Boolean = true,
    onLoginRequired: () -> Unit = {},
) {
  val diagnosticContext = androidx.compose.ui.platform.LocalContext.current
  var diagnosticCount by remember { mutableStateOf(LocalDiagnostics.issues().size) }
  val diagnosticExport =
      rememberLauncherForActivityResult(
          ActivityResultContracts.CreateDocument("application/json")
      ) { uri ->
        if (uri != null) {
          val result = runCatching {
            diagnosticContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
              it.write(LocalDiagnostics.export())
            } ?: error("输出文件不可用")
          }
          cn.edu.ubaa.plus.ui.common.components.UbaaToast.show(
              if (result.isSuccess) "诊断已导出" else "诊断导出失败，请重试"
          )
        }
      }
  var showMysteryDisclaimer by remember { mutableStateOf(false) }
  var pendingMode by remember { mutableStateOf<ConnectionMode?>(null) }
  val preferences by AppPreferencesStore.state.collectAsState()
  var showWidgetColorPicker by remember { mutableStateOf(false) }
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
    AlertDialog(
        onDismissRequest = { showMysteryDisclaimer = false },
        title = { Text("免责声明") },
        text = {
          Text(
              "开启后显示博雅抢课与场地抢单控件。自动任务仅在应用前台运行，不保证成功；请求可能受学校规则、名额和网络状况影响。请遵守学校规定，确认课程时间、预约费用及取消规则。结果未确认时请先查询记录，避免重复操作。关闭开关会停止正在运行的自动任务。"
          )
        },
        confirmButton = {
          TextButton(
              onClick = {
                showMysteryDisclaimer = false
                AppPreferencesStore.setMysterySwitchEnabled(true)
              }
          ) {
            Text("我已了解，开启")
          }
        },
        dismissButton = { TextButton(onClick = { showMysteryDisclaimer = false }) { Text("取消") } },
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

  LaunchedEffect(dormState) {
    if (allowSchoolReads && dormState is DormRoomsState.Loading) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("玻璃与磨砂效果", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "底部导航使用透亮玻璃，内容卡片使用柔和磨砂",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
              checked = preferences.glassEnabled,
              onCheckedChange = AppPreferencesStore::setGlassEnabled,
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("功能页布局", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FeatureLayout.entries.forEach { layout ->
            FilterChip(
                selected = preferences.featureLayout == layout,
                onClick = { AppPreferencesStore.setFeatureLayout(layout) },
                label = { Text(layout.displayName) },
            )
          }
        }
      }
    }

    item {
      SettingsSection(title = "功能选项") {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("？！！？", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
          Switch(
              checked = preferences.mysterySwitchEnabled,
              onCheckedChange = { enabled ->
                if (enabled) showMysteryDisclaimer = true
                else AppPreferencesStore.setMysterySwitchEnabled(false)
              },
          )
        }
      }
    }

    item {
      SettingsSection(title = "问题诊断") {
        Text(
            "本机已记录 $diagnosticCount 条诊断，仅包含功能、连接方式和错误分类。",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { diagnosticExport.launch("UBAAPlus-diagnostics.json") }) {
            Text("导出脱敏诊断")
          }
          TextButton(
              onClick = {
                LocalDiagnostics.clear()
                diagnosticCount = 0
              }
          ) {
            Text("清除记录")
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
          TextButton(onClick = { showWidgetColorPicker = true }) { Text("设置小组件颜色") }
        }
        Text(
            "今日课程小组件同时显示待办，也可单独添加待办小组件。待办来自应用同步快照；打开首页可更新。Android 12 以下壁纸配色回退到应用配色。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      SettingsSection(title = "通知") {
        val context = LocalContext.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("后台提醒", style = MaterialTheme.typography.bodyLarge)
            Text(
                text =
                    "登录后在后台待命，只在课程开课、签到/签退开启、新作业发布或作业将截止时推送，启动时不打扰。" +
                        "关闭只停止通知与前台服务，首页的课程、考勤与图书馆座位照常显示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
              checked = preferences.backgroundRemindersEnabled,
              onCheckedChange = AppPreferencesStore::setBackgroundRemindersEnabled,
          )
        }
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
      }
    }

    item {
      SettingsSection(title = "功能") {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(onClick = onFeatureOrderClick)
                    .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("功能排序与显示", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "分显示/隐藏两层，用上下按钮调整顺序",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    item {
      SettingsSection(title = "寝室用电默认值") {
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
            TextButton(onClick = ::loadDormRooms) { Text("重试") }
            Text(
                text = "也可以手动填写，查询页会直接使用这些值。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
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
            OutlinedTextField(
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
            OutlinedTextField(
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

    item {
      SettingsSection(title = "连接模式") {
        Text(
            text = "当前模式：${currentMode.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        availableModes.forEach { mode ->
          Card(
              modifier =
                  Modifier.padding(vertical = 6.dp).ubaaGlassSurface().fillMaxWidth().clickable {
                    if (mode != currentMode) pendingMode = mode
                  },
              colors =
                  CardDefaults.cardColors(
                      containerColor =
                          if (mode == currentMode) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                          } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                          }
                  ),
          ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected = mode == currentMode, onClick = null)
              Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(text = mode.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }

  pendingMode?.let { mode ->
    AlertDialog(
        onDismissRequest = { pendingMode = null },
        title = { Text("切换连接模式") },
        text = { Text("将退出当前登录并清除本模式会话，需重新登录。") },
        confirmButton = {
          TextButton(
              onClick = {
                pendingMode = null
                onModeSelected(mode)
              }
          ) {
            Text("确认切换")
          }
        },
        dismissButton = { TextButton(onClick = { pendingMode = null }) { Text("取消") } },
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
