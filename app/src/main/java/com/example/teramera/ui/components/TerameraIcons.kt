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
