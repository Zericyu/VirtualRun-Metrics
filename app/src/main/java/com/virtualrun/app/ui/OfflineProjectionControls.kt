package com.virtualrun.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virtualrun.app.model.OfflineMapOverlay
import java.util.Locale

@Composable
fun OfflineProjectionControls(
    anchorCount: Int,
    confirmed: Boolean,
    projection: Result<OfflineMapOverlay>?,
    onPickCenter: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    onUseRoute: () -> Unit,
    onClose: () -> Unit
) {
    val overlay = projection?.getOrNull()
    var retainedOverlay by remember { mutableStateOf(overlay) }
    LaunchedEffect(overlay) { if (overlay != null) retainedOverlay = overlay }
    val instruction = when {
        confirmed -> "检查紫色投影，设为路线后即可返回主界面开始。"
        anchorCount == 0 -> "第1点选场地中心。点地图，或移动十字后选取地图中心。"
        anchorCount == 1 -> "第2点选长轴端点，即场地一端弯道的最远处。"
        anchorCount == 2 -> "第3点选侧边缘中点，左右两侧均可。"
        else -> "检查形状与估算距离；点地图可调整第3点，再确认投影。"
    }
    MaterialTheme(colorScheme = IslandDialogColors) {
        Surface(
            modifier = Modifier.animateContentSize(spring(dampingRatio = 0.88f, stiffness = 360f)),
            shape = RoundedCornerShape(24.dp),
            color = IslandInk.copy(alpha = 0.97f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, IslandAccent.copy(alpha = 0.23f)),
            shadowElevation = 10.dp
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.56f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(IslandAccent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (confirmed) Icons.Default.Check else Icons.Default.Place,
                            null, Modifier.size(18.dp), tint = IslandAccent
                        )
                    }
                    Text(
                        if (confirmed) "地图投影 · 已确认" else "投影选点 · $anchorCount/3",
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, "关闭地图投影", Modifier.size(18.dp), tint = IslandMuted)
                    }
                }

                // Only the explanatory area scrolls; the route/import actions remain visible.
                Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!confirmed) ProjectionAnchorProgress(anchorCount)
                    Text(instruction, color = IslandMuted, style = MaterialTheme.typography.bodySmall)
                    AnimatedVisibility(
                        visible = overlay != null,
                        enter = fadeIn(tween(180, delayMillis = 40)) + expandVertically(
                            spring(dampingRatio = 0.88f, stiffness = 360f), expandFrom = Alignment.Top
                        ),
                        exit = fadeOut(tween(100)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Top)
                    ) {
                        val metricOverlay = overlay ?: retainedOverlay
                        if (metricOverlay != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ProjectionMetric(
                                    label = "地图估算单圈",
                                    value = String.format(Locale.ROOT, "%.1f", metricOverlay.singleLapMeters),
                                    unit = "米",
                                    detail = if (metricOverlay.clockwise) "↻ 顺时针" else "↺ 逆时针",
                                    modifier = Modifier.weight(1f)
                                )
                                ProjectionMetric(
                                    label = "2公里约需圈数",
                                    value = String.format(Locale.ROOT, "%.2f", metricOverlay.lapsForTarget),
                                    unit = "圈",
                                    detail = "距离随选点比例变化",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    projection?.exceptionOrNull()?.message?.let { message ->
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                message, modifier = Modifier.padding(10.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = confirmed,
                        enter = fadeIn(tween(180, delayMillis = 40)) + expandVertically(
                            spring(dampingRatio = 0.88f, stiffness = 360f), expandFrom = Alignment.Top
                        ),
                        exit = fadeOut(tween(100)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Top)
                    ) {
                        Text(
                            "将替换当前路线；点击开始前不会启动模拟定位。",
                            color = IslandMuted, fontSize = 10.sp
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedVisibility(
                        visible = confirmed,
                        enter = fadeIn(tween(180, delayMillis = 40)) + expandVertically(
                            spring(dampingRatio = 0.88f, stiffness = 360f), expandFrom = Alignment.Top
                        ),
                        exit = fadeOut(tween(100)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Top)
                    ) {
                        Button(
                            onClick = onUseRoute,
                            enabled = overlay != null,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IslandAccent, contentColor = IslandInk)
                        ) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("将投影设为路线", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (confirmed) {
                            ProjectionSecondaryButton("重新选点", onReset, Modifier.weight(1f))
                            ProjectionSecondaryButton("关闭图层", onClose, Modifier.weight(1f))
                        } else {
                            Button(
                                onClick = if (anchorCount == 3) onConfirm else onPickCenter,
                                enabled = anchorCount < 3 || overlay != null,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IslandAccent, contentColor = IslandInk)
                            ) {
                                Text(if (anchorCount == 3) "确认投影" else "选取地图中心", fontWeight = FontWeight.SemiBold)
                            }
                            ProjectionSecondaryButton("撤销上点", onUndo, enabled = anchorCount > 0)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectionAnchorProgress(anchorCount: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("中心", "长轴", "侧边").forEachIndexed { index, label ->
            val complete = anchorCount > index
            val active = !complete && anchorCount == index
            Row(
                Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(
                        when {
                            active -> IslandAccent
                            complete -> IslandAccent.copy(alpha = 0.15f)
                            else -> Color.White.copy(alpha = 0.06f)
                        }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    if (complete) {
                        Icon(Icons.Default.Check, null, Modifier.size(13.dp), tint = IslandAccent)
                    } else {
                        Text("${index + 1}", color = if (active) IslandInk else IslandMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(label, fontSize = 11.sp, color = if (complete || active) IslandAccent else IslandMuted)
            }
        }
    }
}

@Composable
private fun ProjectionMetric(label: String, value: String, unit: String, detail: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = IslandAccent.copy(alpha = 0.065f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, IslandAccent.copy(alpha = 0.09f))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = IslandMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, modifier = Modifier.weight(1f, fill = false), color = IslandAccent, fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(unit, color = IslandMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(detail, color = IslandMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProjectionSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, IslandAccent.copy(alpha = if (enabled) 0.23f else 0.08f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = IslandAccent),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, fontSize = 12.sp) }
}
