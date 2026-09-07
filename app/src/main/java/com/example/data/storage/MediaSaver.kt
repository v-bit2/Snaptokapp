package com.example.data.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaSaver {

    data class SaveResult(
        val uri: Uri,
        val filePath: String,
        val sizeBytes: Long
    )

    /**
     * Saves a video stream directly to public MediaStore (Movies/SnapTok).
     * This ensures the video appears immediately in the device's Gallery / Photos app.
     */
    fun saveVideoToGallery(
        context: Context,
        inputStream: InputStream,
        title: String,
        contentLength: Long,
        onProgress: (bytesWritten: Long) -> Unit
    ): SaveResult {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
        val fileName = "SnapTok_${sanitizedTitle}_$timestamp.mp4"

        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SnapTok")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Failed to create MediaStore entry")

            var totalWritten = 0L
            resolver.openOutputStream(uri)?.use { outputStream ->
                val buffer = ByteArray(8 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    totalWritten += read
                    onProgress(totalWritten)
                }
                outputStream.flush()
            } ?: throw IllegalStateException("Failed to open output stream for MediaStore uri")

            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            // Attempt to get physical path or fallback to Movies/SnapTok path
            val fallbackPath = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)}/SnapTok/$fileName"

            return SaveResult(
                uri = uri,
                filePath = fallbackPath,
                sizeBytes = totalWritten
            )
        } else {
            // Android 9 and lower
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "SnapTok"
            )
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }
            val targetFile = File(moviesDir, fileName)
            var totalWritten = 0L
            FileOutputStream(targetFile).use { outputStream ->
                val buffer = ByteArray(8 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    totalWritten += read
                    onProgress(totalWritten)
                }
                outputStream.flush()
            }

            // Register in MediaStore so it appears in Gallery
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DATA, targetFile.absolutePath)
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: Uri.fromFile(targetFile)

            return SaveResult(
                uri = uri,
                filePath = targetFile.absolutePath,
                sizeBytes = totalWritten
            )
        }
    }

    fun playVideo(context: Context, uriString: String, filePath: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = try {
                val parsed = Uri.parse(uriString)
                if (parsed.scheme == "content") parsed else Uri.fromFile(File(filePath))
            } catch (e: Exception) {
                Uri.fromFile(File(filePath))
            }
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // If no default handler, try chooser
            val chooser = Intent.createChooser(intent, "Play Video With").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    fun shareVideo(context: Context, uriString: String, filePath: String, title: String) {
        val uri = try {
            val parsed = Uri.parse(uriString)
            if (parsed.scheme == "content") {
                parsed
            } else {
                val file = File(filePath)
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }
        } catch (e: Exception) {
            Uri.parse(uriString)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Downloaded with SnapTok: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Video").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun deleteVideo(context: Context, uriString: String, filePath: String): Boolean {
        var deleted = false
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) deleted = true
            }
        } catch (ignored: Exception) {}

        try {
            val file = File(filePath)
            if (file.exists()) {
                if (file.delete()) deleted = true
            }
        } catch (ignored: Exception) {}

        return deleted
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = bytes.toDouble() / 1024
            String.format(Locale.US, "%.1f KB", kb)
        }
    }

    fun formatDuration(durationSec: Int): String {
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
