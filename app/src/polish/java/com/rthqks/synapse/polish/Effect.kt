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
            "assets:///cube/arabica.bcube",
            "assets:///cube/ava.bcube",
            "assets:///cube/azrael.bcube",
            "assets:///cube/bourbon.bcube",
            "assets:///cube/byers.bcube",
            "assets:///cube/chemical.bcube",
            "assets:///cube/clayton.bcube",
            "assets:///cube/clouseau.bcube",
            "assets:///cube/cobi.bcube",
            "assets:///cube/contrail.bcube",
            "assets:///cube/cubicle.bcube",
            "assets:///cube/django.bcube",
            "assets:///cube/domingo.bcube",
            "assets:///cube/faded.bcube",
            "assets:///cube/folger.bcube",
            "assets:///cube/fusion.bcube",
            "assets:///cube/hyla.bcube",
            "assets:///cube/identity.bcube",
            "assets:///cube/invert.bcube",
            "assets:///cube/korben.bcube",
            "assets:///cube/lenox.bcube",
            "assets:///cube/lucky.bcube",
            "assets:///cube/mckinnon.bcube",
            "assets:///cube/milo.bcube",
            "assets:///cube/neon.bcube",
            "assets:///cube/paladin.bcube",
            "assets:///cube/pasadena.bcube",
            "assets:///cube/pitaya.bcube",
            "assets:///cube/reeve.bcube",
            "assets:///cube/remy.bcube",
            "assets:///cube/sprocket.bcube",
            "assets:///cube/teigen.bcube",
            "assets:///cube/trent.bcube",
            "assets:///cube/tweed.bcube",
            "assets:///cube/vireo.bcube",
            "assets:///cube/zed.bcube",
            "assets:///cube/zeke.bcube"
        )
    }
}