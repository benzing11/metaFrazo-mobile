package com.metafrazo.app.core.meeting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingCodeTest {
    @Test fun rawCodeIsNormalized() = assertEquals("AB3XYZ", MeetingCode.extract("  ab3 xyz "))

    @Test fun httpsLink() = assertEquals("AB3XYZ", MeetingCode.extract("https://metafrazo.app/join/AB3XYZ"))

    @Test fun linkWithQueryAndSlash() = assertEquals("AB3XYZ", MeetingCode.extract("https://metafrazo.app/join/ab3xyz/?ref=chat"))

    @Test fun customScheme() = assertEquals("AB3XYZ", MeetingCode.extract("metafrazo://join/AB3XYZ"))

    @Test fun validation() {
        assertTrue(MeetingCode.isValid("AB3XYZ"))
        assertFalse(MeetingCode.isValid("AB3"))
    }
}
