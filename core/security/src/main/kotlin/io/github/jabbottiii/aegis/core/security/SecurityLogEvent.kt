package io.github.jabbottiii.aegis.core.security

import io.github.jabbottiii.aegis.core.common.logging.LogArea
import io.github.jabbottiii.aegis.core.common.logging.LogEvent

/** Log events from core:security. None of them carries key material or a file name. */
internal enum class SecurityLogEvent : LogEvent {
    SECRET_CREATED,
    SECRET_KEY_LOST,
    SECRET_UNREADABLE,
    SECRET_KEYSTORE_FAILED,
    SECRET_STORAGE_FAILED,
    STRONGBOX_KEY_FAILED,
    ;

    override val area: LogArea get() = LogArea.SECURITY
}
