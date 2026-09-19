package com.example.data.storage

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object MediaValidator {
    private const val TAG = "MediaValidator"

    enum class MediaType(val extension: String, val mimeType: String, val isVideo: Boolean) {
        MP4("mp4", "video/mp4", true),
        WEBM("webm", "video/webm", true),
        JPEG("jpg", "image/jpeg", false),
        PNG("png", "image/png", false),
        WEBP("webp", "image/webp", false),
        GIF("gif", "image/gif", false),
        UNKNOWN("", "", false)
    }

    sealed class ValidationResult {
        data class Valid(val mediaType: MediaType, val sizeBytes: Long) : ValidationResult()
        data class Invalid(val reason: String, val previewSnippet: String = "") : ValidationResult()
    }

    /**
     * Inspects header bytes of a downloaded file to identify its true media format
     * and verify that it is not an error payload (HTML/JSON/XML) or corrupt file.
     */
    fun validateMediaFile(
        file: File,
        expectedVideo: Boolean,
        expectedContentLength: Long = -1L
    ): ValidationResult {
        if (!file.exists()) {
            return ValidationResult.Invalid("File does not exist: ${file.absolutePath}")
        }

        val fileLength = file.length()
        if (fileLength == 0L) {
            return ValidationResult.Invalid("File is completely empty (0 bytes).")
        }

        // Check against expected content length if supplied by the HTTP server
        if (expectedContentLength > 0 && fileLength < expectedContentLength) {
            return ValidationResult.Invalid(
                "Truncated download: received $fileLength bytes but Content-Length declared $expectedContentLength bytes."
            )
        }

        // Minimum sensible size checks
        if (expectedVideo && fileLength < 10_240L) { // Less than 10KB cannot be a valid TikTok video
            val preview = readPreviewSnippet(file, 256)
            return ValidationResult.Invalid(
                "Video file is unnaturally small ($fileLength bytes). Probable error response.",
                preview
            )
        } else if (!expectedVideo && fileLength < 512L) { // Less than 512 bytes cannot be a valid image
            val preview = readPreviewSnippet(file, 256)
            return ValidationResult.Invalid(
                "Image file is unnaturally small ($fileLength bytes). Probable error response.",
                preview
            )
        }

        val header = ByteArray(64)
        val bytesRead = try {
            FileInputStream(file).use { it.read(header) }
        } catch (e: IOException) {
            return ValidationResult.Invalid("Cannot read file header: ${e.localizedMessage}")
        }

        if (bytesRead < 8) {
            return ValidationResult.Invalid("File too short to verify signature ($bytesRead bytes read).")
        }

        // Check for text/HTML/JSON error payloads
        val preview = readPreviewSnippet(file, 128)
        val lowerPreview = preview.lowercase()
        if (lowerPreview.startsWith("<!doctype") ||
            lowerPreview.startsWith("<html") ||
            lowerPreview.startsWith("<?xml") ||
            lowerPreview.startsWith("{\"code\"") ||
            lowerPreview.startsWith("{\"msg\"") ||
            lowerPreview.startsWith("{\"error\"") ||
            lowerPreview.contains("access denied") ||
            lowerPreview.contains("404 not found") ||
            lowerPreview.contains("rate limit")
        ) {
            return ValidationResult.Invalid(
                "Server returned text/HTML/JSON error instead of media: $preview",
                preview
            )
        }

        val detectedType = detectMediaType(header, bytesRead)

        if (expectedVideo) {
            if (detectedType == MediaType.MP4 || detectedType == MediaType.WEBM) {
                logDebug(TAG, "Video validation passed: ${detectedType.name}, size=$fileLength bytes")
                return ValidationResult.Valid(detectedType, fileLength)
            } else {
                return ValidationResult.Invalid(
                    "Expected video file, but header signatures did not match MP4 or WebM. Detected: ${detectedType.name}. Snippet: $preview",
                    preview
                )
            }
        } else {
            if (detectedType in listOf(MediaType.JPEG, MediaType.PNG, MediaType.WEBP, MediaType.GIF)) {
                logDebug(TAG, "Image validation passed: ${detectedType.name}, size=$fileLength bytes")
                return ValidationResult.Valid(detectedType, fileLength)
            } else {
                return ValidationResult.Invalid(
                    "Expected image file, but header signatures did not match JPEG/PNG/WebP/GIF. Detected: ${detectedType.name}. Snippet: $preview",
                    preview
                )
            }
        }
    }

    private fun logDebug(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            // In unit tests without Android Log framework
        }
    }

    private fun detectMediaType(header: ByteArray, length: Int): MediaType {
        if (length < 4) return MediaType.UNKNOWN

        // JPEG: 0xFF, 0xD8, 0xFF
        if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
            return MediaType.JPEG
        }

        // PNG: 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        if (length >= 8 &&
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() && header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() && header[7] == 0x0A.toByte()
        ) {
            return MediaType.PNG
        }

        // GIF: GIF87a or GIF89a
        if (length >= 6 &&
            header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() &&
            header[3] == '8'.code.toByte() && (header[4] == '7'.code.toByte() || header[4] == '9'.code.toByte()) &&
            header[5] == 'a'.code.toByte()
        ) {
            return MediaType.GIF
        }

        // WebP: RIFF....WEBP
        if (length >= 12 &&
            header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
        ) {
            return MediaType.WEBP
        }

        // WebM / Matroska: 0x1A, 0x45, 0xDF, 0xA3
        if (length >= 4 &&
            header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
        ) {
            return MediaType.WEBM
        }

        // MP4 / MOV: Check for 'ftyp', 'moov', 'mdat' box in first 32 bytes
        // Usually bytes 4..7 are 'ftyp'
        for (i in 0 until (length - 4).coerceAtMost(28)) {
            val isFtyp = header[i] == 'f'.code.toByte() && header[i + 1] == 't'.code.toByte() &&
                    header[i + 2] == 'y'.code.toByte() && header[i + 3] == 'p'.code.toByte()
            val isMoov = header[i] == 'm'.code.toByte() && header[i + 1] == 'o'.code.toByte() &&
                    header[i + 2] == 'o'.code.toByte() && header[i + 3] == 'v'.code.toByte()
            val isMdat = header[i] == 'm'.code.toByte() && header[i + 1] == 'd'.code.toByte() &&
                    header[i + 2] == 'a'.code.toByte() && header[i + 3] == 't'.code.toByte()

            if (isFtyp || isMoov || isMdat) {
                return MediaType.MP4
            }
        }

        return MediaType.UNKNOWN
    }

    private fun readPreviewSnippet(file: File, maxLength: Int): String {
        return try {
            val buf = ByteArray(maxLength)
            val read = FileInputStream(file).use { it.read(buf) }
            if (read > 0) {
                String(buf, 0, read, Charsets.UTF_8).replace("\n", " ").replace("\r", "").trim()
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
