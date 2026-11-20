package com.ayah.quran.core.text

import java.util.Locale

object ArabicTextNormalizer {

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder(input.length)
        var lastWasSpace = true
        for (ch in input) {
            if (isRemovableMark(ch)) continue
            val mapped = mapChar(ch)
            if (mapped == ' ') {
                if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else {
                sb.append(mapped)
                lastWasSpace = false
            }
        }
        if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
            sb.setLength(sb.length - 1)
        }
        return sb.toString().lowercase(Locale.ROOT)
    }

    fun buildFtsQuery(userQuery: String): String {
        val normalized = normalize(userQuery)
        if (normalized.isEmpty()) return ""
        return normalized
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> "$token*" }
    }

    private fun isRemovableMark(ch: Char): Boolean {
        val code = ch.code
        return code in 0x064B..0x065F ||
            code == 0x0670 ||
            code == 0x0640 ||
            code in 0x06D6..0x06ED ||
            code == 0x200C ||
            code == 0x200D
    }

    private fun mapChar(ch: Char): Char = when (ch) {
        '\u0622', '\u0623', '\u0625', '\u0671' -> '\u0627'
        '\u0624' -> '\u0648'
        '\u0626', '\u0649' -> '\u064A'
        '\u0629' -> '\u0647'
        in '\u0660'..'\u0669' -> ('0'.code + (ch.code - 0x0660)).toChar()
        in '\u06F0'..'\u06F9' -> ('0'.code + (ch.code - 0x06F0)).toChar()
        else -> if (isSeparatorOrPunctuation(ch)) ' ' else ch
    }

    private fun isSeparatorOrPunctuation(ch: Char): Boolean {
        if (ch.isWhitespace()) return true
        return ch in PUNCTUATION_CHARS || Character.getType(ch) in PUNCTUATION_TYPES
    }

    private val PUNCTUATION_CHARS = setOf(
        '\u060C', '\u061B', '\u061F', '\u00AB', '\u00BB',
        '\uFD3E', '\uFD3F', '\u06DD', '\u00B7', '\u2013', '\u2014',
        '\u2018', '\u2019', '\u201C', '\u201D', '\u2026',
        '.', ',', ':', ';', '!', '?', '"', '\'', '(', ')', '[', ']', '{', '}',
        '-', '_', '/', '\\', '*', '+', '=', '~', '`', '^', '|', '<', '>', '@', '#', '$', '%', '&',
    )

    private val PUNCTUATION_TYPES = setOf(
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
    )
}
