package io.github.jabbottiii.aegis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.github.jabbottiii.aegis.R
import io.github.jabbottiii.aegis.core.ui.component.StatusCard
import io.github.jabbottiii.aegis.core.ui.component.StatusLevel
import io.github.jabbottiii.aegis.core.ui.theme.AegisTheme

/**
 * The Phase 0 app shell. It states plainly that no protection runs yet. The dashboard
 * (feature:dashboard) replaces it in Phase 1.
 */
@Composable
fun AegisApp(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            StatusCard(
                title = stringResource(R.string.shell_status_title),
                level = StatusLevel.OFF,
                description = stringResource(R.string.shell_status_description),
            )
        }
    }
}

@PreviewLightDark
@Preview(name = "Font scale 200%", fontScale = 2f)
@Composable
private fun AegisAppPreview() {
    AegisTheme(dynamicColor = false) {
        AegisApp()
    }
}
