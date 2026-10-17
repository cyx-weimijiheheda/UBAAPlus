package cn.edu.ubaa.plus.ui.screens.venue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.venue.VenueBuddy

/** 同伴管理弹窗：按学工号添加、删除。 */
@Composable
fun VenueBuddyDialog(
    buddies: List<VenueBuddy>,
    selectedIds: Set<Int>,
    limit: Int,
    onAdd: (String) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
  var uid by remember { mutableStateOf("") }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("管理同伴") },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
              text = "已选 ${selectedIds.size}/$limit 位同伴",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
                value = uid,
                onValueChange = { uid = it.filter(Char::isLetterOrDigit) },
                label = { Text("学工号") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                  if (uid.isNotBlank()) {
                    onAdd(uid)
                    uid = ""
                  }
                },
                enabled = uid.isNotBlank(),
            ) {
              Text("添加")
            }
          }

          LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            items(buddies, key = { it.id }) { buddy ->
              Row(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                      text = buddy.name.ifBlank { buddy.userUid },
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Medium,
                  )
                  Text(
                      text = buddy.userUid,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(onClick = { onDelete(buddy.id) }) {
                  Icon(Icons.Default.Delete, contentDescription = "删除同伴")
                }
              }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
  )
}
