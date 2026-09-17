package com.rork.hollowmarch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.hollowmarch.ui.theme.Ink
import com.rork.hollowmarch.ui.theme.MonoStyle
import kotlin.math.cos
import kotlin.math.sin

enum class Glyph { SPELLBOOK, REST, MAP, TRANSPORT, JOURNAL, TORCH, SWORD, EYE, CROUCH, BAG, SHIELD }

/** Hand-inked square glyphs — the carved panel never uses flat Material icons. */
@Composable
fun WoodcutIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    tint: Color = Ink.Parchment,
    size: Dp = 22.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = (s * 0.075f).coerceAtLeast(1.4f)
        when (glyph) {
            Glyph.SPELLBOOK -> drawSpellbook(s, stroke, tint)
            Glyph.REST -> drawRest(s, stroke, tint)
            Glyph.MAP -> drawMap(s, stroke, tint)
            Glyph.TRANSPORT -> drawTransport(s, stroke, tint)
            Glyph.JOURNAL -> drawJournal(s, stroke, tint)
            Glyph.TORCH -> drawTorch(s, stroke, tint)
            Glyph.SWORD -> drawSword(s, stroke, tint)
            Glyph.EYE -> drawEye(s, stroke, tint)
            Glyph.CROUCH -> drawCrouch(s, stroke, tint)
            Glyph.BAG -> drawBag(s, stroke, tint)
            Glyph.SHIELD -> drawShield(s, stroke, tint)
        }
    }
}

private fun DrawScope.drawSpellbook(s: Float, stroke: Float, tint: Color) {
    val path = Path().apply {
        moveTo(s * 0.5f, s * 0.28f)
        cubicTo(s * 0.34f, s * 0.16f, s * 0.18f, s * 0.20f, s * 0.12f, s * 0.24f)
        lineTo(s * 0.12f, s * 0.78f)
        cubicTo(s * 0.22f, s * 0.72f, s * 0.38f, s * 0.72f, s * 0.5f, s * 0.82f)
        cubicTo(s * 0.62f, s * 0.72f, s * 0.78f, s * 0.72f, s * 0.88f, s * 0.78f)
        lineTo(s * 0.88f, s * 0.24f)
        cubicTo(s * 0.82f, s * 0.20f, s * 0.66f, s * 0.16f, s * 0.5f, s * 0.28f)
        close()
    }
    drawPath(path, tint, style = Stroke(width = stroke))
    drawLine(tint, Offset(s * 0.5f, s * 0.28f), Offset(s * 0.5f, s * 0.82f), stroke * 0.8f)
    drawLine(tint.copy(alpha = 0.7f), Offset(s * 0.24f, s * 0.42f), Offset(s * 0.40f, s * 0.42f), stroke * 0.6f)
    drawLine(tint.copy(alpha = 0.7f), Offset(s * 0.60f, s * 0.50f), Offset(s * 0.76f, s * 0.50f), stroke * 0.6f)
}

private fun DrawScope.drawRest(s: Float, stroke: Float, tint: Color) {
    val path = Path().apply {
        moveTo(s * 0.68f, s * 0.16f)
        cubicTo(s * 0.34f, s * 0.20f, s * 0.24f, s * 0.52f, s * 0.44f, s * 0.74f)
        cubicTo(s * 0.60f, s * 0.90f, s * 0.80f, s * 0.82f, s * 0.86f, s * 0.66f)
        cubicTo(s * 0.60f, s * 0.74f, s * 0.44f, s * 0.44f, s * 0.68f, s * 0.16f)
        close()
    }
    drawPath(path, tint, style = Stroke(width = stroke))
    drawCircle(tint.copy(alpha = 0.8f), radius = stroke * 0.7f, center = Offset(s * 0.26f, s * 0.22f))
}

private fun DrawScope.drawMap(s: Float, stroke: Float, tint: Color) {
    val path = Path().apply {
        moveTo(s * 0.12f, s * 0.26f)
        lineTo(s * 0.38f, s * 0.16f)
        lineTo(s * 0.64f, s * 0.30f)
        lineTo(s * 0.88f, s * 0.20f)
        lineTo(s * 0.88f, s * 0.74f)
        lineTo(s * 0.64f, s * 0.84f)
        lineTo(s * 0.38f, s * 0.70f)
        lineTo(s * 0.12f, s * 0.80f)
        close()
    }
    drawPath(path, tint, style = Stroke(width = stroke))
    drawLine(tint.copy(alpha = 0.65f), Offset(s * 0.38f, s * 0.16f), Offset(s * 0.38f, s * 0.70f), stroke * 0.6f)
    drawLine(tint.copy(alpha = 0.65f), Offset(s * 0.64f, s * 0.30f), Offset(s * 0.64f, s * 0.84f), stroke * 0.6f)
    drawCircle(Ink.Brass, radius = stroke, center = Offset(s * 0.52f, s * 0.52f))
}

