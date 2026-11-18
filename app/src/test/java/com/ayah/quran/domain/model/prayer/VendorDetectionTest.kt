package com.ayah.quran.domain.model.prayer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Manufacturer/brand detection for the device-specific guidance card.
 * HONOR 600 reports manufacturer "HONOR" — case-insensitive matching keeps
 * OEM casing differences harmless.
 */
class VendorDetectionTest {

    @Test
    fun `honor 600 manufacturer is detected as HONOR`() {
        assertEquals(VendorKind.HONOR, VendorDetection.classify("HONOR"))
        assertEquals(VendorKind.HONOR, VendorDetection.classify("honor"))
    }

    @Test
    fun `huawei falls into the huawei family`() {
        assertEquals(VendorKind.HUAWEI, VendorDetection.classify("HUAWEI"))
    }

    @Test
    fun `brand fallback catches honor-branded builds`() {
        assertEquals(VendorKind.HONOR, VendorDetection.classify("Unknown", brand = "HONOR"))
    }

    @Test
    fun `samsung and generic are classified`() {
        assertEquals(VendorKind.SAMSUNG, VendorDetection.classify("samsung"))
        assertEquals(VendorKind.GENERIC, VendorDetection.classify("Google"))
        assertEquals(VendorKind.GENERIC, VendorDetection.classify(null, null))
    }
}
