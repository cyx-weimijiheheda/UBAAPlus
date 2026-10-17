package cn.edu.ubaa.plus.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.ui.common.components.BottomNavTab
import cn.edu.ubaa.plus.ui.common.components.BottomNavigation
import cn.edu.ubaa.plus.ui.common.components.Sidebar

/** 应用底部的三个根 Tab。 */
@Composable
internal fun MainBottomBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
) {
  BottomNavigation(currentTab = selectedTab, onTabSelected = onTabSelected)
}

/**
 * 侧边栏及其遮罩。
 *
 * 遮罩点击与各菜单项点击都会先关闭侧边栏，这里统一处理，避免调用方重复写 `showSidebar = false`。
 */
@Composable
internal fun MainSidebar(
    visible: Boolean,
    userData: UserData,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
) {
  AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable { onDismiss() })
  }

  AnimatedVisibility(
      visible = visible,
      enter = slideInHorizontally(),
      exit = slideOutHorizontally(targetOffsetX = { -it * 2 }),
  ) {
    Box(Modifier.fillMaxHeight(), Alignment.CenterStart) {
      Sidebar(
          userData = userData,
          onLogoutClick = {
            onDismiss()
            onLogout()
          },
          modifier = Modifier.align(Alignment.CenterStart),
      )
    }
  }
}
