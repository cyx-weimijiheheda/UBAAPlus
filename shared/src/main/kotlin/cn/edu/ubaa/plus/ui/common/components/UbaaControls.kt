package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cn.edu.ubaa.plus.ui.theme.UbaaColors

val UbaaControlShape = RoundedCornerShape(14.dp)
val UbaaDialogShape = RoundedCornerShape(24.dp)

@Composable
fun UbaaIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
  IconButton(
      onClick = onClick,
      modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
      enabled = enabled,
      colors = colors,
      interactionSource = interactionSource,
      content = content,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UbaaIconAction(
    onClick: () -> Unit,
    description: String,
    icon: ImageVector = Icons.Default.Close,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
  TooltipBox(
      positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
      tooltip = { PlainTooltip { Text(description) } },
      state = rememberTooltipState(),
  ) {
    UbaaIconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
      Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
  }
}

@Composable
fun UbaaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = UbaaControlShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    loading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
  Button(
      onClick,
      modifier.heightIn(min = 48.dp),
      enabled && !loading,
      shape,
      colors,
      elevation,
      border,
      contentPadding,
      interactionSource,
  ) {
    if (loading) {
      UbaaProgressIndicator(size = 18.dp, color = LocalContentColor.current)
      Spacer(Modifier.width(8.dp))
    }
    content()
  }
}

@Composable
fun UbaaSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = UbaaControlShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
  OutlinedButton(
      onClick,
      modifier.heightIn(min = 48.dp),
      enabled,
      shape,
      colors,
      elevation,
      border,
      contentPadding,
      interactionSource,
      content,
  )
}

@Composable
fun UbaaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    requiredMessage: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = UbaaControlShape,
    clearable: Boolean = false,
) {
  var edited by remember { mutableStateOf(false) }
  var showPassword by remember { mutableStateOf(false) }
  val password = visualTransformation is PasswordVisualTransformation
  val message = errorMessage ?: requiredMessage?.takeIf { edited && value.isBlank() }
  OutlinedTextField(
      value = value,
      onValueChange = {
        edited = true
        onValueChange(it)
      },
      modifier = modifier,
      enabled = enabled,
      readOnly = readOnly,
      textStyle = textStyle,
      label = label,
      placeholder = placeholder,
      leadingIcon = leadingIcon,
      trailingIcon =
          trailingIcon
              ?: when {
                password -> ({
                      UbaaIconAction(
                          onClick = { showPassword = !showPassword },
                          description = if (showPassword) "隐藏密码" else "显示密码",
                          icon =
                              if (showPassword) Icons.Default.VisibilityOff
                              else Icons.Default.Visibility,
                          enabled = enabled,
                      )
                    })
                clearable && value.isNotEmpty() && !readOnly -> ({
                      UbaaIconAction(
                          onClick = {
                            edited = true
                            onValueChange("")
                          },
                          description = "清空输入",
                          enabled = enabled,
                      )
                    })
                else -> null
              },
      supportingText = if (message != null) ({ Text(message) }) else supportingText,
      isError = isError || message != null,
      visualTransformation =
          if (password && showPassword) VisualTransformation.None else visualTransformation,
      keyboardOptions =
          if (singleLine && keyboardOptions.imeAction == ImeAction.Default)
              keyboardOptions.copy(imeAction = ImeAction.Next)
          else keyboardOptions,
      keyboardActions = keyboardActions,
      singleLine = singleLine,
      maxLines = maxLines,
      minLines = minLines,
      shape = shape,
  )
}

@Composable
fun UbaaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "搜索",
) {
  val keyboard = LocalSoftwareKeyboardController.current
  UbaaTextField(
      value,
      onValueChange,
      modifier,
      placeholder = { Text(hint) },
      leadingIcon = { Icon(Icons.Default.Search, null) },
      singleLine = true,
      clearable = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
  )
}

