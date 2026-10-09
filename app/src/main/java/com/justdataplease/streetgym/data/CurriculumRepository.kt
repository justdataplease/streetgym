package com.justdataplease.streetgym.data

import android.content.Context
import com.justdataplease.streetgym.data.model.CurriculumCatalog

interface CurriculumRepository {
    fun load(): Result<CurriculumCatalog>
}

class AssetCurriculumRepository(
    context: Context,
    private val assetName: String = DEFAULT_ASSET_NAME,
) : CurriculumRepository {
    private val appContext = context.applicationContext

    override fun load(): Result<CurriculumCatalog> = runCatching {
        val json = appContext.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        CurriculumJsonParser.parse(json)
    }

    companion object {
        const val DEFAULT_ASSET_NAME = "curriculum.json"
    }
}
