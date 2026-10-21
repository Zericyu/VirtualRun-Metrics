package com.virtualrun.app.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.virtualrun.app.map.MapMode
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.MapView
import kotlin.math.roundToInt

data class MapGlassBackdrop(val image: ImageBitmap, val bounds: Rect)

/** One small, blurred map snapshot shared by all buttons; no per-button map captures. */
@Composable
fun rememberMapGlassBackdrop(map: MapView?, enabled: Boolean, mode: MapMode): MapGlassBackdrop? {
    var backdrop by remember(map, mode) { mutableStateOf<MapGlassBackdrop?>(null) }
    // A mode switch replaces the tile provider, so listen to its new completion queue.
    DisposableEffect(map, enabled, mode) {
        val handler = Handler(Looper.getMainLooper())
        val activeMap = map
        var queued = false
        val capture = Runnable {
            queued = false
            if (activeMap != null && enabled && activeMap.width > 0 && activeMap.height > 0 && activeMap.isAttachedToWindow) {
                runCatching {
                    val width = (activeMap.width * 0.18f).roundToInt().coerceAtLeast(1)
                    val height = (activeMap.height * 0.18f).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(bitmap).apply {
                        scale(width.toFloat() / activeMap.width, height.toFloat() / activeMap.height)
                        activeMap.draw(this)
                    }
                    blurSmallBitmap(bitmap)
                    val origin = IntArray(2)
                    activeMap.getLocationInWindow(origin)
                    MapGlassBackdrop(bitmap.asImageBitmap(), Rect(
                        origin[0].toFloat(), origin[1].toFloat(),
                        (origin[0] + activeMap.width).toFloat(), (origin[1] + activeMap.height).toFloat()
                    ))
                }.onSuccess { backdrop = it }
            }
        }
        fun requestCapture() {
            if (enabled && !queued) {
                queued = true
                handler.postDelayed(capture, 120L)
            }
        }
        val tileProvider = activeMap?.tileProvider
        val tileCompletionHandler = Handler(Looper.getMainLooper()) { requestCapture(); true }
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { requestCapture(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { requestCapture(); return false }
        }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> requestCapture() }
        if (enabled) {
            activeMap?.addMapListener(listener)
            activeMap?.addOnLayoutChangeListener(layoutListener)
            tileProvider?.tileRequestCompleteHandlers?.add(tileCompletionHandler)
            requestCapture()
        }
        onDispose {
            handler.removeCallbacks(capture)
            tileCompletionHandler.removeCallbacksAndMessages(null)
            tileProvider?.tileRequestCompleteHandlers?.remove(tileCompletionHandler)
            activeMap?.removeMapListener(listener)
            activeMap?.removeOnLayoutChangeListener(layoutListener)
        }
    }
    return backdrop
}

@Composable
fun GlassMapButton(
    description: String,
    backdrop: MapGlassBackdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.91f else 1f, spring(stiffness = 520f), label = "glass press")
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier.size(44.dp).semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(36.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(8.dp, shape, clip = false, ambientColor = Color(0x33213345), spotColor = Color(0x55213345))
                .clip(shape).onGloballyPositioned { bounds = it.boundsInWindow() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val background = backdrop
                if (background != null && bounds.width > 0 && background.bounds.width > 0) {
                    val sx = background.image.width / background.bounds.width
                    val sy = background.image.height / background.bounds.height
                    // A slight enlargement gives the glass a soft lens-like refraction.
                    val sourceWidth = (bounds.width * sx / 1.06f).roundToInt().coerceIn(1, background.image.width)
                    val sourceHeight = (bounds.height * sy / 1.06f).roundToInt().coerceIn(1, background.image.height)
                    val x = ((bounds.center.x - background.bounds.left) * sx - sourceWidth / 2f).roundToInt()
                        .coerceIn(0, background.image.width - sourceWidth)
                    val y = ((bounds.center.y - background.bounds.top) * sy - sourceHeight / 2f).roundToInt()
                        .coerceIn(0, background.image.height - sourceHeight)
                    drawImage(background.image, IntOffset(x, y), IntSize(sourceWidth, sourceHeight),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                }
                drawRect(Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.66f), Color.White.copy(alpha = 0.28f), Color(0xFFDDEFFF).copy(alpha = 0.48f)),
                    start = Offset.Zero, end = Offset(size.width, size.height)
                ))
                if (pressed) drawRect(Color.White.copy(alpha = 0.18f))
            }
            Box(Modifier.fillMaxSize().border(1.dp, Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.70f))
            ), shape))
            CompositionLocalProvider(LocalContentColor provides Color(0xFF18212D)) { content() }
        }
    }
}

/** A small sliding-window blur, also supported by the Android 11 emulator. */
private fun blurSmallBitmap(bitmap: Bitmap) {
    val width = bitmap.width
    val height = bitmap.height
    val source = IntArray(width * height)
    val horizontal = IntArray(source.size)
    val output = IntArray(source.size)
    bitmap.getPixels(source, 0, width, 0, 0, width, height)
    fun pass(input: IntArray, result: IntArray, rows: Int, cols: Int, vertical: Boolean) {
        fun index(row: Int, col: Int) = if (vertical) col * width + row else row * width + col
        for (row in 0 until rows) {
            var red = 0; var green = 0; var blue = 0
            fun add(col: Int, sign: Int) {
                val pixel = input[index(row, col.coerceIn(0, cols - 1))]
                red += ((pixel shr 16) and 255) * sign
                green += ((pixel shr 8) and 255) * sign
                blue += (pixel and 255) * sign
            }
            for (col in -2..2) add(col, 1)
            for (col in 0 until cols) {
                result[index(row, col)] = (255 shl 24) or ((red / 5) shl 16) or ((green / 5) shl 8) or (blue / 5)
                add(col - 2, -1); add(col + 3, 1)
            }
        }
    }
    pass(source, horizontal, height, width, false)
    pass(horizontal, output, width, height, true)
    bitmap.setPixels(output, 0, width, 0, 0, width, height)
}
