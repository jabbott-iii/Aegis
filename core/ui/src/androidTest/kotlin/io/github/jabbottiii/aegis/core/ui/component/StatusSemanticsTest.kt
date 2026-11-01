package io.github.jabbottiii.aegis.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jabbottiii.aegis.core.ui.theme.AegisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Semantic tests for TESTING §1 (UI modules) and NFR-A11Y-01. Instrumented test names can't
// contain spaces: DEX allows them only from API 30, and minSdk is 29.
@RunWith(AndroidJUnit4::class)
class StatusSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun label(level: StatusLevel): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(level.labelRes)

    @Test
    fun everyStatusLevelShowsItsTextLabel() {
        compose.setContent {
            AegisTheme(dynamicColor = false) {
                Column {
                    StatusLevel.entries.forEach { StatusIndicator(level = it) }
                }
            }
        }
        StatusLevel.entries.forEach { compose.onNodeWithText(label(it)).assertIsDisplayed() }
    }

    @Test
    fun statusCardIsOneTalkBackNodeWithTitleStatusAndDescription() {
        compose.setContent {
            AegisTheme(dynamicColor = false) {
                StatusCard(title = "Scanner", level = StatusLevel.REVIEW, description = "One app needs a look.")
            }
        }
        val merged = compose.onNodeWithText("Scanner").fetchSemanticsNode()
        val texts = merged.config.getOrNull(SemanticsProperties.Text)?.map { it.text }
        assertEquals(listOf("Scanner", label(StatusLevel.REVIEW), "One app needs a look."), texts)
    }
}
