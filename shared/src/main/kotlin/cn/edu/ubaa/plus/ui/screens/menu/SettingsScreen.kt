package cn.edu.ubaa.plus.ui.screens.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.feature.DormElectricityApi
import cn.edu.ubaa.plus.api.storage.AppPreferencesStore
import cn.edu.ubaa.plus.api.storage.FeatureLayout
import cn.edu.ubaa.plus.model.dto.DormRoomDto
import cn.edu.ubaa.plus.ui.common.components.ThemePalettePicker

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
) {
  var pendingMode by remember { mutableStateOf<ConnectionMode?>(null) }
  val preferences by AppPreferencesStore.state.collectAsState()
  var dormState by remember { mutableStateOf<DormRoomsState>(DormRoomsState.Loading) }
  val dormApi = remember { DormElectricityApi() }

  fun loadDormRooms() {
    dormState = DormRoomsState.Loading
  }

  LaunchedEffect(dormState) {
    if (dormState is DormRoomsState.Loading) {
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
        ThemePalettePicker(
            selected = preferences.themePalette,
            followWallpaper = preferences.followWallpaper,
            onPaletteSelected = AppPreferencesStore::setThemePalette,
            onFollowWallpaperChange = AppPreferencesStore::setFollowWallpaper,
        )
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
                text = "长按拖动排序，开关控制是否显示",
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
                  Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                    if (mode != currentMode) pendingMode = mode
                  },
              colors =
                  CardDefaults.cardColors(
                      containerColor =
                          if (mode == currentMode) {
                            MaterialTheme.colorScheme.secondaryContainer
                          } else {
                            MaterialTheme.colorScheme.surface
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
  var expanded by remember { mutableStateOf(false) }
  Box(modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(
        onClick = { expanded = true },
        enabled = options.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
          text = "$label：$selectedText",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { (value, text) ->
        DropdownMenuItem(
            text = { Text(text) },
            onClick = {
              expanded = false
              onSelected(value)
            },
        )
      }
    }
  }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 10.dp),
      )
      content()
    }
  }
}
