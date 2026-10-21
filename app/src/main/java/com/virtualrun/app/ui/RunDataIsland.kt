package com.virtualrun.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virtualrun.app.model.SimulationMetrics
import com.virtualrun.app.model.RunPace
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun RunDataIsland(
    metrics: SimulationMetrics,
    paceSettings: RunPace,
    isRunning: Boolean,
    expanded: Boolean,
    isLoop: Boolean,
    lap: Int,
    routePointCount: Int,
    routeDistance: Float,
    routeDuration: Long,
    progress: Float,
    deliverySummary: String,
    deliveryWarning: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onParameters: () -> Unit,
    onPaceSettings: () -> Unit,
    onDelivery: () -> Unit,
    onCloseLoop: () -> Unit,
    onReverse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screen = LocalConfiguration.current
    val collapsedWidth = (screen.screenWidthDp - 144).coerceIn(180, 264).dp
    val width by animateDpAsState(
        if (expanded) (screen.screenWidthDp - 32).coerceAtMost(420).dp else collapsedWidth,
        spring(dampingRatio = 0.86f, stiffness = 420f), label = "island width"
    )
    val radius by animateDpAsState(if (expanded) 28.dp else 50.dp, spring(stiffness = 420f), label = "island radius")
    BackHandler(expanded) { onExpandedChange(false) }
    Surface(
        modifier = modifier.width(width).animateContentSize(spring(dampingRatio = 0.88f, stiffness = 420f)),
        color = IslandInk, contentColor = IslandText, shape = RoundedCornerShape(radius), shadowElevation = 12.dp
    ) {
        Column {
            Surface(
                onClick = { onExpandedChange(!expanded) }, color = Color.Transparent, contentColor = IslandText,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = if (expanded) "收起运动数据" else "展开运动数据"
                }
            ) {
                Row(
                    Modifier.heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(IslandAccent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(if (isRunning) Icons.Default.DirectionsRun else Icons.Default.Insights, null, Modifier.size(18.dp), tint = IslandAccent)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (expanded) "运动数据" else if (isRunning) "${number(metrics.distanceMeters / 1000, 2)} km · ${clock(metrics.elapsedMillis)}"
                            else "${metrics.estimatedSteps} 步 · ${pace(metrics.averagePaceSecondsPerKm)}",
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (isRunning) (if (isLoop) "模拟中 · 第 ${lap + 1} 圈" else "模拟中")
                            else if (metrics.elapsedMillis > 0) "已停止 · 本地估算" else "待开始 · 本地估算",
                            color = if (isRunning) IslandAccent else IslandMuted, fontSize = 10.sp, maxLines = 1
                        )
                    }
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(16.dp), tint = IslandMuted)
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(220, delayMillis = 60)) + expandVertically(spring(stiffness = 420f), expandFrom = Alignment.Top),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(220), shrinkTowards = Alignment.Top)
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = screen.screenHeightDp.dp * 0.57f)
                        .verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Divider(color = Color.White.copy(alpha = 0.10f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IslandMetric("模拟距离", "${number(metrics.distanceMeters / 1000, 2)}", "公里", Modifier.weight(1f))
                        IslandMetric("模拟时长", clock(metrics.elapsedMillis), "时长", Modifier.weight(1f))
                        IslandMetric("累计步数", metrics.estimatedSteps.toString(), "步 · 估算", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IslandMetric("平均步频", if (metrics.elapsedMillis > 0) number(metrics.averageCadence, 1) else "—", "步/分钟", Modifier.weight(1f))
                        IslandMetric("平均步幅", if (metrics.estimatedSteps > 0) number(metrics.averageStrideCm, 1) else "—", "厘米", Modifier.weight(1f))
                        IslandMetric("运动量", number(metrics.estimatedCalories, 1), "千卡 · 估算", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("平均配速", fontSize = 12.sp, color = IslandMuted)
                        Text("${pace(metrics.averagePaceSecondsPerKm)} /公里", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = onPaceSettings, modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(paceSettings.displayText, color = IslandAccent, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("设置", color = IslandAccent, fontSize = 11.sp)
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = IslandAccent)
                    }
                    if (routePointCount >= 2) {
                        Divider(color = Color.White.copy(alpha = 0.10f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (isLoop) "循环路线" else "已选路线", fontSize = 12.sp, color = IslandMuted)
                            Text("${number(routeDistance / 1000.0, 2)} km · 预计 ${formatDuration(routeDuration)}", fontSize = 12.sp)
                        }
                        if (isRunning) {
                            LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), Modifier.fillMaxWidth().height(3.dp), color = IslandAccent, trackColor = Color.White.copy(alpha = 0.12f))
                            Text("进度 ${number(progress * 100.0, 1)}%" + if (isLoop) " · 第 ${lap + 1} 圈" else "", color = IslandMuted, fontSize = 11.sp)
                        } else {
                            Row {
                                IslandTextButton("反转跑步方向", onReverse)
                                if (routePointCount >= 3 && !isLoop) IslandTextButton("闭合路线", onCloseLoop)
                            }
                        }
                    }
                    if (isRunning || metrics.elapsedMillis > 0) {
                        TextButton(onClick = onDelivery, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Default.FiberManualRecord, null, Modifier.size(8.dp), tint = if (deliveryWarning) Color(0xFFFFB58C) else IslandAccent)
                            Spacer(Modifier.width(6.dp))
                            Text(deliverySummary, color = if (deliveryWarning) Color(0xFFFFB58C) else IslandMuted, fontSize = 11.sp, maxLines = 2)
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = IslandMuted)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("模拟数据 · 仅本应用显示", color = IslandMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        IslandTextButton("详情与参数", onParameters)
                    }
                }
            }
        }
    }
}

@Composable private fun IslandMetric(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = IslandMuted, fontSize = 10.sp)
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(unit, color = IslandMuted, fontSize = 9.sp, maxLines = 1)
    }
}
@Composable private fun IslandTextButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = IslandAccent), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
        Text(label, fontSize = 11.sp)
    }
}
private fun number(value: Double, digits: Int) = String.format(Locale.ROOT, "%.${digits}f", value)
private fun clock(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
private fun pace(value: Double?): String {
    if (value == null) return "—"
    val seconds = value.roundToLong()
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
