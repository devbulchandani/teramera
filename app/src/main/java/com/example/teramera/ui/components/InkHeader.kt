package com.example.teramera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.teramera.ui.theme.Paper100

/** Organic-ish bottom edge used by every ink header in the app. */
val InkHeaderShape = RoundedCornerShape(bottomStart = 52.dp, bottomEnd = 44.dp)

/**
 * The one dark ground. Sits behind greeting/title rows, holds the mascot and
 * runs under the status bar. In dark mode inverseSurface lifts to Ink800.
 */
@Composable
fun InkHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(InkHeaderShape)
            .background(MaterialTheme.colorScheme.inverseSurface)
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 26.dp),
        content = content,
    )
}

/** 44dp round control on an ink header, cream glyph on a 12% cream fill. */
@Composable
fun HeaderIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Paper100.copy(alpha = if (enabled) 0.12f else 0f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
