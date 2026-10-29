package com.example.teramera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.teramera.ui.theme.Butter
import com.example.teramera.ui.theme.Mint
import com.example.teramera.ui.theme.Peach
import kotlin.math.abs

/**
 * Groups get a mascot, not a generic initials tile. The halo and Baanto face
 * rotate through the three brand accents so groups stay easy to tell apart.
 */
@Composable
fun GroupMascot(
    index: Int,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val tone = when (abs(index) % 3) {
        0 -> Peach
        1 -> Mint
        else -> Butter
    }
    val state = if (abs(index) % 2 == 0) BaantoState.Idle else BaantoState.Pleased
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center,
    ) {
        Baanto(state = state, size = size * 0.84f, withCoin = false)
    }
}
