package io.github.jabbottiii.aegis.core.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.jabbottiii.aegis.core.ui.R

/** The status of the device or of one protection module (FR-UX-02). */
enum class StatusLevel(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    OK(R.string.aegis_status_ok, R.drawable.aegis_ic_status_ok),
    REVIEW(R.string.aegis_status_review, R.drawable.aegis_ic_status_review),
    ACTION_NEEDED(R.string.aegis_status_action_needed, R.drawable.aegis_ic_status_action_needed),
    OFF(R.string.aegis_status_off, R.drawable.aegis_ic_status_off),
    NOT_CHECKED(R.string.aegis_status_not_checked, R.drawable.aegis_ic_status_not_checked),
}
