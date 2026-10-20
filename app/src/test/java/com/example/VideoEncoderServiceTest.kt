package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.VideoEncoderService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoEncoderServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `test probeVideo on empty or missing file returns incompatible`() {
        val service = VideoEncoderService(context)
        val nonExistent = File(tempFolder.root, "does_not_exist.mp4")
        val probe = service.probeVideo(nonExistent)

        assertFalse(probe.isAlreadyCompatible)
        assertEquals(0L, probe.durationMs)
        assertEquals(0, probe.width)
        assertEquals(0, probe.height)
    }

    @Test
    fun `test validateOutputFile rejects non-existent or tiny files`() {
        val service = VideoEncoderService(context)
        val emptyFile = tempFolder.newFile("empty.mp4")
        val validation = service.validateOutputFile(emptyFile)

        assertFalse(validation.isValid)
        assertTrue(validation.reason.contains("too small") || validation.reason.contains("exist"))
    }

    @Test
    fun `test reencodeToCompatibleMp4 returns Fallback on non-existent input without crashing`() = runBlocking {
        val service = VideoEncoderService(context)
        val nonExistent = File(tempFolder.root, "missing_input.mp4")

        val result = service.reencodeToCompatibleMp4(nonExistent)
        assertTrue(result is VideoEncoderService.EncodeResult.Fallback)
        val fallback = result as VideoEncoderService.EncodeResult.Fallback
        assertEquals(nonExistent.absolutePath, fallback.fallbackFile.absolutePath)
        assertTrue(fallback.reason.isNotEmpty())
    }

    @Test
    fun `test validateOutputFile catches invalid stream signatures gracefully`() {
        val service = VideoEncoderService(context)
        val dummyCorruptedFile = tempFolder.newFile("corrupted.mp4")
        FileOutputStream(dummyCorruptedFile).use { fos ->
            fos.write(ByteArray(5000) { 0x55 })
        }

        val validation = service.validateOutputFile(dummyCorruptedFile)
        assertFalse(validation.isValid)
    }

    @Test
    fun `test Mp4FastStart relocates moov atom and updates chunk offsets`() {
        val inputMp4 = tempFolder.newFile("test_input.mp4")
        val outputMp4 = File(tempFolder.root, "test_faststart_output.mp4")

        // Build a synthetic MP4: ftyp (16 bytes) + mdat (24 bytes) + moov with stco (32 bytes)
        FileOutputStream(inputMp4).use { fos ->
            val buf = java.nio.ByteBuffer.allocate(16 + 24 + 32)
            // 1. ftyp atom (16 bytes)
            buf.putInt(16)
            buf.put("ftyp".toByteArray(Charsets.ISO_8859_1))
            buf.put("mp42".toByteArray(Charsets.ISO_8859_1))
            buf.putInt(0)

            // 2. mdat atom (24 bytes)
            buf.putInt(24)
            buf.put("mdat".toByteArray(Charsets.ISO_8859_1))
            buf.put(ByteArray(16) { 0xAA.toByte() })

            // 3. moov atom (32 bytes)
            buf.putInt(32)
            buf.put("moov".toByteArray(Charsets.ISO_8859_1))
            // inside moov: stco (20 bytes)
            buf.putInt(20)
            buf.put("stco".toByteArray(Charsets.ISO_8859_1))
            buf.putInt(0) // version & flags
            buf.putInt(1) // entry count = 1
            buf.putInt(40) // initial chunk offset = 40
            // 4 pad bytes to fill moov (32 - 8 - 20 = 4)
            buf.put(ByteArray(4))

            fos.write(buf.array())
        }

        // Before faststart, moov is after mdat
        assertFalse(com.example.service.Mp4FastStart.hasFastStart(inputMp4))

        // Process with faststart (in-place)
        val success = com.example.service.Mp4FastStart.process(inputMp4)
        assertTrue("Mp4FastStart process should succeed", success)
        assertTrue("Input file must still exist", inputMp4.exists())
        assertEquals(72L, inputMp4.length())

        // After faststart, moov is before mdat
        assertTrue(com.example.service.Mp4FastStart.hasFastStart(inputMp4))

        // Check that stco offset was adjusted from 40 to 40 + moovSize (32) = 72
        java.io.RandomAccessFile(inputMp4, "r").use { raf ->
            // ftyp = 16 bytes. Next is moov header (8 bytes) + stco header (8 bytes) + version/flags (4 bytes) + count (4 bytes)
            // Offset to the chunk offset value: 16 + 8 + 8 + 4 + 4 = 40
            raf.seek(40)
            val newOffset = raf.readInt()
            assertEquals(72, newOffset)
        }
    }
}
