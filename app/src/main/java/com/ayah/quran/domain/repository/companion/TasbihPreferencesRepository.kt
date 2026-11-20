package com.ayah.quran.domain.repository.companion

import kotlinx.coroutines.flow.Flow

interface TasbihPreferencesRepository {
    val selectedCounterId: Flow<Long?>
    val hapticsEnabled: Flow<Boolean>
    suspend fun setSelectedCounterId(counterId: Long?)
    suspend fun setHapticsEnabled(enabled: Boolean)
}
