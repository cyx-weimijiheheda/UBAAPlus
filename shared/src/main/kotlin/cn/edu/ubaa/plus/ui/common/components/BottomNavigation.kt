package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

enum class BottomNavTab {
  HOME,
  REGULAR,
  PROFILE,
}

@Composable
fun BottomNavigation(
    currentTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
  NavigationBar(
      modifier = modifier.fillMaxWidth(),
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    NavigationBarItem(
        icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "主页") },
        label = { Text("主页") },
        selected = currentTab == BottomNavTab.HOME,
        onClick = { onTabSelected(BottomNavTab.HOME) },
    )

    NavigationBarItem(
        icon = { Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "功能") },
        label = { Text("功能") },
        selected = currentTab == BottomNavTab.REGULAR,
        onClick = { onTabSelected(BottomNavTab.REGULAR) },
    )

    NavigationBarItem(
        icon = { Icon(imageVector = Icons.Default.Person, contentDescription = "个人中心") },
        label = { Text("个人中心") },
        selected = currentTab == BottomNavTab.PROFILE,
        onClick = { onTabSelected(BottomNavTab.PROFILE) },
    )
  }
}
