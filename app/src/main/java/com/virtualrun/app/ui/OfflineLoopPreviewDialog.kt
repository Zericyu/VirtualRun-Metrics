package com.virtualrun.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.virtualrun.app.model.OfflineLoopPlan
import com.virtualrun.app.model.OfflineLoopPlanner
import androidx.compose.material3.OutlinedButton
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** A separate, local-coordinate preview. It neither edits routes nor starts location playback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineLoopPreviewDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    projectionEnabled: Boolean = true,
    onProjectToMap: (OfflineLoopPlan) -> Unit
) {
    var clockwise by rememberSaveable { mutableStateOf(false) }
    var seed by rememberSaveable { mutableLongStateOf(Random.nextLong()) }
    var showGaitPreview by rememberSaveable { mutableStateOf(false) }
    val plan = remember(seed, clockwise) {
        OfflineLoopPlanner.generate(seed = seed, clockwise = clockwise)
    }
    val direction = if (clockwise) "顺时针" else "逆时针"
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    MotionDialog(visible = visible, onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("离线跑圈预览", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "合成示意 · 本地二维坐标",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭跑圈预览") }
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            selected = !clockwise,
                            onClick = { clockwise = false },
                            label = { Text("逆时针") }
                        )
                        FilterChip(
                            selected = clockwise,
                            onClick = { clockwise = true },
                            label = { Text("顺时针") }
                        )
                    }
                    LoopPlanCanvas(
                        plan = plan,
                        modifier = Modifier.fillMaxWidth().height(248.dp).semantics {
                            contentDescription = "离线合成跑道形示意，$direction，橙点为起终点"
                        }
                    )
                    Text(
                        "2.00 公里 · 400米 × 5圈 · $direction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "灰虚线为基础轮廓，蓝线沿全圈分布细小连续变化。箭头表示方向，橙点为起终点；切换方向保留当前曲线。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("几何自检", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "采样折线长度：${String.format(Locale.ROOT, "%.2f", plan.polylineDistanceMeters)} 米",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("规划距离：2000.00 米", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "本图是合成示意。确认后在地图选3个参考点投影，距离按地图比例重新估算；确认投影后可将其设为路线，再点击开始模拟。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { showGaitPreview = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("查看步幅步频过程")
                }
                Button(onClick = { seed = Random.nextLong() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("重新生成预览", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { onProjectToMap(plan) },
                    enabled = projectionEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (projectionEnabled) "确认预览，去地图选点" else "停止模拟后可投影") }
            }
        }
    }
    OfflineGaitPreviewDialog(visible && showGaitPreview, seed, plan.targetDistanceMeters, onDismiss = { showGaitPreview = false })
}

@Composable
private fun LoopPlanCanvas(plan: OfflineLoopPlan, modifier: Modifier = Modifier) {
    val points = plan.lapOutline
    Canvas(modifier) {
        drawRoundRect(Color(0xFFF1F6FA), cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
        if (points.size < 3) return@Canvas
        val allPoints = points + plan.baselineOutline
        val minX = allPoints.minOf { it.xMeters }
        val maxX = allPoints.maxOf { it.xMeters }
        val minY = allPoints.minOf { it.yMeters }
        val maxY = allPoints.maxOf { it.yMeters }
        val padding = 20.dp.toPx()
        val scale = min(
            (size.width - padding * 2) / (maxX - minX).toFloat(),
            (size.height - padding * 2) / (maxY - minY).toFloat()
        )
        val left = (size.width - (maxX - minX).toFloat() * scale) / 2
        val top = (size.height - (maxY - minY).toFloat() * scale) / 2
        val projected = points.map {
            Offset(left + (it.xMeters - minX).toFloat() * scale, top + (maxY - it.yMeters).toFloat() * scale)
        }
        val path = Path().apply {
            moveTo(projected.first().x, projected.first().y)
            projected.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, Color(0xFFE6F1E9))
        val baseline = Path().apply {
            plan.baselineOutline.forEachIndexed { index, point ->
                val x = left + (point.xMeters - minX).toFloat() * scale
                val y = top + (maxY - point.yMeters).toFloat() * scale
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            baseline,
            Color(0xFF75849A),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        )
        drawPath(path, Color(0xFF1E88E5), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        for (fraction in listOf(0.15, 0.45, 0.75)) {
            val index = (projected.lastIndex * fraction).roundToInt().coerceIn(1, projected.lastIndex - 1)
            val before = projected[index - 1]
            val after = projected[index + 1]
            val length = hypot(after.x - before.x, after.y - before.y)
            if (length <= 0f) continue
            val direction = Offset((after.x - before.x) / length, (after.y - before.y) / length)
            val normal = Offset(-direction.y, direction.x)
            val center = projected[index]
            val tip = center + direction * 8.dp.toPx()
            val base = center - direction * 6.dp.toPx()
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo((base + normal * 5.dp.toPx()).x, (base + normal * 5.dp.toPx()).y)
                lineTo((base - normal * 5.dp.toPx()).x, (base - normal * 5.dp.toPx()).y)
                close()
            }
            drawPath(arrow, Color(0xFF134BA0))
        }
        drawCircle(Color.White, 9.dp.toPx(), projected.first())
        drawCircle(Color(0xFFF27653), 6.dp.toPx(), projected.first())
    }
}
