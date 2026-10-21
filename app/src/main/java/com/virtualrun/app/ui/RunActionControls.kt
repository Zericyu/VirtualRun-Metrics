package com.virtualrun.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Keep one action mounted while it moves from the right column to the screen centre. */
@Composable
fun RunActionControls(
    isRunning: Boolean,
    canStart: Boolean,
    hasRoute: Boolean,
    actionColor: Color,
    onClear: () -> Unit,
    onAction: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(52.dp)) {
        val splitWidth = (maxWidth - 12.dp) / 2
        val targetWidth = if (isRunning) maxWidth * 0.72f else splitWidth
        val width by animateDpAsState(targetWidth, spring(dampingRatio = 0.9f, stiffness = 380f), label = "run width")
        val left by animateDpAsState(
            if (isRunning) (maxWidth - targetWidth) / 2 else maxWidth - targetWidth,
            spring(dampingRatio = 0.9f, stiffness = 380f), label = "run centering"
        )
        val clearWidth by animateDpAsState(
            if (canStart || isRunning) splitWidth else maxWidth,
            spring(dampingRatio = 0.9f, stiffness = 380f), label = "clear width"
        )
        AnimatedVisibility(
            visible = !isRunning,
            modifier = Modifier.align(Alignment.CenterStart).width(clearWidth),
            enter = fadeIn(tween(180, delayMillis = 80)), exit = fadeOut(tween(100))
        ) {
            Button(
                onClick = onClear, enabled = !isRunning && hasRoute,
                colors = ButtonDefaults.buttonColors(containerColor = IslandRaised.copy(alpha = 0.96f), contentColor = IslandText),
                shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Clear, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("清除", fontSize = 16.sp)
            }
        }
        AnimatedVisibility(
            visible = isRunning || canStart,
            modifier = Modifier.offset(x = left).width(width),
            enter = fadeIn(tween(180)), exit = fadeOut(tween(120))
        ) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = actionColor, contentColor = IslandText),
                shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Crossfade(targetState = isRunning, animationSpec = tween(160), label = "run label") { running ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (running) "停止" else "开始", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
