package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.TikwmApiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}

