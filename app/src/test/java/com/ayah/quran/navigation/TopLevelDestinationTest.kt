package com.ayah.quran.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `exposes exactly the six product sections in order`() {
        val names = TopLevelDestination.entries.map { it.name }
        assertEquals(
            listOf("HOME", "QURAN", "PRAYER", "WALLPAPERS", "AUDIO", "SETTINGS"),
            names,
        )
    }

    @Test
    fun `routes are unique and non-blank`() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
        routes.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `home is the start destination`() {
        assertEquals("home", TopLevelDestination.entries.first().route)
    }
}
