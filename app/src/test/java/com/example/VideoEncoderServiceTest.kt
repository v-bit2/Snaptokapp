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
}
