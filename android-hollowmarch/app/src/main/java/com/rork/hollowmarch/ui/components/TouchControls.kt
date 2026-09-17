package com.rork.hollowmarch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.hollowmarch.ui.theme.Ink
import kotlin.math.hypot

/**
 * A fixed-base thumb stick. Drag anywhere inside it; the knob follows your finger
 * and reports [onChange] in -1..1 per axis (x right, y down). Release snaps it home.
 */
@Composable
fun TouchJoystick(
    label: String,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var knob by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                val radius = minOf(size.width, size.height) / 2f * 0.78f
                fun dragTo(pos: Offset) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    var offset = pos - center
                    val len = hypot(offset.x, offset.y)
                    if (len > radius) offset *= radius / len
                    knob = offset
                    onChange(offset.x / radius, offset.y / radius)
                }
                fun rest() {
                    knob = Offset.Zero
                    onChange(0f, 0f)
                }
                // Track exactly the pointer that landed on this stick. detectDragGestures
                // watches the first change in every event, so with both thumbs down the
                // second stick latches onto the wrong finger — a hand-rolled loop keeps
                // each stick (and the buttons) fully independent under multitouch.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    dragTo(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        dragTo(change.position)
                    }
                    rest()
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val rim = size.minDimension / 2f - 2.dp.toPx()
            drawCircle(color = Ink.Surface.copy(alpha = 0.6f), radius = rim, center = center)
            drawCircle(
                color = Ink.Hairline,
                radius = rim,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Ink.Hairline.copy(alpha = 0.55f),
                radius = rim * 0.45f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Ink.Parchment.copy(alpha = 0.9f),
                radius = rim * 0.3f,
                center = center + knob
            )
        }
        MonoText(
            label,
            color = Ink.Dim,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 9.dp)
        )
    }
}

/** A round carved hand-button: sword to swing, eye to use. */
@Composable
fun TouchActionButton(
    glyph: Glyph,
    label: String,
    diameter: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(if (enabled) Ink.Surface.copy(alpha = 0.9f) else Ink.Surface.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    color = if (enabled) Ink.Hairline else Ink.Hairline.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            WoodcutIcon(
                glyph = glyph,
                tint = if (enabled) Ink.Parchment else Ink.Dim,
                size = diameter * 0.42f
            )
        }
        Spacer(Modifier.height(4.dp))
        MonoText(label, color = Ink.Faded, fontSize = 8.sp)
    }
}
