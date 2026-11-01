package io.github.jabbottiii.aegis.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.github.jabbottiii.aegis.core.ui.theme.AegisTheme

@Composable
private fun StatusLevel.color(): Color =
    when (this) {
        StatusLevel.OK -> AegisTheme.statusColors.ok
        StatusLevel.REVIEW -> AegisTheme.statusColors.review
        StatusLevel.ACTION_NEEDED -> AegisTheme.statusColors.actionNeeded
        StatusLevel.OFF, StatusLevel.NOT_CHECKED -> AegisTheme.statusColors.inactive
    }

/**
 * An icon plus a text label. The label carries the meaning, so TalkBack reads it and the status
 * never depends on color alone (NFR-A11Y-01). The icon is decorative, and the row sets no merge
 * boundary of its own, so a parent that merges (such as [StatusCard]) reads the label as part of
 * its one announcement.
 */
@Composable
fun StatusIndicator(
    level: StatusLevel,
    modifier: Modifier = Modifier,
    label: String = stringResource(level.labelRes),
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(level.iconRes),
            contentDescription = null,
            tint = level.color(),
            modifier = Modifier.size(24.dp),
        )
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

/** A titled card with a status and a plain-language description. TalkBack reads all three together. */
@Composable
fun StatusCard(
    title: String,
    level: StatusLevel,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .padding(16.dp)
                    .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            StatusIndicator(level = level)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun StatusIndicatorPreview() {
    AegisTheme(dynamicColor = false) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusLevel.entries.forEach { StatusIndicator(level = it) }
            }
        }
    }
}

@PreviewLightDark
@Preview(name = "Font scale 200%", fontScale = 2f)
@Composable
private fun StatusCardPreview() {
    AegisTheme(dynamicColor = false) {
        StatusCard(
            title = "Scanner",
            level = StatusLevel.REVIEW,
            description = "One app needs a closer look.",
        )
    }
}
