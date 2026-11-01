package io.github.jabbottiii.aegis.testing.common

import io.github.jabbottiii.aegis.core.common.logging.LogRecord
import io.github.jabbottiii.aegis.core.common.logging.LogSink
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Collects log records so tests can assert that nothing sensitive was logged. Safe to write from
 * several threads.
 */
class RecordingLogSink : LogSink {
    private val written = CopyOnWriteArrayList<LogRecord>()

    val records: List<LogRecord> get() = written.toList()

    override fun write(record: LogRecord) {
        written += record
    }
}
