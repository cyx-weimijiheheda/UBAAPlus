package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    canNavigateBack: Boolean,
    onNavigationIconClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .ubaaGlassSurface(
                  shape = RectangleShape,
                  chrome = true,
                  solidColor = MaterialTheme.colorScheme.surfaceContainer,
              )
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
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
  }
}
