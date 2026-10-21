package com.virtualrun.app.ui

import android.view.WindowManager
import android.view.Gravity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** Keeps modal content mounted through its exit animation, including nested previews. */
@Composable
fun MotionDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    islandStyle: Boolean = false,
    content: @Composable () -> Unit
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    val dim = animateFloatAsState(if (visible) (if (islandStyle) 0.22f else 0.32f) else 0f, tween(220), label = "modal dim")
    if (visible || state.currentState || !state.isIdle) {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            DisposableEffect(window, islandStyle) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window?.setWindowAnimations(0)
                // Older Compose dialogs measure to the app's usable height but center that
                // window on the whole display. Top gravity keeps the island aligned on API 30.
                if (islandStyle) window?.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                onDispose { }
            }
            Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = if (islandStyle) Alignment.TopCenter else Alignment.Center) {
                Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = dim.value)) }.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = onDismissRequest
                ))
                AnimatedVisibility(
                    visibleState = state,
                    enter = if (islandStyle) {
                        fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.9f, stiffness = 360f),
                            initialScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 0f)) +
                            expandVertically(spring(dampingRatio = 0.9f, stiffness = 360f), expandFrom = Alignment.Top)
                    } else fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.9f, stiffness = 360f),
                        initialScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                        slideInVertically(spring(dampingRatio = 0.9f, stiffness = 360f)) { it / 8 },
                    exit = if (islandStyle) {
                        fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.96f,
                            transformOrigin = TransformOrigin(0.5f, 0f)) + shrinkVertically(tween(220), shrinkTowards = Alignment.Top)
                    } else fadeOut(tween(130)) + scaleOut(tween(180), targetScale = 0.98f,
                        transformOrigin = TransformOrigin(0.5f, 1f)) + slideOutVertically(tween(200)) { it / 10 }
                ) {
                    Box(Modifier.widthIn(max = if (islandStyle) 452.dp else 560.dp).fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = if (islandStyle) 12.dp else 16.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}
                    )) {
                        MaterialTheme(colorScheme = IslandDialogColors) { content() }
                    }
                }
            }
        }
    }
}

@Composable
fun MotionAlertDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    islandStyle: Boolean = false
) {
    MotionDialog(visible, onDismissRequest, islandStyle) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 12.dp) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.9f).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ProvideTextStyle(if (islandStyle) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge) { title() }
                Box(Modifier.weight(1f, fill = false)) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    dismissButton?.invoke()
                    Spacer(Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}
