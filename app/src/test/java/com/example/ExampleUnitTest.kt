package com.example

import com.example.core.DeviceInfoTopic
import com.example.core.FeatureRegister
import com.example.core.NovaIntent
import com.example.core.NovaLanguage
import com.example.managers.PythonBridge
import com.example.voice.IntentDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun featureRegister_has155UniqueFeatures_14Categories_13Phases() {
        val features = FeatureRegister.allFeatures
        assertEquals(155, features.size)
        assertEquals(155, features.map { it.id }.toSet().size)
        assertEquals(14, features.map { it.categoryCode }.toSet().size)
        assertEquals((1..13).toSet(), features.map { it.phase }.toSet())
    }

    @Test
    fun intentDetector_parsesHindiHinglishAndEnglish() {
        val detector = IntentDetector()

        assertEquals(NovaLanguage.HINDI, detector.detectLanguage("बैटरी स्टेटस बताओ"))
        assertEquals(NovaLanguage.HINGLISH, detector.detectLanguage("YouTube kholo"))
        assertEquals(NovaLanguage.ENGLISH, detector.detectLanguage("Open YouTube and search music"))

        val openApp = detector.detect("YouTube kholo")
        assertTrue(openApp is NovaIntent.OpenApp && openApp.appName.equals("YouTube", ignoreCase = true))

        val battery = detector.detect("Battery status batao")
        assertEquals(NovaIntent.DeviceInfoQuery(DeviceInfoTopic.BATTERY), battery)

        val torch = detector.detect("Flashlight on karo")
        assertEquals(NovaIntent.ToggleFlashlight(true), torch)

        assertEquals("45", detector.evaluateMath("250 ka 18%"))
        assertEquals("540", detector.evaluateMath("45 * 12"))
    }

    @Test
    fun pythonBridge_evaluatesSandboxedExpressions() = runBlocking {
        val bridge = PythonBridge()
        val output = bridge.evaluateScript("x = 21 * 2\nprint(x)", "", sendRemote = false)
        assertEquals("42", output.trim())
    }
}
