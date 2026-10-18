package cn.edu.ubaa.plus.ui.navigation

import androidx.compose.runtime.Composable
import cn.edu.ubaa.plus.ui.common.components.BottomNavTab
import cn.edu.ubaa.plus.ui.common.components.BottomNavigation

/** 应用底部的三个根 Tab。 */
@Composable
internal fun MainBottomBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
) {
  BottomNavigation(currentTab = selectedTab, onTabSelected = onTabSelected)
}