private fun DrawScope.drawTransport(s: Float, stroke: Float, tint: Color) {
    drawCircle(tint, radius = s * 0.34f, center = Offset(s / 2, s / 2), style = Stroke(width = stroke))
    val star = Path().apply {
        moveTo(s * 0.5f, s * 0.14f)
        lineTo(s * 0.58f, s * 0.44f)
        lineTo(s * 0.86f, s * 0.5f)
        lineTo(s * 0.58f, s * 0.56f)
        lineTo(s * 0.5f, s * 0.86f)
        lineTo(s * 0.42f, s * 0.56f)
        lineTo(s * 0.14f, s * 0.5f)
        lineTo(s * 0.42f, s * 0.44f)
        close()
    }
    drawPath(star, tint.copy(alpha = 0.9f), style = Stroke(width = stroke * 0.8f))
}

private fun DrawScope.drawJournal(s: Float, stroke: Float, tint: Color) {
    drawRect(
        color = tint,
        topLeft = Offset(s * 0.2f, s * 0.14f),
        size = Size(s * 0.6f, s * 0.72f),
        style = Stroke(width = stroke)
    )
    drawLine(tint.copy(alpha = 0.7f), Offset(s * 0.3f, s * 0.14f), Offset(s * 0.3f, s * 0.86f), stroke * 0.7f)
    drawLine(Ink.Brass, Offset(s * 0.62f, s * 0.14f), Offset(s * 0.62f, s * 0.56f), stroke)
    drawLine(tint.copy(alpha = 0.6f), Offset(s * 0.40f, s * 0.36f), Offset(s * 0.70f, s * 0.36f), stroke * 0.55f)
    drawLine(tint.copy(alpha = 0.6f), Offset(s * 0.40f, s * 0.50f), Offset(s * 0.70f, s * 0.50f), stroke * 0.55f)
}

private fun DrawScope.drawTorch(s: Float, stroke: Float, tint: Color) {
    drawLine(tint, Offset(s * 0.5f, s * 0.92f), Offset(s * 0.5f, s * 0.46f), stroke * 1.6f, cap = StrokeCap.Butt)
    val flame = Path().apply {
        moveTo(s * 0.5f, s * 0.08f)
        cubicTo(s * 0.72f, s * 0.26f, s * 0.70f, s * 0.42f, s * 0.5f, s * 0.48f)
        cubicTo(s * 0.30f, s * 0.42f, s * 0.28f, s * 0.26f, s * 0.5f, s * 0.08f)
        close()
    }
    drawPath(flame, Ink.Brass, style = Stroke(width = stroke))
}

private fun DrawScope.drawSword(s: Float, stroke: Float, tint: Color) {
    drawLine(tint, Offset(s * 0.5f, s * 0.10f), Offset(s * 0.5f, s * 0.66f), stroke * 1.4f)
    drawLine(tint, Offset(s * 0.28f, s * 0.66f), Offset(s * 0.72f, s * 0.66f), stroke)
    drawLine(Ink.Brass, Offset(s * 0.5f, s * 0.66f), Offset(s * 0.5f, s * 0.88f), stroke * 1.2f)
}

private fun DrawScope.drawEye(s: Float, stroke: Float, tint: Color) {
    val path = Path().apply {
        moveTo(s * 0.1f, s * 0.5f)
        cubicTo(s * 0.3f, s * 0.2f, s * 0.7f, s * 0.2f, s * 0.9f, s * 0.5f)
        cubicTo(s * 0.7f, s * 0.8f, s * 0.3f, s * 0.8f, s * 0.1f, s * 0.5f)
        close()
    }
    drawPath(path, tint, style = Stroke(width = stroke))
    drawCircle(tint, radius = s * 0.12f, center = Offset(s * 0.5f, s * 0.5f))
}

