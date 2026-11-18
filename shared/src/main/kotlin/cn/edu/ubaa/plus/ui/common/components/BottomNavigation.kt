package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.ui.theme.ubaaLiquidGlass

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
  val itemColors =
      NavigationBarItemDefaults.colors(
          selectedIconColor = MaterialTheme.colorScheme.primary,
          selectedTextColor = MaterialTheme.colorScheme.primary,
          indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f),
          unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
          unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
      )

  Box(modifier = modifier.navigationBarsPadding()) {
    NavigationBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(72.dp)
                .ubaaLiquidGlass(shape = RoundedCornerShape(28.dp)),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
      NavigationBarItem(
          icon = {
            Icon(
                imageVector =
                    if (currentTab == BottomNavTab.HOME) Icons.Default.Home
                    else Icons.Outlined.Home,
                contentDescription = "主页",
            )
          },
          label = { Text("主页") },
          selected = currentTab == BottomNavTab.HOME,
          onClick = { onTabSelected(BottomNavTab.HOME) },
          colors = itemColors,
      )

      NavigationBarItem(
          icon = {
            Icon(
                imageVector =
                    if (currentTab == BottomNavTab.REGULAR) Icons.AutoMirrored.Filled.List
                    else Icons.AutoMirrored.Outlined.List,
                contentDescription = "功能",
            )
          },
          label = { Text("功能") },
          selected = currentTab == BottomNavTab.REGULAR,
          onClick = { onTabSelected(BottomNavTab.REGULAR) },
          colors = itemColors,
      )

      NavigationBarItem(
          icon = {
            Icon(
                imageVector =
                    if (currentTab == BottomNavTab.PROFILE) Icons.Default.Person
                    else Icons.Outlined.Person,
                contentDescription = "个人中心",
            )
          },
          label = { Text("个人中心") },
          selected = currentTab == BottomNavTab.PROFILE,
          onClick = { onTabSelected(BottomNavTab.PROFILE) },
          colors = itemColors,
      )
    }
  }
}
