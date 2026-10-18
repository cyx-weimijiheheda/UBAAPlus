package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.ui.theme.UbaaBrand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    canNavigateBack: Boolean,
    onNavigationIconClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
  CenterAlignedTopAppBar(
      expandedHeight = 56.dp,
      title = { Text(title) },
      navigationIcon = {
        if (canNavigateBack) {
          IconButton(onClick = onNavigationIconClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
          }
        }
      },
      actions = actions,
      colors =
          TopAppBarDefaults.topAppBarColors(
              containerColor = UbaaBrand.current.brand,
              titleContentColor = UbaaBrand.current.onBrand,
              navigationIconContentColor = UbaaBrand.current.onBrand,
              actionIconContentColor = UbaaBrand.current.onBrand,
          ),
  )
}
