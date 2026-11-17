package com.ayah.quran.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily

/** One ayah for the mushaf text builder. */
data class MushafAyahData(
    val ayahNumber: Int,
    val displayText: String,
)

/** Character range of one ayah within the annotated string. */
data class MushafAyahRange(
    val ayahNumber: Int,
    val start: Int,
    val end: Int, // exclusive
)

/**
 * Builds a continuous Mushaf-style AnnotatedString from a list of ayahs.
 *
 * Every ayah's Uthmani text is concatenated inline with its verse-ending
 * marker (﴿N﴾ in Arabic-Indic digits, gold). The returned ranges map
 * character offsets back to ayah numbers for tap handling.
 *
 * Quranic text is never modified — this function only concatenates the
 * verbatim dataset strings and adds styling metadata.
 */
fun buildMushafString(
    ayahs: List<MushafAyahData>,
    highlightColor: Color,
    selectedAyahNumber: Int?,
    textColor: Color,
    markerColor: Color,
    fontSize: TextUnit,
): Pair<AnnotatedString, List<MushafAyahRange>> {
    val ranges = mutableListOf<MushafAyahRange>()
    val markerSize = fontSize * 0.72f

    val text = buildAnnotatedString {
        ayahs.forEach { ayah ->
            val textStart = length
            append(ayah.displayText)
            val textEnd = length

            // Ayah number marker.
            append(" \uFD3F")
            append(AyahNumbering.toArabicIndic(ayah.ayahNumber))
            append("\uFD3E")
            val markerEnd = length

            addStyle(SpanStyle(color = textColor, fontSize = fontSize), textStart, textEnd)
            addStyle(SpanStyle(color = markerColor, fontSize = markerSize), textEnd, markerEnd)

            if (ayah.ayahNumber == selectedAyahNumber) {
                addStyle(SpanStyle(background = highlightColor), textStart, markerEnd)
            }

            ranges.add(MushafAyahRange(ayah.ayahNumber, textStart, markerEnd))
        }
    }
    return text to ranges
}

/** Finds the ayah number containing [offset], or null. */
fun findAyahAtOffset(offset: Int, ranges: List<MushafAyahRange>): Int? =
    ranges.firstOrNull { offset >= it.start && offset < it.end }?.ayahNumber

/**
 * Continuous Mushaf-style Quran text with tap-to-select per ayah.
 *
 * RTL, Amiri typeface, and full diacritics are preserved by the platform
 * text stack. Rebuilds the AnnotatedString when [highlightAyahNumber] or
 * [fontSize] changes (highlight/size only — text content is stable).
 */
@Composable
fun MushafText(
    ayahs: List<MushafAyahData>,
    highlightAyahNumber: Int?,
    fontSize: TextUnit,
    onAyahTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
    val markerColor = MaterialTheme.colorScheme.secondary
    val textColor = MaterialTheme.colorScheme.onSurface

    val (annotatedString, ranges) = remember(ayahs, highlightAyahNumber, highlightColor, markerColor, textColor, fontSize) {
        buildMushafString(ayahs, highlightColor, highlightAyahNumber, textColor, markerColor, fontSize)
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    SelectionContainer {
        Text(
            text = annotatedString,
            onTextLayout = { layoutResult = it },
            style = TextStyle(
                fontFamily = QuranTextFontFamily,
                textDirection = TextDirection.Rtl,
            ),
            modifier = modifier
                .fillMaxSize()
                .pointerInput(ayahs) {
                    detectTapGestures { position ->
                        layoutResult?.let { layout ->
                            val charOffset = layout.getOffsetForPosition(position)
                            findAyahAtOffset(charOffset, ranges)?.let { ayahNumber ->
                                onAyahTap(ayahNumber)
                            }
                        }
                    }
                },
        )
    }
}
