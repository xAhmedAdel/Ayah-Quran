package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.RubElHizb
import com.ayah.quran.domain.repository.RubElHizbCatalog
import javax.inject.Inject

class GetRubElHizbListUseCase @Inject constructor(
    private val catalog: RubElHizbCatalog,
) {
    suspend operator fun invoke(): List<RubElHizb> = catalog.getAll()
}
