package com.virtualrun.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun MapLocateButton(
    backdrop: MapGlassBackdrop?,
    locating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MapActionGlass(
        backdrop = backdrop,
        description = "定位自己",
        state = if (locating) "正在定位" else null,
        onClick = onClick,
        modifier = modifier.size(60.dp)
    ) {
        if (locating) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).clearAndSetSemantics {},
                color = IslandAccent,
                strokeWidth = 2.dp
            )
        } else {
            Icon(Icons.Default.MyLocation, null, Modifier.size(25.dp), tint = IslandAccent)
        }
    }
}

@Composable
fun MapPreviewButton(
    backdrop: MapGlassBackdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MapActionGlass(
        backdrop = backdrop,
        description = "一键生成跑圈预览",
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(60.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(12.dp))
                    .background(IslandAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Loop, null, Modifier.size(21.dp), tint = IslandAccent)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "一键生成跑圈预览",
                    color = IslandAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "预览轨迹 · 地图选点",
                    modifier = Modifier.clearAndSetSemantics {},
                    color = IslandMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = IslandAccent.copy(alpha = 0.8f))
        }
    }
}

/** Both actions sample the existing shared backdrop; no extra map capture or platform blur. */
@Composable
private fun MapActionGlass(
    backdrop: MapGlassBackdrop?,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.76f, stiffness = 520f),
        label = "map action press"
    )
    val shape = RoundedCornerShape(20.dp)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            if (state != null) stateDescription = state
        }.clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(12.dp, shape, clip = false, ambientColor = IslandInk.copy(alpha = 0.34f), spotColor = IslandInk.copy(alpha = 0.55f))
                .clip(shape).onGloballyPositioned { bounds = it.boundsInWindow() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val image = backdrop
                if (image != null && bounds.width > 0f && bounds.height > 0f && image.bounds.width > 0f && image.bounds.height > 0f) {
                    val sx = image.image.width / image.bounds.width
                    val sy = image.image.height / image.bounds.height
                    val width = (bounds.width * sx / 1.04f).roundToInt().coerceIn(1, image.image.width)
                    val height = (bounds.height * sy / 1.04f).roundToInt().coerceIn(1, image.image.height)
                    val left = ((bounds.center.x - image.bounds.left) * sx - width / 2f).roundToInt()
                        .coerceIn(0, image.image.width - width)
                    val top = ((bounds.center.y - image.bounds.top) * sy - height / 2f).roundToInt()
                        .coerceIn(0, image.image.height - height)
                    drawImage(
                        image.image, IntOffset(left, top), IntSize(width, height),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                } else {
                    drawRect(IslandInk)
                }
                drawRect(Brush.linearGradient(
                    listOf(IslandRaised.copy(alpha = 0.92f), IslandInk.copy(alpha = 0.90f), IslandRaised.copy(alpha = 0.94f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ))
                // A restrained sheen keeps the surface readable over bright road tiles.
                drawRect(Brush.verticalGradient(
                    listOf(IslandAccent.copy(alpha = 0.08f), Color.Transparent),
                    endY = size.height * 0.7f
                ))
                if (pressed) drawRect(IslandAccent.copy(alpha = 0.09f))
            }
            Box(Modifier.fillMaxSize().border(
                1.dp,
                Brush.linearGradient(
                    listOf(IslandAccent.copy(alpha = 0.44f), Color.White.copy(alpha = 0.08f), IslandAccent.copy(alpha = 0.20f))
                ),
                shape
            ))
            content()
        }
    }
}
