package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ExampleRobolectricTest {

  @Test
  fun `app_name, powered_by, and app_description match হিসাবনিকাশ branding in default and bn locales`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("হিসাবনিকাশ", context.getString(R.string.app_name))
    assertEquals("Powered by DZ Ai-STUDIO", context.getString(R.string.powered_by))
    assertTrue(context.getString(R.string.app_description).contains("DZ Ai-STUDIO"))

    val bnConfig = Configuration(context.resources.configuration).apply {
      setLocale(Locale.forLanguageTag("bn-BD"))
    }
    val bnContext = context.createConfigurationContext(bnConfig)
    assertEquals("হিসাবনিকাশ", bnContext.getString(R.string.app_name))
    assertEquals("Powered by DZ Ai-STUDIO", bnContext.getString(R.string.powered_by))
    assertTrue(
      bnContext.getString(R.string.app_description).contains(
        "আপনার আয়, ব্যয়, বাজেট, অ্যাকাউন্ট ও সঞ্চয়ের সম্পূর্ণ হিসাব রাখুন এক জায়গায়। অফলাইনেই নিরাপদে পরিচালনা করুন আপনার ব্যক্তিগত অর্থ—হিসাবনিকাশের মাধ্যমে।"
      )
    )
  }
}
