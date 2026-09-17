package com.rork.hollowmarch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.hollowmarch.game.GameMap
import com.rork.hollowmarch.ui.theme.Ink
import kotlin.math.cos
import kotlin.math.sin

/**
 * The old wireframe automap: only what you have walked is drawn, in verdigris
 * line-work over black, with a brass mark for where you stand.
 */
@Composable
fun AutomapPanel(
    map: GameMap,
    playerX: Float,
    playerY: Float,
    playerAngle: Float,
    exploredPercent: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Ink.Overlay.copy(alpha = 0.94f))
            .border(1.dp, Ink.Hairline)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoText(map.title.uppercase(), color = Ink.Faded, fontSize = 10.sp)
            MonoText("$exploredPercent% walked", color = Ink.Verdigris, fontSize = 10.sp)
        }
        EngravedRule(Modifier.padding(vertical = 8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Ink.Canvas)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.minDimension / map.width
                fun walkable(x: Int, y: Int): Boolean =
                    x in 0 until map.width && y in 0 until map.height &&
                        map.walls[y * map.width + x] == 0 &&
                        map.explored[y * map.width + x]

                for (y in 0 until map.height) {
                    for (x in 0 until map.width) {
                        if (!walkable(x, y)) continue
                        val left = x * cell
                        val top = y * cell
                        drawRect(
                            color = Ink.Verdigris.copy(alpha = 0.13f),
                            topLeft = Offset(left, top),
                            size = Size(cell, cell)
                        )
                        // draw the wall edges only, so it reads as a wireframe
                        if (!walkable(x, y - 1)) {
                            drawLine(Ink.Verdigris, Offset(left, top), Offset(left + cell, top), 1.4f)
                        }
                        if (!walkable(x, y + 1)) {
                            drawLine(Ink.Verdigris, Offset(left, top + cell), Offset(left + cell, top + cell), 1.4f)
                        }
                        if (!walkable(x - 1, y)) {
                            drawLine(Ink.Verdigris, Offset(left, top), Offset(left, top + cell), 1.4f)
                        }
                        if (!walkable(x + 1, y)) {
                            drawLine(Ink.Verdigris, Offset(left + cell, top), Offset(left + cell, top + cell), 1.4f)
                        }
                    }
                }

                // the ways through — stairs up in verdigris, ways down in brass — once found
                map.portals.forEach { portal ->
                    val tx = portal.x.toInt().coerceIn(0, map.width - 1)
                    val ty = portal.y.toInt().coerceIn(0, map.height - 1)
                    if (map.explored[ty * map.width + tx]) {
                        val ink = if (portal.down) Ink.Brass else Ink.Verdigris
                        drawCircle(
                            ink.copy(alpha = 0.5f),
                            radius = cell * 1.4f,
                            center = Offset(portal.x * cell, portal.y * cell)
                        )
                    }
                }

                // player mark
                val cx = playerX * cell
                val cy = playerY * cell
                val r = cell * 1.9f
                val arrow = Path().apply {
                    moveTo(cx + cos(playerAngle) * r, cy + sin(playerAngle) * r)
                    lineTo(
                        cx + cos(playerAngle + 2.5f) * r * 0.7f,
                        cy + sin(playerAngle + 2.5f) * r * 0.7f
                    )
                    lineTo(
                        cx + cos(playerAngle - 2.5f) * r * 0.7f,
                        cy + sin(playerAngle - 2.5f) * r * 0.7f
                    )
                    close()
                }
                drawPath(arrow, Ink.Brass)
            }
        }
        EngravedRule(Modifier.padding(top = 8.dp))
        MonoText(
            "Tap anywhere to close",
            modifier = Modifier.padding(top = 6.dp),
            color = Ink.Dim,
            fontSize = 10.sp
        )
    }
}
