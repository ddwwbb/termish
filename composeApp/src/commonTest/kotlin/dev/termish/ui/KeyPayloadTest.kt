package dev.termish.ui

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KeyPayloadTest {
    @Test
    fun parsesStandardBackslashEscapes() {
        assertContentEquals(
            byteArrayOf(0x0a, 0x0d, 0x09, 0x1b, '\\'.code.toByte(), '^'.code.toByte()),
            parseKeyPayload("\\n\\r\\t\\e\\\\\\^").getOrThrow(),
        )
    }

    @Test
    fun parsesHexAndUnicodeEscapes() {
        assertContentEquals(
            byteArrayOf(0x1b, '['.code.toByte(), 'A'.code.toByte(), 0xff.toByte()),
            parseKeyPayload("\\x1b[A\\xFF").getOrThrow(),
        )
        assertContentEquals(
            "中文".encodeToByteArray(),
            parseKeyPayload("\\u4e2d\\u6587").getOrThrow(),
        )
    }

    @Test
    fun parsesCaretControlCharsCaseInsensitive() {
        assertContentEquals(
            byteArrayOf(0x00, 0x03, 0x03, 0x04, 0x1a, 0x1b, 0x1c, 0x1f, 0x7f),
            parseKeyPayload("^@^C^c^d^Z^[^\\^_^?").getOrThrow(),
        )
    }

    @Test
    fun preservesPlainTextAndUtf8WithoutAppendingNewline() {
        val text = "ls -la /tmp/日志"
        assertContentEquals(
            text.encodeToByteArray(),
            parseKeyPayload(text).getOrThrow(),
        )
    }

    @Test
    fun rejectsInvalidEscapesWithPosition() {
        val invalidCases =
            listOf(
                "abc\\" to 3,
                "abc^" to 3,
                "a\\q" to 1,
                "\\x4" to 0,
                "\\x+1" to 0,
                "\\u123" to 0,
                "\\u12g4" to 0,
                "ok^1" to 2,
            )
        for ((input, expectedIndex) in invalidCases) {
            val result = parseKeyPayload(input)
            assertTrue(result.isFailure, "Expected failure for: $input")
            val ex = assertIs<KeyPayloadException>(result.exceptionOrNull())
            assertEquals(expectedIndex, ex.index, "Wrong error index for: $input")
        }
    }
}
