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
        const val ID_CAMERA = 1
        const val ID_MIC = 2
        const val ID_SURFACE_VIEW = 3
        const val ID_ENCODER = 4
        const val ID_LUT_IMPORT = 5
        val Title = Property.Key<String>("name")

        val LUTS = listOf(
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