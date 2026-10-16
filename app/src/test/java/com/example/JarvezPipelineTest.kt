package com.example

import com.jarvez.assistant.command.CommandNormalizer
import com.jarvez.assistant.command.CommandParser
import com.jarvez.assistant.command.ConfirmationType
import com.jarvez.assistant.command.ExecutionContext
import com.jarvez.assistant.command.IntentType
import com.jarvez.assistant.command.ParsedCommand
import com.jarvez.assistant.command.SystemAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvezPipelineTest {

    @Test
    fun testCommandNormalizer_stripsWakeWordsAndFillers() {
        val res1 = CommandNormalizer.normalize("hey jarvez please open youtube")
        assertTrue(res1.strippedWakeWord)
        assertEquals("open youtube", res1.cleaned)

        val res2 = CommandNormalizer.normalize("jarvez bhai camera khol do")
        assertTrue(res2.strippedWakeWord)
        assertEquals("camera kholo", res2.cleaned)

        val res3 = CommandNormalizer.normalize("suno jarvez flashlight on kar do")
        assertTrue(res3.strippedWakeWord)
        assertEquals("flashlight on karo", res3.cleaned)
    }

    @Test
    fun testCommandParser_youtubeCommands() {
        // English play
        val cmd1 = CommandParser.parse("play coldplay on youtube", "play coldplay on youtube")
        assertEquals(IntentType.YOUTUBE_PLAY, cmd1.intentType)
        assertEquals("coldplay", cmd1.query)

        // Urdu play
        val cmd2 = CommandParser.parse("youtube par atif aslam chalao", "youtube par atif aslam chalao")
        assertEquals(IntentType.YOUTUBE_PLAY, cmd2.intentType)
        assertEquals("atif aslam", cmd2.query)

        // YouTube search
        val cmd3 = CommandParser.parse("search youtube for android tutorial", "search youtube for android tutorial")
        assertEquals(IntentType.YOUTUBE_SEARCH, cmd3.intentType)
        assertEquals("android tutorial", cmd3.query)
    }

    @Test
    fun testCommandParser_whatsappCommands() {
        // English WhatsApp
        val cmd1 = CommandParser.parse(
            "send whatsapp message to Ali saying hello how are you",
            "send whatsapp message to Ali saying hello how are you"
        )
        assertEquals(IntentType.WHATSAPP_MESSAGE, cmd1.intentType)
        assertEquals("Ali", cmd1.contactName)
        assertEquals("hello how are you", cmd1.messageBody)
        assertTrue(cmd1.requiresConfirmation)

        // Urdu WhatsApp
        val cmd2 = CommandParser.parse(
            "Ali ko whatsapp message bhejo ke kal milte hain",
            "Ali ko whatsapp message bhejo ke kal milte hain"
        )
        assertEquals(IntentType.WHATSAPP_MESSAGE, cmd2.intentType)
        assertEquals("Ali", cmd2.contactName)
        assertEquals("kal milte hain", cmd2.messageBody)
        assertTrue(cmd2.requiresConfirmation)
    }

    @Test
    fun testCommandParser_systemControls() {
        val torchOn = CommandParser.parse("torch on karo", "torch on karo")
        assertEquals(IntentType.SYSTEM_CONTROL, torchOn.intentType)
        assertEquals(SystemAction.FLASHLIGHT_ON, torchOn.systemAction)

        val torchOff = CommandParser.parse("flashlight off", "flashlight off")
        assertEquals(IntentType.SYSTEM_CONTROL, torchOff.intentType)
        assertEquals(SystemAction.FLASHLIGHT_OFF, torchOff.systemAction)

        val volUp = CommandParser.parse("awaz barhao", "awaz barhao")
        assertEquals(IntentType.SYSTEM_CONTROL, volUp.intentType)
        assertEquals(SystemAction.VOLUME_UP, volUp.systemAction)

        val volDown = CommandParser.parse("volume down", "volume down")
        assertEquals(IntentType.SYSTEM_CONTROL, volDown.intentType)
        assertEquals(SystemAction.VOLUME_DOWN, volDown.systemAction)

        val settings = CommandParser.parse("open settings", "open settings")
        assertEquals(IntentType.SYSTEM_CONTROL, settings.intentType)
        assertEquals(SystemAction.OPEN_SETTINGS, settings.systemAction)
    }

    @Test
    fun testCommandParser_googleSearch() {
        val search1 = CommandParser.parse("search google for latest ai news", "search google for latest ai news")
        assertEquals(IntentType.SEARCH_WEB, search1.intentType)
        assertEquals("latest ai news", search1.query)

        val search2 = CommandParser.parse("google par search karo flutter vs native", "google par search karo flutter vs native")
        assertEquals(IntentType.SEARCH_WEB, search2.intentType)
        assertEquals("flutter vs native", search2.query)
    }

    @Test
    fun testCommandParser_openApp() {
        val app1 = CommandParser.parse("open chrome", "open chrome")
        assertEquals(IntentType.OPEN_APP, app1.intentType)
        assertEquals("chrome", app1.targetApp)

        val app2 = CommandParser.parse("camera kholo", "camera kholo")
        assertEquals(IntentType.OPEN_APP, app2.intentType)
        assertEquals("camera", app2.targetApp)
    }

    @Test
    fun testCommandParser_confirmationResponses() {
        val pendingCmd = ParsedCommand(
            rawText = "send whatsapp to Ali",
            intentType = IntentType.WHATSAPP_MESSAGE,
            contactName = "Ali",
            messageBody = "Hi"
        )
        val context = ExecutionContext(pendingConfirmationCommand = pendingCmd)

        val yesResponse = CommandParser.parse("haan bhej do", "haan bhej do", context)
        assertEquals(IntentType.CONFIRMATION_RESPONSE, yesResponse.intentType)
        assertEquals(ConfirmationType.AFFIRMATIVE, yesResponse.confirmationType)

        val noResponse = CommandParser.parse("nahi mat bhejo", "nahi mat bhejo", context)
        assertEquals(IntentType.CONFIRMATION_RESPONSE, noResponse.intentType)
        assertEquals(ConfirmationType.NEGATIVE, noResponse.confirmationType)
    }
}
