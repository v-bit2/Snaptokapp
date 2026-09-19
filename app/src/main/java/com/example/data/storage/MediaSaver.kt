package com.example.data.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaSaver {
    private const val TAG = "SnapTokMediaSaver"

    data class SaveResult(
        val uri: Uri,
        val filePath: String,
        val sizeBytes: Long,
        val mimeType: String = "video/mp4"
    )

    /**
     * Saves a video stream with complete validation:
     * 1. Writes to an isolated temporary file in cacheDir
     * 2. Flushes userspace buffer and calls fsync() on OS file descriptor
     * 3. Verifies Content-Length (catches truncated network streams)
     * 4. Verifies MP4/WebM magic bytes (catches HTML error pages or rate limit JSON)
     * 5. Atomically saves to public MediaStore (Movies/SnapTok) with IS_PENDING, fsync(),
     *    sets exact MediaColumns.SIZE and DATE_MODIFIED, and triggers MediaScannerConnection
     * 6. Cleans up temp files safely and deletes pending MediaStore rows on any error
     */
    fun saveVideoToGallery(
        context: Context,
        inputStream: InputStream,
        title: String,
        expectedContentLength: Long = -1L,
        onProgress: (bytesWritten: Long) -> Unit
    ): SaveResult {
        Log.d(TAG, "[DL-1] Starting video download write. expectedLength=$expectedContentLength, title=$title")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30).ifBlank { "video" }

        // Isolated temporary file
        val tempFile = File.createTempFile("snaptok_v_", ".tmp", context.cacheDir)
        var totalWritten = 0L

        try {
            // Step 1: Write network stream to temp file
            FileOutputStream(tempFile).use { fos ->
                val buffer = ByteArray(32 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    fos.write(buffer, 0, read)
                    totalWritten += read
                    onProgress(totalWritten)
                }
                fos.flush()
                fos.fd.sync() // Ensure OS flushes all bytes to physical storage
            }
            Log.d(TAG, "[DL-2] Stream closed and synced to temp file. totalBytesWritten=$totalWritten")

            // Step 2: Content-Length verification
            if (expectedContentLength > 0 && totalWritten < expectedContentLength) {
                val msg = "Download incomplete: received $totalWritten bytes but server expected $expectedContentLength bytes."
                Log.e(TAG, "[DL-ERR] $msg")
                throw IOException(msg)
            }

            return saveExistingVideoFileToGallery(context, tempFile, title)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Saves an already downloaded, verified, or re-encoded video File directly to MediaStore / Public Gallery.
     * Performs physical descriptor sync, IS_PENDING finalization, and MediaScannerConnection registration.
     */
    fun saveExistingVideoFileToGallery(
        context: Context,
        videoFile: File,
        title: String
    ): SaveResult {
        if (!videoFile.exists() || videoFile.length() == 0L) {
            throw IOException("Video file does not exist or is empty: ${videoFile.absolutePath}")
        }

        val totalWritten = videoFile.length()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30).ifBlank { "video" }

        // Signature & Magic Bytes verification
        val validation = MediaValidator.validateMediaFile(
            file = videoFile,
            expectedVideo = true
        )

        if (validation !is MediaValidator.ValidationResult.Valid) {
            val reason = (validation as MediaValidator.ValidationResult.Invalid).reason
            Log.e(TAG, "[DL-ERR] Media validation failed: $reason")
            throw IOException("Invalid or corrupted video data: $reason")
        }

        val mediaType = validation.mediaType
        val extension = mediaType.extension
        val mimeType = mediaType.mimeType
        val finalFileName = "SnapTok_${sanitizedTitle}_$timestamp.$extension"
        Log.d(TAG, "[DL-3] Video signature verified: $mimeType ($extension). Final filename: $finalFileName")

        // Transfer to MediaStore / Public Gallery
        val resolver = context.contentResolver

        // Branch A: Android 10+ (Q+) Scoped Storage MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, finalFileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SnapTok")
                put(MediaStore.Video.Media.IS_PENDING, 1)
                put(MediaStore.Video.Media.SIZE, totalWritten)
                val nowSec = System.currentTimeMillis() / 1000
                put(MediaStore.Video.Media.DATE_ADDED, nowSec)
                put(MediaStore.Video.Media.DATE_MODIFIED, nowSec)
            }

            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                try {
                    resolver.openFileDescriptor(uri, "w")?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { fos ->
                            videoFile.inputStream().use { inStream ->
                                inStream.copyTo(fos)
                            }
                            fos.flush()
                            pfd.fileDescriptor.sync() // Ensure physical disk commit
                        }
                    }

                    // Finalize MediaStore entry: clear IS_PENDING and update final size
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    contentValues.put(MediaStore.Video.Media.SIZE, totalWritten)
                    contentValues.put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                    resolver.update(uri, contentValues, null, null)

                    // Query the actual DATA path if assigned by the OS
                    var resolvedPath = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)}/SnapTok/$finalFileName"
                    try {
                        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                                if (dataIdx >= 0) {
                                    val realPath = cursor.getString(dataIdx)
                                    if (!realPath.isNullOrBlank()) {
                                        resolvedPath = realPath
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not query MediaStore DATA column", e)
                    }

                    // Notify MediaScanner for instant third-party app visibility (Alight Motion, Gallery, etc.)
                    MediaScannerConnection.scanFile(context, arrayOf(resolvedPath), arrayOf(mimeType), null)
                    Log.d(TAG, "[DL-4] Video saved to MediaStore: uri=$uri, path=$resolvedPath, size=$totalWritten")

                    return SaveResult(
                        uri = uri,
                        filePath = resolvedPath,
                        sizeBytes = totalWritten,
                        mimeType = mimeType
                    )
                } catch (writeErr: Exception) {
                    Log.e(TAG, "[DL-ERR] Error writing to MediaStore URI: $uri", writeErr)
                    try {
                        resolver.delete(uri, null, null) // remove partial row
                    } catch (ignored: Exception) {}
                    throw writeErr
                }
            }
        }

        // Branch B: Direct public Movies directory (Android 9 or below / fallback)
        val moviesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "SnapTok"
        )
        if (!moviesDir.exists()) {
            moviesDir.mkdirs()
        }

        if (moviesDir.exists() && moviesDir.canWrite()) {
            val targetFile = File(moviesDir, finalFileName)
            FileOutputStream(targetFile).use { fos ->
                videoFile.inputStream().use { inStream ->
                    inStream.copyTo(fos)
                }
                fos.flush()
                fos.fd.sync()
            }

            val contentValues = ContentValues().apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.DATA, targetFile.absolutePath)
                }
                put(MediaStore.Video.Media.DISPLAY_NAME, finalFileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.SIZE, totalWritten)
            }

            val uri = try {
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: Uri.fromFile(targetFile)
            } catch (e: Exception) {
                Uri.fromFile(targetFile)
            }

            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
            Log.d(TAG, "[DL-4] Video saved to public Movies dir: path=${targetFile.absolutePath}, uri=$uri")

            return SaveResult(
                uri = uri,
                filePath = targetFile.absolutePath,
                sizeBytes = totalWritten,
                mimeType = mimeType
            )
        }

        // Branch C: Safe app-specific external files dir fallback with FileProvider
        val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }
        val safeFallbackFile = File(fallbackDir, finalFileName)
        FileOutputStream(safeFallbackFile).use { fos ->
            videoFile.inputStream().use { inStream ->
                inStream.copyTo(fos)
            }
            fos.flush()
            fos.fd.sync()
        }

        val safeUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", safeFallbackFile)
        } catch (e: Exception) {
            Uri.fromFile(safeFallbackFile)
        }

        MediaScannerConnection.scanFile(context, arrayOf(safeFallbackFile.absolutePath), arrayOf(mimeType), null)
        Log.d(TAG, "[DL-4] Video saved to app-specific fallback: path=${safeFallbackFile.absolutePath}, uri=$safeUri")

        return SaveResult(
            uri = safeUri,
            filePath = safeFallbackFile.absolutePath,
            sizeBytes = totalWritten,
            mimeType = mimeType
        )
    }

    /**
     * Saves an image stream with complete validation:
     * 1. Writes to an isolated temporary file in cacheDir
     * 2. Flushes userspace buffer and calls fsync() on OS file descriptor
     * 3. Verifies Content-Length
     * 4. Verifies JPEG/PNG/WebP/GIF magic bytes (prevents saving HTML error pages or JSON as photos)
     * 5. Identifies true MIME type and extension (.jpg, .png, .webp) to prevent third-party parser errors
     * 6. Atomically saves to public MediaStore (Pictures/SnapTok) with IS_PENDING, fsync(),
     *    sets exact MediaColumns.SIZE and DATE_MODIFIED, and triggers MediaScannerConnection
     */
    fun saveImageToGallery(
        context: Context,
        inputStream: InputStream,
        title: String,
        index: Int,
        total: Int,
        expectedContentLength: Long = -1L
    ): SaveResult {
        Log.d(TAG, "[DL-IMG-1] Starting image write (${index + 1}/$total). expectedLength=$expectedContentLength")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24).ifBlank { "photo" }

        // Isolated temporary file with index for race-condition prevention
        val tempFile = File.createTempFile("snaptok_i_${index}_", ".tmp", context.cacheDir)
        var totalWritten = 0L

        try {
            // Step 1: Write network stream to temp file
            FileOutputStream(tempFile).use { fos ->
                val buffer = ByteArray(32 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    fos.write(buffer, 0, read)
                    totalWritten += read
                }
                fos.flush()
                fos.fd.sync()
            }
            Log.d(TAG, "[DL-IMG-2] Stream closed and synced to temp file. totalBytesWritten=$totalWritten")

            // Step 2: Content-Length verification
            if (expectedContentLength > 0 && totalWritten < expectedContentLength) {
                val msg = "Download incomplete: received $totalWritten bytes but server expected $expectedContentLength bytes."
                Log.e(TAG, "[DL-ERR] $msg")
                throw IOException(msg)
            }

            // Step 3: Signature & Magic Bytes verification
            val validation = MediaValidator.validateMediaFile(
                file = tempFile,
                expectedVideo = false,
                expectedContentLength = expectedContentLength
            )

            if (validation !is MediaValidator.ValidationResult.Valid) {
                val reason = (validation as MediaValidator.ValidationResult.Invalid).reason
                Log.e(TAG, "[DL-ERR] Image validation failed: $reason")
                throw IOException("Invalid or corrupted image data: $reason")
            }

            val mediaType = validation.mediaType
            val extension = mediaType.extension
            val mimeType = mediaType.mimeType
            val finalFileName = "SnapTok_${sanitizedTitle}_${index + 1}of${total}_$timestamp.$extension"
            Log.d(TAG, "[DL-IMG-3] Image signature verified: $mimeType ($extension). Final filename: $finalFileName")

            val resolver = context.contentResolver

            // Branch A: Android 10+ (Q+) Scoped Storage MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapTok")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                    put(MediaStore.Images.Media.SIZE, totalWritten)
                    val nowSec = System.currentTimeMillis() / 1000
                    put(MediaStore.Images.Media.DATE_ADDED, nowSec)
                    put(MediaStore.Images.Media.DATE_MODIFIED, nowSec)
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    try {
                        resolver.openFileDescriptor(uri, "w")?.use { pfd ->
                            FileOutputStream(pfd.fileDescriptor).use { fos ->
                                tempFile.inputStream().use { inStream ->
                                    inStream.copyTo(fos)
                                }
                                fos.flush()
                                pfd.fileDescriptor.sync()
                            }
                        }

                        // Finalize entry
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentValues.put(MediaStore.Images.Media.SIZE, totalWritten)
                        contentValues.put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                        resolver.update(uri, contentValues, null, null)

                        var resolvedPath = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}/SnapTok/$finalFileName"
                        try {
                            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                                    if (dataIdx >= 0) {
                                        val realPath = cursor.getString(dataIdx)
                                        if (!realPath.isNullOrBlank()) {
                                            resolvedPath = realPath
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not query MediaStore DATA column", e)
                        }

                        MediaScannerConnection.scanFile(context, arrayOf(resolvedPath), arrayOf(mimeType), null)
                        Log.d(TAG, "[DL-IMG-4] Image saved to MediaStore: uri=$uri, path=$resolvedPath")

                        return SaveResult(
                            uri = uri,
                            filePath = resolvedPath,
                            sizeBytes = totalWritten,
                            mimeType = mimeType
                        )
                    } catch (writeErr: Exception) {
                        Log.e(TAG, "[DL-ERR] Error writing to MediaStore image URI: $uri", writeErr)
                        try {
                            resolver.delete(uri, null, null)
                        } catch (ignored: Exception) {}
                        throw writeErr
                    }
                }
            }

            // Branch B: Direct public Pictures directory
            val picturesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "SnapTok"
            )
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }

            if (picturesDir.exists() && picturesDir.canWrite()) {
                val targetFile = File(picturesDir, finalFileName)
                FileOutputStream(targetFile).use { fos ->
                    tempFile.inputStream().use { inStream ->
                        inStream.copyTo(fos)
                    }
                    fos.flush()
                    fos.fd.sync()
                }

                val contentValues = ContentValues().apply {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
                    }
                    put(MediaStore.Images.Media.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.SIZE, totalWritten)
                }

                val uri = try {
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        ?: Uri.fromFile(targetFile)
                } catch (e: Exception) {
                    Uri.fromFile(targetFile)
                }

                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
                Log.d(TAG, "[DL-IMG-4] Image saved to public Pictures dir: path=${targetFile.absolutePath}, uri=$uri")

                return SaveResult(
                    uri = uri,
                    filePath = targetFile.absolutePath,
                    sizeBytes = totalWritten,
                    mimeType = mimeType
                )
            }

            // Branch C: Safe app-specific fallback
            val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            if (!fallbackDir.exists()) {
                fallbackDir.mkdirs()
            }
            val safeFallbackFile = File(fallbackDir, finalFileName)
            FileOutputStream(safeFallbackFile).use { fos ->
                tempFile.inputStream().use { inStream ->
                    inStream.copyTo(fos)
                }
                fos.flush()
                fos.fd.sync()
            }

            val safeUri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", safeFallbackFile)
            } catch (e: Exception) {
                Uri.fromFile(safeFallbackFile)
            }

            MediaScannerConnection.scanFile(context, arrayOf(safeFallbackFile.absolutePath), arrayOf(mimeType), null)
            Log.d(TAG, "[DL-IMG-4] Image saved to app-specific fallback: path=${safeFallbackFile.absolutePath}, uri=$safeUri")

            return SaveResult(
                uri = safeUri,
                filePath = safeFallbackFile.absolutePath,
                sizeBytes = totalWritten,
                mimeType = mimeType
            )
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
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
            val chooser = Intent.createChooser(intent, "Play Video With").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    fun viewImage(context: Context, uriString: String, filePath: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = try {
                val parsed = Uri.parse(uriString)
                if (parsed.scheme == "content") parsed else Uri.fromFile(File(filePath))
            } catch (e: Exception) {
                Uri.fromFile(File(filePath))
            }
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(intent, "View Image With").apply {
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
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Downloaded with SnapTok: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Video").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareImage(context: Context, uriString: String, filePath: String, title: String) {
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
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Downloaded with SnapTok: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Image").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareMultipleImages(context: Context, uriStrings: List<String>, title: String) {
        val uris = ArrayList<Uri>()
        for (u in uriStrings) {
            try {
                uris.add(Uri.parse(u))
            } catch (ignored: Exception) {}
        }
        if (uris.isEmpty()) return

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, "Downloaded with SnapTok: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Images").apply {
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
