package cn.edu.ubaa.plus.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.feature.LibraryLoanDto
import cn.edu.ubaa.plus.model.format.formatUbaaDate
import cn.edu.ubaa.plus.ui.common.components.UbaaLoadingState
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaToastEffect

@Composable
fun LibraryBorrowScreen(
    viewModel: LibraryBorrowViewModel,
    onOpenUrl: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsState()
  LaunchedEffect(Unit) { viewModel.ensureLoaded() }
  val loans =
      when (state.selectedTab) {
        LibraryBorrowTab.CURRENT -> state.currentLoans
        LibraryBorrowTab.HISTORY -> state.history
      }

  UbaaToastEffect(state.error) {}

  Scaffold(
      containerColor = androidx.compose.ui.graphics.Color.Transparent,
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      TabRow(selectedTabIndex = state.selectedTab.ordinal) {
        LibraryBorrowTab.entries.forEach { tab ->
          Tab(
              selected = state.selectedTab == tab,
              onClick = { viewModel.selectTab(tab) },
              text = { Text(tab.label) },
          )
        }
      }

      UbaaPullToRefresh(
          refreshing = state.isLoading,
          onRefresh = viewModel::refresh,
          modifier = Modifier.fillMaxSize(),
      ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          when {
            state.isLoading && loans.isEmpty() ->
                item {
                  UbaaLoadingState(
                      text = "正在加载借阅记录...",
                      showIndicator = false,
                      modifier = Modifier.fillMaxWidth().height(200.dp),
                  )
                }
            state.error != null && loans.isEmpty() ->
                item {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                    ) {
                      Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            text = state.error.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        OutlinedButton(onClick = viewModel::refresh) { Text("重试") }
                      }
                    }
                    OutlinedButton(
                        onClick = { onOpenUrl("https://space.lib.buaa.edu.cn/space/") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text("打开图书馆网站登录")
                    }
                  }
                }
            loans.isEmpty() ->
                item {
                  Text(
                      text =
                          if (state.selectedTab == LibraryBorrowTab.CURRENT) "暂无当前借阅" else "暂无借阅历史",
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
            else -> items(loans) { loan -> LibraryLoanCard(loan) }
          }
          item {
            OutlinedButton(
                onClick = { onOpenUrl("https://space.lib.buaa.edu.cn/space/") },
                modifier = Modifier.fillMaxWidth(),
            ) {
              Text("打开图书馆网站")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LibraryLoanCard(loan: LibraryLoanDto) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
          text = loan.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
      if (loan.author.isNotBlank()) {
        Text(
            text = loan.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
      if (loan.callNo.isNotBlank()) {
        Text("索书号：${loan.callNo}", style = MaterialTheme.typography.bodySmall)
      }
      if (loan.loanDate.isNotBlank()) {
        Text("借阅日期：${formatUbaaDate(loan.loanDate)}", style = MaterialTheme.typography.bodySmall)
      }
      if (loan.dueDate.isNotBlank()) {
        Text(
            text = "应还日期：${formatUbaaDate(loan.dueDate)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
      }
      if (loan.renewTimes > 0) {
        Text("续借次数：${loan.renewTimes}", style = MaterialTheme.typography.bodySmall)
      }
      if (loan.status.isNotBlank()) {
        Text(loan.status, style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}