/** A figure folded low, weight on its heels: the crouch. */
private fun DrawScope.drawCrouch(s: Float, stroke: Float, tint: Color) {
    val back = Path().apply {
        moveTo(s * 0.2f, s * 0.86f)
        cubicTo(s * 0.14f, s * 0.56f, s * 0.28f, s * 0.38f, s * 0.52f, s * 0.38f)
        cubicTo(s * 0.72f, s * 0.38f, s * 0.84f, s * 0.52f, s * 0.80f, s * 0.62f)
        lineTo(s * 0.62f, s * 0.62f)
    }
    drawPath(back, tint, style = Stroke(width = stroke))
    drawLine(tint, Offset(s * 0.2f, s * 0.86f), Offset(s * 0.46f, s * 0.86f), stroke)
    drawLine(tint, Offset(s * 0.62f, s * 0.62f), Offset(s * 0.56f, s * 0.86f), stroke)
    drawLine(tint, Offset(s * 0.56f, s * 0.86f), Offset(s * 0.82f, s * 0.86f), stroke)
    drawCircle(tint, radius = s * 0.09f, center = Offset(s * 0.26f, s * 0.30f))
}

/** A latched satchel: flap, clasp and the strap that carries it. */
private fun DrawScope.drawBag(s: Float, stroke: Float, tint: Color) {
    val strap = Path().apply {
        moveTo(s * 0.3f, s * 0.38f)
        cubicTo(s * 0.3f, s * 0.1f, s * 0.7f, s * 0.1f, s * 0.7f, s * 0.38f)
    }
    drawPath(strap, tint, style = Stroke(width = stroke * 0.8f))
    drawRect(
        color = tint,
        topLeft = Offset(s * 0.22f, s * 0.38f),
        size = Size(s * 0.56f, s * 0.44f),
        style = Stroke(width = stroke)
    )
    drawLine(tint.copy(alpha = 0.7f), Offset(s * 0.22f, s * 0.52f), Offset(s * 0.78f, s * 0.52f), stroke * 0.8f)
    drawCircle(Ink.Brass, radius = stroke, center = Offset(s * 0.5f, s * 0.58f))
}

/** A heater board, boss at the heart: the raised guard. */
private fun DrawScope.drawShield(s: Float, stroke: Float, tint: Color) {
    val path = Path().apply {
        moveTo(s * 0.2f, s * 0.16f)
        lineTo(s * 0.8f, s * 0.16f)
        cubicTo(s * 0.84f, s * 0.5f, s * 0.7f, s * 0.78f, s * 0.5f, s * 0.88f)
        cubicTo(s * 0.3f, s * 0.78f, s * 0.16f, s * 0.5f, s * 0.2f, s * 0.16f)
        close()
    }
    drawPath(path, tint, style = Stroke(width = stroke))
    drawCircle(Ink.Brass, radius = s * 0.07f, center = Offset(s * 0.5f, s * 0.36f))
}

/** The bust on the carved panel: it hollows out as you bleed. */
@Composable
fun Paperdoll(vitalityFraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val health = vitalityFraction.coerceIn(0f, 1f)
        val skin = Color(
            red = 0.62f - (1f - health) * 0.18f,
            green = 0.55f - (1f - health) * 0.24f,
            blue = 0.42f - (1f - health) * 0.18f
        )
        // shoulders
        val shoulders = Path().apply {
            moveTo(w * 0.06f, h)
            cubicTo(w * 0.14f, h * 0.62f, w * 0.86f, h * 0.62f, w * 0.94f, h)
            close()
        }
        drawPath(shoulders, skin.copy(alpha = 0.9f))
        drawPath(shoulders, Ink.Hairline, style = Stroke(width = 1.4f))
        // head
        drawOval(
            color = skin,
            topLeft = Offset(w * 0.30f, h * 0.10f),
            size = Size(w * 0.40f, h * 0.52f)
        )
        drawOval(
            color = Ink.Hairline,
            topLeft = Offset(w * 0.30f, h * 0.10f),
            size = Size(w * 0.40f, h * 0.52f),
            style = Stroke(width = 1.2f)
        )
        // hatching for hair and brow
        val hatch = Ink.Canvas.copy(alpha = 0.75f)
        for (i in 0 until 6) {
            val x = w * (0.32f + i * 0.07f)
            drawLine(hatch, Offset(x, h * 0.14f), Offset(x, h * 0.24f), 1.2f)
        }
        drawLine(hatch, Offset(w * 0.38f, h * 0.32f), Offset(w * 0.44f, h * 0.32f), 1.6f)
        drawLine(hatch, Offset(w * 0.56f, h * 0.32f), Offset(w * 0.62f, h * 0.32f), 1.6f)
        drawLine(hatch, Offset(w * 0.46f, h * 0.46f), Offset(w * 0.54f, h * 0.46f), 1.4f)
        if (health < 0.55f) {
            drawLine(Ink.Blood, Offset(w * 0.42f, h * 0.22f), Offset(w * 0.56f, h * 0.44f), 1.8f)
        }
        if (health < 0.3f) {
            drawLine(Ink.Blood, Offset(w * 0.36f, h * 0.52f), Offset(w * 0.60f, h * 0.58f), 1.8f)
        }
    }
}

