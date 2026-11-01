package io.github.jabbottiii.aegis.core.security.secret

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

/**
 * Flushes [directory]'s entries to disk, so that a rename into it survives a power cut. Java has no
 * API for this, so it calls `fsync` on the directory itself.
 */
internal fun fsyncDirectory(directory: File) {
    try {
        // Linux opens a directory read-only like a file. OsConstants has no O_DIRECTORY.
        val fd = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(fd)
        } finally {
            Os.close(fd)
        }
    } catch (e: ErrnoException) {
        throw IOException(e)
    }
}
