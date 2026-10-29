package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 统一的“从上向下拉动”刷新容器。
 *
 * 项目里已经有 9 个页面使用 Material2 的 pullRefresh；这里抽出同一个封装， 避免每个页面各写一遍状态和指示器。内容根部必须是可滚动容器 （LazyColumn /
 * verticalScroll），否则手势没有 nested scroll 来源。
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UbaaPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorBackground: Color = MaterialTheme.colorScheme.surface,
    indicatorContent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit,
) {
  val state = rememberPullRefreshState(refreshing = refreshing, onRefresh = onRefresh)
  Box(modifier = modifier.fillMaxSize().pullRefresh(state)) {
    content()
    PullRefreshIndicator(
        refreshing = refreshing,
        state = state,
        modifier = Modifier.align(Alignment.TopCenter),
        backgroundColor = indicatorBackground,
        contentColor = indicatorContent,
    )
  }
}
