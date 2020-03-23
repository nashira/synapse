package com.rthqks.synapse.polish

import com.rthqks.synapse.R
import com.rthqks.synapse.logic.*

class Effect(
    val network: Network
) {
    val properties = Properties().apply {
        put(
            Property(
                Title,
                TextType(
                    R.string.property_name_network_name,
                    R.drawable.ic_text_fields
                ), "Synapse Effect"
            ), TextConverter
        )
    }

    val title: String get() = properties[Title]

    companion object {
        // node id allocations
        // 0 - 10,000 : effect specific nodes
        // 10000+ : effect context nodes
        const val ID_CAMERA = 10_001
        const val ID_MIC = 10_002
        const val ID_SURFACE_VIEW = 10_003
        const val ID_ENCODER = 10_004
        const val ID_LUT_IMPORT = 10_005
        const val ID_LUT = 10_006
        const val ID_THUMBNAIL = 10_007
        val Title = Property.Key<String>("name")

        val LUTS = listOf(
            "fuji3513",
            "arabica",
            "ava",
            "azrael",
            "bourbon",
            "byers",
            "chemical",
            "clayton",
            "clouseau",
            "cobi",
            "contrail",
            "cubicle",
            "django",
            "domingo",
            "faded",
            "folger",
            "fusion",
            "hyla",
            "identity",
            "invert",
            "korben",
            "lenox",
            "lucky",
            "mckinnon",
            "milo",
            "neon",
            "paladin",
            "pasadena",
            "pitaya",
            "reeve",
            "remy",
            "sprocket",
            "teigen",
            "trent",
            "tweed",
            "vireo",
            "zed",
            "zeke"
        )
    }
}