/** A tick-marked apothecary vial, never a rounded pill meter. */
@Composable
fun Vial(
    fraction: Float,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val w = size.width
            val h = size.height
            val inset = w * 0.22f
            val bodyW = w - inset * 2
            drawRect(
                color = Ink.Canvas,
                topLeft = Offset(inset, 0f),
                size = Size(bodyW, h)
            )
            val fillH = h * fraction.coerceIn(0f, 1f)
            drawRect(
                color = color,
                topLeft = Offset(inset, h - fillH),
                size = Size(bodyW, fillH)
            )
            // dither band across the meniscus
            if (fillH > 2f) {
                drawRect(
                    color = color.copy(alpha = 0.45f),
                    topLeft = Offset(inset, h - fillH),
                    size = Size(bodyW, 2f)
                )
            }
            drawRect(
                color = Ink.Hairline,
                topLeft = Offset(inset, 0f),
                size = Size(bodyW, h),
                style = Stroke(width = 1.4f)
            )
            val ticks = 8
            for (i in 1 until ticks) {
                val y = h * i / ticks
                drawLine(
                    Ink.Hairline,
                    Offset(inset - w * 0.14f, y),
                    Offset(inset, y),
                    1.2f
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = MonoStyle.copy(
                    fontSize = 8.sp,
                    color = Ink.Faded,
                    letterSpacing = 1.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Hairline rule with a lozenge in the middle, the chronicle's paragraph break. */
@Composable
fun EngravedRule(modifier: Modifier = Modifier, color: Color = Ink.Hairline) {
    Canvas(modifier = modifier.fillMaxWidth().height(9.dp)) {
        val midY = size.height / 2
        val gap = size.width * 0.035f
        drawLine(color, Offset(0f, midY), Offset(size.width / 2 - gap, midY), 1f)
        drawLine(color, Offset(size.width / 2 + gap, midY), Offset(size.width, midY), 1f)
        val r = size.height * 0.28f
        val path = Path().apply {
            moveTo(size.width / 2, midY - r)
            lineTo(size.width / 2 + r, midY)
            lineTo(size.width / 2, midY + r)
            lineTo(size.width / 2 - r, midY)
            close()
        }
        drawPath(path, color)
    }
}

/** The compass reduced to a needle tick at the very top of the viewport. */
@Composable
fun CompassTick(headingDegrees: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val cx = size.width / 2
            val cy = size.height * 0.72f
            // drifting ticks so the strip reads as a real compass
            for (i in -6..6) {
                val offsetDeg = (headingDegrees % 15f)
                val x = cx + (i * 15f - offsetDeg) * (size.width / 220f)
                if (x < 0 || x > size.width) continue
                val major = i % 3 == 0
                drawLine(
                    color = if (major) Ink.Faded else Ink.Hairline,
                    start = Offset(x, cy - if (major) 6f else 3f),
                    end = Offset(x, cy),
                    strokeWidth = 1.1f
                )
            }
            val needle = Path().apply {
                moveTo(cx, cy - 12f)
                lineTo(cx + 4.5f, cy - 3f)
                lineTo(cx - 4.5f, cy - 3f)
                close()
            }
            drawPath(needle, Ink.Brass)
        }
    }
}

/** A small engraved medallion standing in for a faction seal. */
@Composable
fun PowerSeal(
    seed: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Ink.Overlay, radius = r, center = center)
        drawCircle(color.copy(alpha = 0.8f), radius = r - 1f, center = center, style = Stroke(width = 1.4f))
        val spokes = 5 + (seed % 4)
        for (i in 0 until spokes) {
            val a = (i / spokes.toFloat()) * 6.2832f + seed * 0.31f
            val inner = r * 0.28f
            val outer = r * (0.62f + ((seed + i) % 3) * 0.09f)
            drawLine(
                color = color.copy(alpha = 0.85f),
                start = Offset(center.x + cos(a) * inner, center.y + sin(a) * inner),
                end = Offset(center.x + cos(a) * outer, center.y + sin(a) * outer),
                strokeWidth = 1.6f
            )
        }
        drawCircle(color, radius = r * 0.16f, center = center)
    }
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.Parchment,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        modifier = modifier.padding(0.dp),
        style = MonoStyle.copy(color = color, fontSize = fontSize),
        textAlign = textAlign
    )
}
