package io.github.jabbottiii.aegis

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import io.github.jabbottiii.aegis.core.ui.R as CoreUiR

// Instrumented test names can't contain spaces: DEX allows them only from API 30, and minSdk is 29.
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsAppNameAndHonestStatus() {
        val activity = compose.activity
        compose.onNodeWithText(activity.getString(R.string.app_name)).assertIsDisplayed()
        // The shell runs no protection, so its status must read "Off", never a protected state.
        compose
            .onNodeWithText(activity.getString(CoreUiR.string.aegis_status_off), useUnmergedTree = true)
            .assertIsDisplayed()
        compose
            .onNodeWithText(activity.getString(R.string.shell_status_description), useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
