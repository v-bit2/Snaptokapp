package com.example

import com.example.data.storage.MediaValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test valid MP4 file with ftyp header is recognized and validated`() {
        val file = tempFolder.newFile("test_video.mp4")
        FileOutputStream(file).use { fos ->
            // 4 bytes size + 'f','t','y','p' + 'i','s','o','m' + dummy padding > 10KB
            val header = byteArrayOf(
                0x00, 0x00, 0x00, 0x20,
                0x66, 0x74, 0x79, 0x70, // ftyp
                0x69, 0x73, 0x6F, 0x6D  // isom
            )
            fos.write(header)
            fos.write(ByteArray(15_000)) // pad past 10KB minimum
        }

        val result = MediaValidator.validateMediaFile(file, expectedVideo = true)
        assertTrue("Expected valid MP4 validation result", result is MediaValidator.ValidationResult.Valid)
        val valid = result as MediaValidator.ValidationResult.Valid
        assertEquals(MediaValidator.MediaType.MP4, valid.mediaType)
        assertEquals("video/mp4", valid.mediaType.mimeType)
        assertEquals("mp4", valid.mediaType.extension)
    }

    @Test
    fun `test valid JPEG file is recognized`() {
        val file = tempFolder.newFile("test_photo.jpg")
        FileOutputStream(file).use { fos ->
            // JPEG SOI: 0xFF, 0xD8, 0xFF, 0xE0
            val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
            fos.write(header)
            fos.write(ByteArray(2_048)) // > 512 bytes
        }

        val result = MediaValidator.validateMediaFile(file, expectedVideo = false)
        assertTrue("Expected valid JPEG validation result", result is MediaValidator.ValidationResult.Valid)
        val valid = result as MediaValidator.ValidationResult.Valid
        assertEquals(MediaValidator.MediaType.JPEG, valid.mediaType)
        assertEquals("image/jpeg", valid.mediaType.mimeType)
    }

    @Test
    fun `test valid WebP file is recognized with correct MIME type`() {
        val file = tempFolder.newFile("test_photo.webp")
        FileOutputStream(file).use { fos ->
            // RIFF (4) + length (4) + WEBP (4)
            val header = byteArrayOf(
                0x52, 0x49, 0x46, 0x46, // RIFF
                0x00, 0x00, 0x01, 0x00,
                0x57, 0x45, 0x42, 0x50  // WEBP
            )
            fos.write(header)
            fos.write(ByteArray(2_048))
        }

        val result = MediaValidator.validateMediaFile(file, expectedVideo = false)
        assertTrue("Expected valid WebP validation result", result is MediaValidator.ValidationResult.Valid)
        val valid = result as MediaValidator.ValidationResult.Valid
        assertEquals(MediaValidator.MediaType.WEBP, valid.mediaType)
        assertEquals("image/webp", valid.mediaType.mimeType)
        assertEquals("webp", valid.mediaType.extension)
    }

    @Test
    fun `test HTML error page is rejected as invalid media`() {
        val file = tempFolder.newFile("error.mp4")
        file.writeText("<!DOCTYPE html><html><head><title>Access Denied</title></head><body>Rate limited</body></html>")

        val result = MediaValidator.validateMediaFile(file, expectedVideo = true)
        assertTrue("Expected invalid result for HTML error page", result is MediaValidator.ValidationResult.Invalid)
        val invalid = result as MediaValidator.ValidationResult.Invalid
        assertTrue(invalid.reason.contains("error") || invalid.reason.contains("HTML") || invalid.reason.contains("small"))
    }

    @Test
    fun `test JSON error response is rejected as invalid media`() {
        val file = tempFolder.newFile("error.mp4")
        file.writeText("{\"code\": -1, \"msg\": \"URL has expired, please request new token\"}")

        val result = MediaValidator.validateMediaFile(file, expectedVideo = true)
        assertTrue("Expected invalid result for JSON error payload", result is MediaValidator.ValidationResult.Invalid)
    }

    @Test
    fun `test truncated file is rejected when expectedContentLength is provided`() {
        val file = tempFolder.newFile("truncated.mp4")
        FileOutputStream(file).use { fos ->
            val header = byteArrayOf(
                0x00, 0x00, 0x00, 0x20,
                0x66, 0x74, 0x79, 0x70,
                0x69, 0x73, 0x6F, 0x6D
            )
            fos.write(header)
            fos.write(ByteArray(11_000))
        }

        // Server reported 50,000 bytes, but file only has 11,012 bytes
        val result = MediaValidator.validateMediaFile(file, expectedVideo = true, expectedContentLength = 50_000L)
        assertTrue("Expected invalid result for truncated file", result is MediaValidator.ValidationResult.Invalid)
        val invalid = result as MediaValidator.ValidationResult.Invalid
        assertTrue(invalid.reason.contains("Truncated"))
    }
}