@Composable
fun UbaaToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    checkbox: Boolean = false,
) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .toggleable(
                  value = checked,
                  enabled = enabled,
                  role = if (checkbox) Role.Checkbox else Role.Switch,
                  onValueChange = onCheckedChange,
              )
              .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (checkbox) Checkbox(checked, onCheckedChange = null, enabled = enabled)
    Column(Modifier.weight(1f)) {
      Text(
          label,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
      )
      if (supportingText != null)
          Text(
              supportingText,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
    }
    if (!checkbox) Switch(checked, onCheckedChange = null, enabled = enabled)
  }
}

@Composable
fun UbaaCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
) =
    UbaaToggleRow(
        label,
        checked,
        onCheckedChange,
        modifier,
        supportingText,
        enabled,
        checkbox = true,
    )

@Composable
fun UbaaProgressIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp,
) {
  CircularProgressIndicator(modifier.size(size), color = color, strokeWidth = strokeWidth)
}

enum class UbaaStatusTone {
  NEUTRAL,
  INFO,
  SUCCESS,
  WARNING,
  ERROR,
}

@Composable
fun UbaaStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: UbaaStatusTone = UbaaStatusTone.NEUTRAL,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val colors =
      when (tone) {
        UbaaStatusTone.SUCCESS ->
            UbaaColors.current.successContainer to UbaaColors.current.onSuccessContainer
        UbaaStatusTone.INFO -> scheme.primaryContainer to scheme.onPrimaryContainer
        UbaaStatusTone.WARNING ->
            UbaaColors.current.warningContainer to UbaaColors.current.onWarningContainer
        UbaaStatusTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
        UbaaStatusTone.NEUTRAL -> scheme.secondaryContainer to scheme.onSecondaryContainer
      }
  Surface(
      modifier,
      shape = RoundedCornerShape(8.dp),
      color = containerColor ?: colors.first,
      contentColor = contentColor ?: colors.second,
  ) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
    )
  }
}

@Composable
fun UbaaAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    titleActions: @Composable RowScope.() -> Unit = {},
    dismissEnabled: Boolean = true,
) {
  val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
  AlertDialog(
      onDismissRequest = onDismissRequest,
      confirmButton = confirmButton,
      dismissButton = dismissButton,
      modifier = modifier,
      icon = icon,
      title = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.weight(1f)) {
            ProvideTextStyle(MaterialTheme.typography.titleLarge) { title?.invoke() }
          }
          titleActions()
          UbaaIconAction(onClick = onDismissRequest, description = "关闭", enabled = dismissEnabled)
        }
      },
      text =
          text?.let { content ->
            ({ Box(Modifier.heightIn(max = maxContentHeight)) { content() } })
          },
      shape = UbaaDialogShape,
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 0.dp,
      properties = properties,
  )
}

@Composable
fun UbaaTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = UbaaControlShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
  TextButton(
      onClick,
      modifier.heightIn(min = 48.dp),
      enabled,
      shape,
      colors,
      elevation,
      border,
      contentPadding,
      interactionSource,
      content,
  )
}

@Composable
fun UbaaTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = UbaaControlShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
  FilledTonalButton(
      onClick,
      modifier.heightIn(min = 48.dp),
      enabled,
      shape,
      colors,
      elevation,
      border,
      contentPadding,
      interactionSource,
      content,
  )
}

/** Compact secondary actions remain actions; status-only labels use UbaaStatusBadge. */
@Composable
fun UbaaActionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    destructive: Boolean = false,
    loading: Boolean = false,
) {
  AssistChip(
      onClick = onClick,
      label = label,
      modifier = modifier.heightIn(min = 48.dp),
      enabled = enabled && !loading,
      leadingIcon =
          if (loading) ({ UbaaProgressIndicator(size = 16.dp, color = LocalContentColor.current) })
          else leadingIcon,
      trailingIcon = trailingIcon,
      shape = UbaaControlShape,
      colors =
          if (destructive)
              AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
          else colors,
  )
}
