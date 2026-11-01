package io.github.jabbottiii.aegis.core.data

import io.github.jabbottiii.aegis.core.common.logging.LogArea
import io.github.jabbottiii.aegis.core.common.logging.LogEvent

/** Log events from core:data. None of them names a database, a file, or a setting. */
internal enum class DataLogEvent : LogEvent {
    DATABASE_UNREADABLE,
    DATABASE_KEY_REPLACED,
    DATABASE_OPEN_FAILED,
    DATABASE_NOT_CLOSED,
    SETTINGS_RESET_AFTER_CORRUPTION,
    ;

    override val area: LogArea get() = LogArea.DATA
}
