package com.ayah.quran.domain.repository

import com.ayah.quran.domain.model.RubElHizb

interface RubElHizbCatalog {
    suspend fun getAll(): List<RubElHizb>
}
