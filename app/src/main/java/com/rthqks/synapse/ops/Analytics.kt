package com.rthqks.synapse.ops

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Analytics @Inject constructor(
    private val context: Context
) {
    private val firebase = FirebaseAnalytics.getInstance(context)

    fun logEvent(event: Event) {
        val bundle = Bundle()
        event.param.forEach {
            when (it.type) {
                String::class.java -> bundle.putString(it.name, it.value as String)
                Long::class.java -> bundle.putLong(it.name, it.value as Long)
                Double::class.java -> bundle.putDouble(it.name, it.value as Double)
            }
        }
        firebase.logEvent(event.name, bundle)
    }

    sealed class Event(
        val name: String,
        vararg val param: Param<*>
    ) {
        class SetEffect(effectName: String) : Event(
            "set_effect",
            Param("effect_name", effectName, String::class.java)
        )

        class RecordStart(effectName: String) : Event(
            "record_start",
            Param("effect_name", effectName, String::class.java)
        )

        class RecordStop(effectName: String, duration: Long) : Event(
            "record_stop",
            Param("effect_name", effectName, String::class.java),
            Param("duration", duration, Long::class.java)
        )

        class OpenSettings : Event("open_settings")
        class EditSetting(settingName: String, settingValue: String) : Event(
            "edit_setting",
            Param("setting_name", settingName, String::class.java),
            Param("setting_value", settingValue, String::class.java)
        )

        class OpenGallery : Event("open_gallery")
        class ViewVideo : Event("view_video")
        class ShareVideo : Event("share")
        class PolishInit(supported: Boolean, permissionsGranted: Boolean) : Event(
            "polish_init",
            Param("device_supported", supported.toString(), String::class.java),
            Param("permissions_granted", permissionsGranted.toString(), String::class.java)
        )
    }

    class Param<T>(
        val name: String,
        val value: T,
        val type: Class<T>
    )
}