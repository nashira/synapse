package com.rthqks.synapse.polish

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rthqks.synapse.R
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.polish.activity_polish.*


class PolishActivity : DaggerAppCompatActivity() {
    private lateinit var preferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polish)

        preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val finishedIntro = preferences.getBoolean(FINISHED_INTRO, false)

        val states = checkPermissions()

        Log.d(TAG, "permissions $states")

        val neededPermissions = states.filter { !it.granted }.map { it.name }

        if (neededPermissions.isNotEmpty()) {
            requestPermissions(neededPermissions.toTypedArray(), 123)
            return
        }

        if (finishedIntro) {
            onReady()
            return
        } else {
            showIntro()
        }
    }

    private fun showIntro() {
        preferences.edit {
            putBoolean(FINISHED_INTRO, true)
        }
        onReady()
    }

    private fun onReady() {
        val behavior = BottomSheetBehavior.from(layout_colors)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        var recording = false

        surface_view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(
                holder: SurfaceHolder?,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.d(TAG, "surfaceChanged $width $height")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                Log.d(TAG, "surfaceDestroyed")
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                Log.d(TAG, "surfaceCreated")
            }
        })

        button_color.setOnClickListener {
            Log.d(TAG, "show luts")
            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }

        button_camera.setOnClickListener {
            Log.d(TAG, "flip camera")
        }

        button_record.setOnClickListener {
            recording = !recording
            if (recording) {
                focusMode()
            } else {
                exploreMode()
            }
            Log.d(TAG, "recording $recording")
        }

        button_settings.setOnClickListener {
            Log.d(TAG, "show settings")

        }

        val snapHelper = LinearSnapHelper()

        val layoutManager = effect_list.layoutManager as LinearLayoutManager
        effect_list.adapter = EffectAdapter()

        effect_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        val view = snapHelper.findSnapView(layoutManager)
                        val pos = view?.let { effect_list.getChildAdapterPosition(it) }
                        Log.d(TAG, "pos $pos")

                    }
                }
            }
        })

        effect_list.doOnLayout {
            it.setPadding(it.width / 2, 0, it.width / 2, 0)
            effect_list.scrollToPosition(0)
            snapHelper.attachToRecyclerView(effect_list)
        }
    }

    private fun focusMode() {
        listOf(
            button_camera,
            button_settings,
            button_color
        ).forEach {
            it.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction { it.visibility = View.GONE }
                .start()
        }
    }

    private fun exploreMode() {
        listOf(
            button_camera,
            button_settings,
            button_color
        ).forEach {
            it.visibility = View.VISIBLE
            it.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    private fun checkPermissions(): List<Permission> {
        return PERMISSIONS.map {
            Permission(
                it,
                checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED,
                shouldShowRequestPermissionRationale(it)
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val perms = checkPermissions()
        val neededPermissions = perms.filter { !it.granted }
        if (neededPermissions.isEmpty()) {
            onReady()
        } else {
            Toast.makeText(this, "NEED PERMS", Toast.LENGTH_LONG).show()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    companion object {
        const val TAG = "PolishActivity"
        const val PREF_NAME = "intro"
        const val FINISHED_INTRO = "finished_intro"
        val PERMISSIONS = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}

private data class Permission(val name: String, val granted: Boolean, val showRationale: Boolean)

private class EffectAdapter : RecyclerView.Adapter<EffectViewHolder>() {
    private val effects = listOf(
        "none",
        "time warp",
        "sparkle",
        "trip",
        "topography"
    )
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EffectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_effect, parent, false)
        return EffectViewHolder(view)
    }

    override fun getItemCount(): Int {
        return effects.size
    }

    override fun onBindViewHolder(holder: EffectViewHolder, position: Int) {
        when (position) {
//            0 -> (holder.itemView as TextView).width = parentWidth / 2
//            effects.size + 1 -> (holder.itemView as TextView).width = parentWidth / 2
            else -> (holder.itemView as TextView).text = effects[position]
        }

    }
}

private class EffectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)