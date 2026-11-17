package com.ayah.quran.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `fromStorage maps every enum name back to itself`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorage(mode.name))
        }
    }

    @Test
    fun `fromStorage falls back to SYSTEM for unknown values`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("sepia"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
    }
}
