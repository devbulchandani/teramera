package com.example.teramera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun HomeIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val roof = Path().apply {
            moveTo(3f, 10.5f)
            lineTo(12f, 3f)
            lineTo(21f, 10.5f)
            lineTo(21f, 21f)
            lineTo(3f, 21f)
            close()
        }
        drawPath(roof, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tint, p(9f, 21f), p(9f, 15f), w, StrokeCap.Round)
        drawLine(tint, p(9f, 15f), p(15f, 15f), w, StrokeCap.Round)
        drawLine(tint, p(15f, 15f), p(15f, 21f), w, StrokeCap.Round)
    }
}

@Composable
fun GroupsIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun c(x: Float, y: Float) = Offset(x * s, y * s)
        drawCircle(tint, radius = 3.5f * s, center = c(9f, 8f), style = Stroke(w, cap = StrokeCap.Round))
        val shoulders = Path().apply {
            moveTo(2.5f, 20f)
            cubicTo(2.5f, 16.4f, 5.4f, 14f, 9f, 14f)
            cubicTo(12.6f, 14f, 15.5f, 16.4f, 15.5f, 20f)
        }
        drawPath(shoulders, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(tint, radius = 2.5f * s, center = c(17f, 9f), style = Stroke(w, cap = StrokeCap.Round))
        val shoulder2 = Path().apply {
            moveTo(17f, 14f)
            cubicTo(19.5f, 14f, 21.5f, 16f, 21.5f, 18.5f)
        }
        drawPath(shoulder2, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun ActivityIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun c(x: Float, y: Float) = Offset(x * s, y * s)
        drawCircle(tint, radius = 9f * s, center = c(12f, 12f), style = Stroke(w, cap = StrokeCap.Round))
        drawLine(tint, c(12f, 7f), c(12f, 12f), w, StrokeCap.Round)
        drawLine(tint, c(12f, 12f), c(15f, 15f), w, StrokeCap.Round)
    }
}

@Composable
fun SettleIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    3f * s, 10f * s, 21f * s, 17f * s,
                    topLeftCornerRadius = CornerRadius(3f * s),
                    topRightCornerRadius = CornerRadius(3f * s),
                    bottomRightCornerRadius = CornerRadius(3f * s),
                    bottomLeftCornerRadius = CornerRadius(3f * s),
                )
            )
        }
        drawPath(body, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tint, p(7f, 13.5f), p(7.6f, 13.5f), w, StrokeCap.Round)
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = p(9.5f, 7f),
            size = Size(5f * s, 6f * s),
            style = Stroke(w, cap = StrokeCap.Round),
        )
    }
}
