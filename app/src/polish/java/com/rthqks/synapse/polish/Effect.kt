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
            "identity",
            "invert",
            "yellow_film_01",
            "action_magenta_01",
            "action_red_01",
            "adventure_1453",
            "agressive_highlights",
            "bleech_bypass_green",
            "bleech_bypass_yellow_01",
            "blue_dark",
            "bright_green_01",
            "brownish",
            "colorful_0209",
            "conflict_01",
            "contrast_highlights",
            "contrasty_afternoon",
            "cross_process_cp3",
            "cross_process_cp4",
            "cross_process_cp6",
            "cross_process_cp14",
            "cross_process_cp15",
            "cross_process_cp16",
            "cross_process_cp18",
            "cross_process_cp130",
            "dark_green_02",
            "dark_green",
            "dark_place_01",
            "dream_1",
            "dream_85",
            "faded_retro_01",
            "faded_retro_02",
            "film_0987",
            "film_9879",
            "film_high_contrast",
            "flat_30",
            "green_2025",
            "green_action",
            "green_afternoon",
            "green_conflict",
            "green_day_01",
            "green_day_02",
            "green_g09",
            "green_indoor",
            "green_light",
            "harsh_day",
            "harsh_sunset",
            "highlights_protection",
            "indoor_blue",
            "low_contrast_blue",
            "low_key_01",
            "magenta_day_01",
            "magenta_day",
            "magenta_dream",
            "memories",
            "moonlight_01",
            "mostly_blue",
            "muted_01",
            "only_red_and_blue",
            "only_red",
            "operation_yellow",
            "orange_dark_4",
            "orange_dark_7",
            "orange_dark_look",
            "orange_underexposed",
            "protect_highlights_01",
            "red_afternoon_01",
            "red_day_01",
            "red_dream_01",
            "retro_brown_01",
            "retro_magenta_01",
            "retro_yellow_01",
            "smart_contrast",
            "subtle_blue",
            "subtle_green",
            "yellow_55b"
        )
    }
}