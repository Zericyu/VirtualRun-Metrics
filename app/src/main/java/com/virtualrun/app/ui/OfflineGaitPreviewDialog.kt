package com.virtualrun.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.virtualrun.app.model.OfflineGaitPhase
import com.virtualrun.app.model.OfflineGaitPlan
import com.virtualrun.app.model.OfflineGaitPlanner
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OfflineGaitPreviewDialog(visible: Boolean, seed: Long, distanceMeters: Double, onDismiss: () -> Unit) {
    val plan = remember(seed, distanceMeters) { OfflineGaitPlanner.generate(distanceMeters, seed) }
    var fraction by rememberSaveable(seed, distanceMeters) { mutableFloatStateOf(0f) }
    val sample = plan.atElapsed(fraction.toDouble() * plan.durationSeconds)
    val phase = when (plan.phaseAt(sample.distanceMeters)) {
        OfflineGaitPhase.START -> "起步"
        OfflineGaitPhase.STEADY -> "稳定段"
        OfflineGaitPhase.FINISH -> "减速收尾"
    }
    MotionAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = { Text("离线步幅步频预览") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("起步逐渐增大 → 稳定段小幅波动 → 结尾逐渐减小。", style = MaterialTheme.typography.bodySmall)
                Text(
                    "全程平均：步频 ${number(plan.averageCadence)} 步/分钟 · 步幅 ${number(plan.averageStrideCm)} 厘米",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("蓝：步频（0–200步/分钟）", color = Color(0xFF1976D2), style = MaterialTheme.typography.labelSmall)
                Text("紫：步幅（0–80厘米） · 横轴为时间", color = Color(0xFF8051D3), style = MaterialTheme.typography.labelSmall)
                GaitChart(plan, fraction, Modifier.fillMaxWidth().height(130.dp))
                Slider(
                    value = fraction,
                    onValueChange = { fraction = it },
                    modifier = Modifier.semantics { contentDescription = "拖动查看起步、稳定段和收尾" }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { fraction = 0f }, modifier = Modifier.weight(1f)) { Text("起步") }
                    OutlinedButton(onClick = { fraction = 0.5f }, modifier = Modifier.weight(1f)) { Text("中段") }
                    OutlinedButton(onClick = { fraction = 1f }, modifier = Modifier.weight(1f)) { Text("结尾") }
                }
                Text("$phase · ${time(sample.elapsedSeconds)} · ${number(sample.distanceMeters)}米", style = MaterialTheme.typography.titleSmall)
                Text("当前步频：${number(sample.cadenceStepsPerMinute)} 步/分钟")
                Text("当前步幅：${number(sample.strideCm)} 厘米")
                Text("推算配速：${time(sample.paceSecondsPerKm)} /公里")
                Text(
                    "截至当前平均：步频 ${sample.averageCadenceSoFar?.let(::number) ?: "—"} 步/分钟\n" +
                        "步幅 ${sample.averageStrideCmSoFar?.let(::number) ?: "—"} 厘米",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "全程估算：${time(plan.durationSeconds)} · ${plan.estimatedSteps}步",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "全程平均保持设定目标；拖动时更新当前值和截至当前平均。合成示意仅供离线查看，速度由步频×步幅推算。地图投影改变长度后，本页仍对应原离线规划距离。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("返回轨迹预览") } }
    )
}

@Composable
private fun GaitChart(plan: OfflineGaitPlan, fraction: Float, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "合成步频和步幅随时间变化，两行使用各自标尺" }) {
        val rowHeight = size.height / 2f
        for (row in 0..1) {
            val bottom = (row + 1) * rowHeight - 5.dp.toPx()
            val top = row * rowHeight + 5.dp.toPx()
            drawLine(Color.LightGray, Offset(0f, bottom), Offset(size.width, bottom))
            val path = Path()
            plan.samples.forEachIndexed { index, sample ->
                val x = (sample.elapsedSeconds / plan.durationSeconds).toFloat() * size.width
                val normalized = if (row == 0) sample.cadenceStepsPerMinute / 200.0 else sample.strideCm / 80.0
                val y = bottom - normalized.toFloat() * (bottom - top)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, if (row == 0) Color(0xFF1976D2) else Color(0xFF8051D3), style = Stroke(2.dp.toPx()))
        }
        val cursor = fraction * size.width
        drawLine(Color(0xFFEF8A40), Offset(cursor, 0f), Offset(cursor, size.height), 2.dp.toPx())
    }
}

private fun number(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
private fun time(seconds: Double): String {
    val total = seconds.roundToInt()
    return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
}
