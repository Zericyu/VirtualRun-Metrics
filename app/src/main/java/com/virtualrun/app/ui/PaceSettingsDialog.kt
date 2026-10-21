package com.virtualrun.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.virtualrun.app.model.RunPace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaceSettingsDialog(
    visible: Boolean,
    pace: RunPace,
    isRunning: Boolean,
    onSave: (RunPace) -> Unit,
    onDismiss: () -> Unit
) {
    var fixed by remember { mutableStateOf(pace.isFixed) }
    var fastestText by remember { mutableStateOf(RunPace.format(pace.fastestSecondsPerKm)) }
    var slowestText by remember { mutableStateOf(RunPace.format(pace.slowestSecondsPerKm)) }
    LaunchedEffect(visible) {
        if (visible) {
            fixed = pace.isFixed
            fastestText = RunPace.format(pace.fastestSecondsPerKm)
            slowestText = RunPace.format(pace.slowestSecondsPerKm)
        }
    }
    val fastest = RunPace.parse(fastestText)
    val slowest = if (fixed) fastest else RunPace.parse(slowestText)
    val valid = fastest != null && slowest != null && fastest <= slowest
    MotionAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        islandStyle = true,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(IslandAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Speed, null, Modifier.size(18.dp), tint = IslandAccent)
                }
                Column(Modifier.weight(1f)) {
                    Text("自定义配速", style = MaterialTheme.typography.titleMedium)
                    Text("运动参数", color = IslandMuted, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, "关闭配速设置", Modifier.size(18.dp), tint = IslandMuted)
                }
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("当前：${pace.displayText}", color = IslandAccent, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = fixed, onClick = { fixed = true }, enabled = !isRunning, label = { Text("固定配速") })
                    FilterChip(selected = !fixed, onClick = { fixed = false }, enabled = !isRunning, label = { Text("浮动范围") })
                }
                Text("直接输入大于0的分:秒，例如5:45、8:10。配速没有预设区间，数字越小，速度越快；精确到秒。",
                    color = IslandMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = fastestText, onValueChange = { fastestText = it },
                    label = { Text(if (fixed) "目标配速（分:秒/公里）" else "最快配速（分:秒/公里）") },
                    enabled = !isRunning, singleLine = true, isError = fastest == null,
                    shape = RoundedCornerShape(16.dp),
                    supportingText = { if (fastest == null) Text("请输入有效配速，例如6:00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = if (fixed) ImeAction.Done else ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = if (fixed) "目标配速输入" else "最快配速输入" }
                )
                AnimatedVisibility(visible = !fixed, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    OutlinedTextField(
                        value = slowestText, onValueChange = { slowestText = it },
                        label = { Text("最慢配速（分:秒/公里）") },
                        enabled = !isRunning, singleLine = true, isError = slowest == null || (fastest != null && slowest < fastest),
                        shape = RoundedCornerShape(16.dp),
                        supportingText = {
                            if (slowest == null) Text("请输入有效配速，例如6:30")
                            else if (fastest != null && slowest < fastest) Text("最慢配速应大于或等于最快配速")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "最慢配速输入" }
                    )
                }
                Text(
                    if (isRunning) "运行中暂不能修改。停止后保存，新配速用于下一次模拟。"
                    else if (fixed) "保存后，下次模拟按目标配速运行。"
                    else "保存后，下次模拟在所设范围内平滑变化；范围相同则按固定配速运行。",
                    color = IslandMuted, style = MaterialTheme.typography.bodySmall
                )
                TextButton(enabled = !isRunning, onClick = {
                    fixed = false; fastestText = "7:03"; slowestText = "7:26"
                }) { Text("恢复默认配速") }
            }
        },
        confirmButton = {
            Button(enabled = valid && !isRunning, shape = RoundedCornerShape(50), onClick = {
                if (fastest != null && slowest != null && valid) {
                    onSave(RunPace(fastest, slowest))
                    onDismiss()
                }
            }) { Text("保存配速") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
