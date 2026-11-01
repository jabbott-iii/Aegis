package io.github.jabbottiii.aegis.logging

import android.util.Log
import io.github.jabbottiii.aegis.core.common.logging.LogLevel
import io.github.jabbottiii.aegis.core.common.logging.LogRecord
import io.github.jabbottiii.aegis.core.common.logging.LogSink

/**
 * The logging facade's only Android adapter, and the only file allowed to import
 * `android.util.Log` (config/detekt/detekt.yml). It writes what [LogRecord] already contains and
 * adds nothing: the policy has removed free text in release builds before a record gets here.
 */
internal class LogcatSink : LogSink {
    override fun write(record: LogRecord) {
        val line =
            buildString {
                append(record.event)
                if (record.values.isNotEmpty()) append(" values=").append(record.values.joinToString(","))
                record.errorType?.let { append(" error=").append(it) }
                record.debugText?.let { append('\n').append(it) }
            }
        when (record.level) {
            LogLevel.DEBUG -> Log.d(TAG, line)
            LogLevel.INFO -> Log.i(TAG, line)
            LogLevel.WARN -> Log.w(TAG, line)
            LogLevel.ERROR -> Log.e(TAG, line)
        }
    }

    private companion object {
        const val TAG = "Aegis"
    }
}
