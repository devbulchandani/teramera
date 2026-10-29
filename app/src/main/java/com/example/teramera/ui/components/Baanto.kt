package com.example.teramera.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.teramera.ui.theme.MascotCream

/**
 * Baanto — the teramera mascot. A coin split down the middle: teal for what
 * you paid ("mera"), violet for what you owe ("tere"). Quiet, geometric, and
 * drawn in Compose so it scales without a raster asset.
 *
 * Coordinates live in a 200×200 design space and are scaled to [size].
 */
enum class BaantoState { Idle, Pleased, Thinking, Sorry }

private const val CX = 100f
private const val CY = 105f
private const val R = 60f

@Composable
fun Baanto(
    state: BaantoState = BaantoState.Idle,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    withCoin: Boolean = true,
    animate: Boolean = false,
) {
    val teal = MaterialTheme.colorScheme.primary
    val violet = MaterialTheme.colorScheme.secondary
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val coinInk = MaterialTheme.colorScheme.onSurface
    val cream = MascotCream
    val ring = MaterialTheme.colorScheme.outlineVariant

    val body = remember { Path().apply { addOval(Rect(CX - R, CY - R, CX + R, CY + R)) } }
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    val sway = if (animate) {
        val transition = rememberInfiniteTransition(label = "baanto")
        transition.animateFloat(
            initialValue = -1.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "sway",
        ).value
    } else {
        0f
    }

    Canvas(modifier = modifier.size(size)) {
        val factor = this.size.minDimension / 200f
        scale(factor, factor, pivot = Offset.Zero) {
            rotate(sway, Offset(CX, CY)) {
                clipPath(body) {
                    drawCircle(teal, R, Offset(CX, CY))
                    rotate(-13f, Offset(CX, CY)) {
                        clipRect(left = 100f, top = -100f, right = 300f, bottom = 300f) {
                            drawCircle(violet, R, Offset(CX, CY))
                        }
                    }
                }
                drawCircle(cream.copy(alpha = 0.28f), R, Offset(CX, CY), style = Stroke(2f))

                drawFace(state, cream, ink)

                if (withCoin) drawCoin(cream, ring, coinInk, paint)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFace(
    state: BaantoState,
    cream: Color,
    ink: Color,
) {
    val eye = 6.5f
    when (state) {
        BaantoState.Idle -> {
            drawCircle(cream, eye, Offset(79f, 97f))
            drawCircle(cream, eye, Offset(121f, 97f))
            drawArc(
                color = cream, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(86f, 112f), size = Size(28f, 22f),
                style = Stroke(5f, cap = StrokeCap.Round),
            )
        }

        BaantoState.Pleased -> {
            drawArc(
                color = cream, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(72f, 90f), size = Size(14f, 14f),
                style = Stroke(5f, cap = StrokeCap.Round),
            )
            drawArc(
                color = cream, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(114f, 90f), size = Size(14f, 14f),
                style = Stroke(5f, cap = StrokeCap.Round),
            )
            drawArc(
                color = cream, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(82f, 108f), size = Size(36f, 26f),
                style = Stroke(5.5f, cap = StrokeCap.Round),
            )
        }

        BaantoState.Thinking -> {
            drawCircle(cream, eye, Offset(79f, 97f))
            drawArc(
                color = cream, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(114f, 90f), size = Size(14f, 14f),
                style = Stroke(5f, cap = StrokeCap.Round),
            )
            drawLine(cream, Offset(92f, 122f), Offset(108f, 122f), strokeWidth = 5f, cap = StrokeCap.Round)
            drawCircle(ink.copy(alpha = 0.55f), 4f, Offset(150f, 66f))
            drawCircle(ink.copy(alpha = 0.55f), 5.5f, Offset(160f, 56f))
            drawCircle(ink.copy(alpha = 0.55f), 7f, Offset(174f, 44f))
        }

        BaantoState.Sorry -> {
            drawArc(
                color = cream, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(72f, 90f), size = Size(14f, 14f),
                style = Stroke(5f, cap = StrokeCap.Round),
            )
            drawArc(
                color = cream, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(114f, 90f), size = Size(14f, 14f),
                style = Stroke(5f, cap = StrokeCap.Round),
            )
            drawArc(
                color = cream, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(86f, 118f), size = Size(28f, 18f),
                style = Stroke(5f, cap = StrokeCap.Round),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoin(
    cream: Color,
    ring: Color,
    ink: Color,
    paint: Paint,
) {
    drawCircle(cream, 17f, Offset(163f, 142f))
    drawCircle(ring, 17f, Offset(163f, 142f), style = Stroke(2.5f))
    paint.color = ink.toArgb()
    paint.textSize = 19f
    drawContext.canvas.nativeCanvas.drawText("₹", 163f, 149.5f, paint)
}
