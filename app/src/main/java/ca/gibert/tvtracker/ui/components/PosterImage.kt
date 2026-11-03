package ca.gibert.tvtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.gibert.tvtracker.data.Show
import coil.compose.AsyncImage

@Composable
fun PosterImage(
    show: Show?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val url = show?.images?.poster?.firstOrNull()
        ?: show?.images?.thumb?.firstOrNull()
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.surfaceContainerHighest,
                        colors.surfaceContainerHigh,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.LiveTv,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(28.dp),
        )

        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl(url),
                contentDescription = show?.title?.let { "Poster for $it" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun imageUrl(url: String): String =
    if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        "https://walter.trakt.tv$url"
    }
