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
        // Two Baanto-style coins: the back one peeks out from behind the front.
        drawCircle(tint, radius = 6.5f * s, center = c(9f, 11.5f), style = Stroke(w, cap = StrokeCap.Round))
        drawCircle(tint, radius = 7.5f * s, center = c(14.5f, 13f), style = Stroke(w, cap = StrokeCap.Round))
        drawCircle(tint, radius = 1.2f * s, center = c(12f, 11.5f))
        drawCircle(tint, radius = 1.2f * s, center = c(17f, 11.5f))
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = c(12.5f, 13f),
            size = Size(4f * s, 3f * s),
            style = Stroke(1.5f * s, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun ActivityIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val pulse = Path().apply {
            moveTo(3f * s, 12f * s)
            lineTo(6.5f * s, 12f * s)
            lineTo(9f * s, 5.5f * s)
            lineTo(13.5f * s, 18.5f * s)
            lineTo(16.5f * s, 12f * s)
            lineTo(21f * s, 12f * s)
        }
        drawPath(pulse, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun SettleIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        drawLine(tint, p(4f, 8f), p(19f, 8f), w, StrokeCap.Round)
        drawLine(tint, p(16f, 5f), p(19f, 8f), w, StrokeCap.Round)
        drawLine(tint, p(19f, 8f), p(16f, 11f), w, StrokeCap.Round)
        drawLine(tint, p(20f, 16f), p(5f, 16f), w, StrokeCap.Round)
        drawLine(tint, p(8f, 13f), p(5f, 16f), w, StrokeCap.Round)
        drawLine(tint, p(5f, 16f), p(8f, 19f), w, StrokeCap.Round)
    }
}

@Composable
fun ArrowBackIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        drawLine(tint, p(15f, 5f), p(8f, 12f), w, StrokeCap.Round)
        drawLine(tint, p(8f, 12f), p(15f, 19f), w, StrokeCap.Round)
    }
}

@Composable
fun ChevronRightIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        drawLine(tint, p(9f, 5f), p(16f, 12f), w, StrokeCap.Round)
        drawLine(tint, p(16f, 12f), p(9f, 19f), w, StrokeCap.Round)
    }
}

@Composable
fun CloseIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        drawLine(tint, p(6f, 6f), p(18f, 18f), w, StrokeCap.Round)
        drawLine(tint, p(18f, 6f), p(6f, 18f), w, StrokeCap.Round)
    }
}

@Composable
fun PlusIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        drawLine(tint, p(12f, 5f), p(12f, 19f), w, StrokeCap.Round)
        drawLine(tint, p(5f, 12f), p(19f, 12f), w, StrokeCap.Round)
    }
}

@Composable
fun CheckIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 2f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        drawLine(tint, p(5f, 12.5f), p(9.5f, 17f), w, StrokeCap.Round)
        drawLine(tint, p(9.5f, 17f), p(19f, 7f), w, StrokeCap.Round)
    }
}

@Composable
fun ShareIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        drawLine(tint, p(12f, 16f), p(12f, 4f), w, StrokeCap.Round)
        drawLine(tint, p(8f, 8f), p(12f, 4f), w, StrokeCap.Round)
        drawLine(tint, p(12f, 4f), p(16f, 8f), w, StrokeCap.Round)
        val box = Path().apply {
            moveTo(5f * s, 14f * s)
            lineTo(5f * s, 20f * s)
            lineTo(19f * s, 20f * s)
            lineTo(19f * s, 14f * s)
        }
        drawPath(box, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun BackspaceIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val outline = Path().apply {
            moveTo(20f * s, 6f * s)
            lineTo(9f * s, 6f * s)
            lineTo(4f * s, 12f * s)
            lineTo(9f * s, 18f * s)
            lineTo(20f * s, 18f * s)
            close()
        }
        drawPath(outline, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tint, p(17f, 9f), p(13f, 15f), w, StrokeCap.Round)
        drawLine(tint, p(13f, 9f), p(17f, 15f), w, StrokeCap.Round)
    }
}

@Composable
fun PowerIcon(modifier: Modifier = Modifier, tint: Color, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val w = 1.75f * s
        drawLine(tint, Offset(12f * s, 3f * s), Offset(12f * s, 11f * s), w, StrokeCap.Round)
        drawArc(
            color = tint,
            startAngle = -50f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(4f * s, 5f * s),
            size = Size(16f * s, 16f * s),
            style = Stroke(w, cap = StrokeCap.Round),
        )
    }
}
