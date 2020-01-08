package com.rthqks.synapse.assets

import android.content.Context

class AssetManager(
    private val context: Context
) {

    fun readTextAsset(fileName: String) =
        context.assets.open(fileName).bufferedReader().use { it.readText() }
}