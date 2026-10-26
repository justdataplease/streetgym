package com.justdataplease.streetgym.data

import android.content.Context
import com.justdataplease.streetgym.data.model.CurriculumCatalog
import org.json.JSONObject

interface CurriculumRepository {
    fun load(): Result<CurriculumCatalog>
}

class AssetCurriculumRepository(
    context: Context,
    private val assetName: String = DEFAULT_ASSET_NAME,
) : CurriculumRepository {
    private val appContext = context.applicationContext

    override fun load(): Result<CurriculumCatalog> = readRoot().mapCatching(CurriculumJsonParser::parse)

    /** The asset's parsed JSON, so other readers of it (Skills) don't read and parse the same 2.4 MB again. */
    fun readRoot(): Result<JSONObject> = runCatching {
        JSONObject(appContext.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    companion object {
        const val DEFAULT_ASSET_NAME = "curriculum.json"
    }
}
