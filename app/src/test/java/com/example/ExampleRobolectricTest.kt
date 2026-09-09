package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.service.TikwmApiService
import com.example.ui.ShareOverlayActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SnapTok", appName)
  }

  @Test
  fun `test tiktok url extraction`() {
    val text = "Check out this video! https://www.tiktok.com/@creator/video/1234567890?is_from_webapp=1 so funny"
    val extracted = TikwmApiService.extractTikTokUrl(text)
    assertNotNull(extracted)
    assertEquals("https://www.tiktok.com/@creator/video/1234567890?is_from_webapp=1", extracted)
  }

  @Test
  fun `test share intent resolves to ShareOverlayActivity`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, "https://vt.tiktok.com/ZS2x1234/")
    }
    val resolveInfoList = context.packageManager.queryIntentActivities(shareIntent, 0)
    assertTrue("At least one activity must resolve ACTION_SEND text/plain", resolveInfoList.isNotEmpty())
    val resolvedActivity = resolveInfoList.find { it.activityInfo.name == ShareOverlayActivity::class.java.name }
    assertNotNull("ShareOverlayActivity must be registered as target for text/plain share", resolvedActivity)
  }

  @Test
  fun `test open and close in-app video player`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.viewmodel.MainViewModel(app)

    org.junit.Assert.assertNull(viewModel.playingVideo.value)

    viewModel.openInAppPlayer(
      title = "Dance Trend",
      author = "dancer123",
      uriString = "content://media/external/video/media/1",
      filePath = "/storage/emulated/0/Movies/SnapTok/dance.mp4"
    )

    val playing = viewModel.playingVideo.value
    assertNotNull(playing)
    assertEquals("Dance Trend", playing?.title)
    assertEquals("dancer123", playing?.author)
    assertEquals("content://media/external/video/media/1", playing?.uriString)

    viewModel.closeInAppPlayer()
    org.junit.Assert.assertNull(viewModel.playingVideo.value)
  }
}

