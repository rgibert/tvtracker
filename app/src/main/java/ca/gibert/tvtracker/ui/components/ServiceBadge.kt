package ca.gibert.tvtracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.gibert.tvtracker.R

/**
 * Maps Trakt's [ca.gibert.tvtracker.data.Show.network] onto a bundled brand mark.
 *
 * Trakt has no public "where to watch" endpoint — /watchnow exists but is
 * limited access and answers 403 for this client — so `network`, which we
 * already request with extended=full, is the service information available.
 * Networks without a bundled logo fall back to a generic tile carrying the
 * name, so the line still reads correctly.
 */
internal fun serviceLogoFor(network: String?): Int? {
    val key = network?.trim()?.lowercase().orEmpty()
    if (key.isEmpty()) return null

    return when {
        // "max" first so "HBO Max" resolves to Max rather than HBO.
        "max" in key -> R.drawable.service_max
        // Plain "apple" too, so a hand-typed "Apple" still gets the mark.
        "apple" in key -> R.drawable.service_appletv
        "netflix" in key -> R.drawable.service_netflix
        "disney" in key -> R.drawable.service_disney
        // Before "hulu" so "FX on Hulu" credits the network, not the platform.
        "fx" in key -> R.drawable.service_fx
        "fox" in key -> R.drawable.service_fox
        "hulu" in key -> R.drawable.service_hulu
        "amazon" in key || "prime video" in key -> R.drawable.service_prime_video
        "paramount" in key -> R.drawable.service_paramount_plus
        "hbo" in key -> R.drawable.service_hbo
        "showtime" in key -> R.drawable.service_showtime
        "starz" in key -> R.drawable.service_starz
        "bbc" in key -> R.drawable.service_bbc
        "amc" in key -> R.drawable.service_amc
        "cbs" in key -> R.drawable.service_cbs
        "nbc" in key -> R.drawable.service_nbc
        "discovery" in key -> R.drawable.service_discovery
        "crunchyroll" in key -> R.drawable.service_crunchyroll
        "roku" in key -> R.drawable.service_roku
        "tubi" in key -> R.drawable.service_tubi
        "plex" in key -> R.drawable.service_plex
        "mubi" in key -> R.drawable.service_mubi
        else -> null
    }
}

/**
 * Channel names offered as one-tap picks when pinning a show's channel.
 *
 * Every entry resolves to a bundled logo through [serviceLogoFor] (a unit test
 * holds this), so a pick always gets a proper badge; anything else can still be
 * typed in by hand.
 */
internal val suggestedServices: List<String> = listOf(
    "HBO",
    "Max",
    "Netflix",
    "Apple TV",
    "Prime Video",
    "Plex",
    "Disney+",
    "Hulu",
    "Paramount+",
    "FX",
    "AMC",
    "Showtime",
    "Starz",
    "BBC",
    "CBS",
    "NBC",
    "Fox",
    "Discovery",
    "Crunchyroll",
    "Tubi",
    "Roku",
    "MUBI",
)

/**
 * A brand tile for a streaming service.
 *
 * The tile is white rather than themed because most marks are dark: an
 * unthemed canvas keeps Netflix red, Hulu green and the black wordmarks all
 * legible against the deep blue cards.
 */
@Composable
fun ServiceBadge(
    network: String,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    val shape = RoundedCornerShape(percent = 28)
    val logo = serviceLogoFor(network)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color.White)
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.12f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                painter = painterResource(id = logo),
                // The service name sits beside the tile, so a description here
                // would only make TalkBack say it twice.
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.22f),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.LiveTv,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.40f),
                modifier = Modifier.size(size * 0.56f),
            )
        }
    }
}

/**
 * `service badge + name`, or just the name when there is no service to show.
 *
 * [prefix] keeps the year ahead of the network on watchlist cards, and
 * [fallback] preserves the placeholder those cards used when neither the year
 * nor the network is known.
 */
@Composable
fun ServiceLine(
    network: String?,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    fallback: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val service = network?.trim().orEmpty()

    if (service.isEmpty()) {
        val text = listOfNotNull(prefix).joinToString(" · ").ifBlank { fallback.orEmpty() }
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier,
            )
        }
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServiceBadge(network = service)
        Spacer(Modifier.width(7.dp))
        Text(
            text = listOfNotNull(prefix, service).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // fill = false keeps short names hugging the badge instead of
            // stretching across the card, while still bounding long ones so
            // they ellipsize rather than pushing the trailing controls.
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
