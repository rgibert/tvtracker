package ca.gibert.tvtracker.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The softer, glassier palette used across the app. Material's colour scheme
 * still carries the text and control colours; these tokens add the translucent
 * surfaces and iridescent highlights that sit on top of the Zen background.
 */
@Immutable
data class ZenColors(
    val dark: Boolean,
    val onAccent: Color,
    val glassBrush: Brush,
    val glassStrongBrush: Brush,
    val glassBorder: Color,
    val glassBorderStrong: Color,
    val glassShadow: Color,
    val backgroundBrush: Brush,
    val iridescentBrush: Brush,
    val logoBrush: Brush,
    val mutedFill: Color,
    val topSheen: Color,
    val auroraSky: Color,
    val auroraBlush: Color,
    val auroraPeach: Color,
    val auroraMint: Color,
)

val LightZenColors = ZenColors(
    dark = false,
    onAccent = Color(0xFF0B1012),
    glassBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.68f),
            Color.White.copy(alpha = 0.38f),
        ),
    ),
    glassStrongBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.84f),
            Color.White.copy(alpha = 0.56f),
        ),
    ),
    glassBorder = Color.White.copy(alpha = 0.72f),
    glassBorderStrong = Color.White.copy(alpha = 0.9f),
    glassShadow = Color(0xFF6A8793).copy(alpha = 0.20f),
    backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFFF5F9FA),
            Color(0xFFEAF2F4),
            Color(0xFFF1F4F4),
        ),
    ),
    iridescentBrush = Brush.linearGradient(
        listOf(
            Color(0xFFD7EDF4),
            Color(0xFFE7D9ED),
            Color(0xFFF3DCCE),
            Color(0xFFD4ECE2),
        ),
    ),
    logoBrush = Brush.linearGradient(
        listOf(
            Color(0xFFB9DFEB),
            Color(0xFFE1CEE9),
            Color(0xFFF2D3C1),
        ),
    ),
    mutedFill = Color(0xFFE5EEF1).copy(alpha = 0.72f),
    topSheen = Color.White.copy(alpha = 0.34f),
    auroraSky = Color(0xFFA9D6E4).copy(alpha = 0.48f),
    auroraBlush = Color(0xFFD9BBD7).copy(alpha = 0.34f),
    auroraPeach = Color(0xFFF1CBB9).copy(alpha = 0.36f),
    auroraMint = Color(0xFFB9E0D3).copy(alpha = 0.32f),
)

val DarkZenColors = ZenColors(
    dark = true,
    onAccent = Color(0xFFF1F8FA),
    glassBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.035f),
        ),
    ),
    glassStrongBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.06f),
        ),
    ),
    glassBorder = Color.White.copy(alpha = 0.15f),
    glassBorderStrong = Color.White.copy(alpha = 0.24f),
    glassShadow = Color.Black.copy(alpha = 0.45f),
    backgroundBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF0A1114),
            Color(0xFF0E171B),
            Color(0xFF0A1215),
        ),
    ),
    iridescentBrush = Brush.linearGradient(
        listOf(
            Color(0xFF234A57),
            Color(0xFF3C2C45),
            Color(0xFF4B352B),
            Color(0xFF234139),
        ),
    ),
    logoBrush = Brush.linearGradient(
        listOf(
            Color(0xFF3C6F7C),
            Color(0xFF60496A),
            Color(0xFF765544),
        ),
    ),
    mutedFill = Color.White.copy(alpha = 0.07f),
    topSheen = Color.White.copy(alpha = 0.08f),
    auroraSky = Color(0xFF1C5365).copy(alpha = 0.42f),
    auroraBlush = Color(0xFF4A2D49).copy(alpha = 0.30f),
    auroraPeach = Color(0xFF5A382A).copy(alpha = 0.26f),
    auroraMint = Color(0xFF1E4B40).copy(alpha = 0.30f),
)

val LocalZenColors = staticCompositionLocalOf { LightZenColors }

@Composable
fun ZenBackground(modifier: Modifier = Modifier) {
    val zen = LocalZenColors.current
    Canvas(modifier.fillMaxSize()) {
        drawRect(brush = zen.backgroundBrush)

        fun blob(center: Offset, radius: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }

        blob(
            center = Offset(size.width * 0.92f, size.height * 0.04f),
            radius = size.width * 0.64f,
            color = zen.auroraSky,
        )
        blob(
            center = Offset(size.width * 0.02f, size.height * 0.38f),
            radius = size.width * 0.56f,
            color = zen.auroraBlush,
        )
        blob(
            center = Offset(size.width * 0.76f, size.height * 0.74f),
            radius = size.width * 0.62f,
            color = zen.auroraPeach,
        )
        blob(
            center = Offset(size.width * 0.12f, size.height * 0.97f),
            radius = size.width * 0.58f,
            color = zen.auroraMint,
        )

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(zen.topSheen, Color.Transparent),
                endY = size.height * 0.34f,
            ),
        )
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    brush: Brush? = null,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val zen = LocalZenColors.current
    val surface = modifier
        .then(
            if (zen.dark) {
                Modifier
            } else {
                Modifier.shadow(
                    elevation = if (strong) 18.dp else 12.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = zen.glassShadow,
                    spotColor = zen.glassShadow,
                )
            },
        )
        .clip(shape)
        .background(brush ?: if (strong) zen.glassStrongBrush else zen.glassBrush)
        .border(
            width = 1.dp,
            color = if (strong) zen.glassBorderStrong else zen.glassBorder,
            shape = shape,
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        )

    Box(surface) {
        if (brush != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                zen.topSheen,
                                Color.Transparent,
                                Color.White.copy(alpha = if (zen.dark) 0.015f else 0.10f),
                            ),
                        ),
                    ),
            )
        }
        content()
    }
}

@Composable
fun ZenIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    GlassSurface(
        modifier = modifier
            .size(42.dp)
            .alpha(if (enabled) 1f else 0.55f),
        shape = CircleShape,
        strong = true,
        onClick = if (enabled) onClick else null,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
