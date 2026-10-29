package com.metafrazo.app.feature.captions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionRouterTest {
    private val translations = mapOf("en" to "hello", "ta" to "வணக்கம்", "hi" to "नमस्ते")

    @Test fun emptyRoomBroadcastsEverything() {
        val plan = CaptionRouter.plan(
            sourceLang = "en",
            original = "hello",
            translations = translations,
            remoteIdentities = emptyList(),
            prefs = emptyMap(),
        )
        assertEquals(1, plan.size)
        assertNull("empty room must broadcast", plan[0].identities)
        assertEquals(translations, plan[0].translations)
    }

    @Test fun unknownRecipientsGetBroadcastFallback() {
        val plan = CaptionRouter.plan(
            sourceLang = "en",
            original = "hello",
            translations = translations,
            remoteIdentities = listOf("a", "b"),
            prefs = emptyMap(),
        )
        // No known prefs -> single broadcast (null identities) with all translations.
        assertEquals(1, plan.size)
        assertNull(plan[0].identities)
        assertEquals(translations, plan[0].translations)
    }

    @Test fun knownRecipientGetsOnlyTheirLanguage() {
        val plan = CaptionRouter.plan(
            sourceLang = "en",
            original = "hello",
            translations = translations,
            remoteIdentities = listOf("tamilUser"),
            prefs = mapOf("tamilUser" to "ta"),
        )
        assertEquals(1, plan.size)
        assertEquals(listOf("tamilUser"), plan[0].identities)
        assertEquals(mapOf("ta" to "வணக்கம்"), plan[0].translations)
    }

    @Test fun recipientsGroupedByLanguage() {
        val plan = CaptionRouter.plan(
            sourceLang = "en",
            original = "hello",
            translations = translations,
            remoteIdentities = listOf("t1", "t2", "h1"),
            prefs = mapOf("t1" to "ta", "t2" to "ta", "h1" to "hi"),
        )
        // One message per distinct language, no broadcast fallback (all known).
        assertEquals(2, plan.size)
        val ta = plan.first { it.translations.containsKey("ta") }
        val hi = plan.first { it.translations.containsKey("hi") }
        assertEquals(setOf("t1", "t2"), ta.identities!!.toSet())
        assertEquals(mapOf("ta" to "வணக்கம்"), ta.translations)
        assertEquals(listOf("h1"), hi.identities)
        assertEquals(mapOf("hi" to "नमस्ते"), hi.translations)
    }

    @Test fun mixedKnownAndUnknownSplitDelivery() {
        val plan = CaptionRouter.plan(
            sourceLang = "en",
            original = "hello",
            translations = translations,
            remoteIdentities = listOf("known", "mystery"),
            prefs = mapOf("known" to "hi"),
        )
        assertEquals(2, plan.size)
        val targeted = plan.first { it.identities == listOf("known") }
        assertEquals(mapOf("hi" to "नमस्ते"), targeted.translations)
        // Fallback targets only the unknown recipient, not a full broadcast.
        val fallback = plan.first { it.identities == listOf("mystery") }
        assertEquals(translations, fallback.translations)
    }

    @Test fun preferenceMatchingSourceReturnsOriginal() {
        val plan = CaptionRouter.plan(
            sourceLang = "en",
            original = "hello",
            translations = mapOf("ta" to "வணக்கம்"),
            remoteIdentities = listOf("englishUser"),
            prefs = mapOf("englishUser" to "en"),
        )
        assertEquals(1, plan.size)
        assertEquals(mapOf("en" to "hello"), plan[0].translations)
    }

    @Test fun missingTranslationFallsBackToOriginal() {
        val plan = CaptionRouter.plan(
            sourceLang = "en",
            original = "hello",
            translations = emptyMap(),
            remoteIdentities = listOf("u"),
            prefs = mapOf("u" to "hi"),
        )
        assertEquals(1, plan.size)
        assertEquals(mapOf("hi" to "hello"), plan[0].translations)
    }

    @Test fun fallbackAlwaysCarriesSourceLanguage() {
        val plan = CaptionRouter.plan(
            sourceLang = "ta",
            original = "வணக்கம்",
            translations = mapOf("en" to "hello"),
            remoteIdentities = emptyList(),
            prefs = emptyMap(),
        )
        assertTrue(plan[0].translations.containsKey("ta"))
        assertEquals("வணக்கம்", plan[0].translations["ta"])
    }
}
