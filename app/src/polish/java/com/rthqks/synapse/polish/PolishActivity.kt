package com.rthqks.synapse.polish

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.view.doOnLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rthqks.synapse.R
import com.rthqks.synapse.util.throttleClick
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.polish.activity_polish.*
import javax.inject.Inject


class PolishActivity : DaggerAppCompatActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    lateinit var viewModel: PolishViewModel
    private lateinit var orientationEventListener:  OrientationEventListener
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polish)
        viewModel = ViewModelProvider(this, viewModelFactory)[PolishViewModel::class.java]

        preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val finishedIntro = preferences.getBoolean(FINISHED_INTRO, false)

        val states = checkPermissions()

        Log.d(TAG, "permissions $states")

        val neededPermissions = states.filter { !it.granted }.map { it.name }

        if (neededPermissions.isNotEmpty()) {
            requestPermissions(neededPermissions.toTypedArray(), 123)
            return
        }

        viewModel.deviceSupported.observe(this, Observer {
            if (!it) {
                Toast.makeText(this, "device not supported", Toast.LENGTH_LONG).show()
                finish()
                return@Observer
            }

            if (finishedIntro) {
                onReady()
            } else {
                showIntro()
            }
        })


        var orientation = 0
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(angle: Int) {
                val update = when (angle) {
                    in 45..135 -> 90
                    in 135..225 -> 180
                    in 225..315 -> 270
                    else -> 0
                }

                if (orientation != update) {
                    orientation = update
                    viewModel.setDeviceOrientation(orientation)
                }
            }
        }
    }

    private fun setUiOrientation(orientation: Int) {

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

        viewModel.setSurfaceView(surface_view)

        button_color.setOnClickListener {
            Log.d(TAG, "show luts")
            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }

        button_camera.setOnClickListener(throttleClick {
            Log.d(TAG, "flip camera")
            viewModel.flipCamera()
        })

        button_record.setOnClickListener(throttleClick {
            recording = !recording
            if (recording) {
                focusMode()
                viewModel.startRecording()
            } else {
                exploreMode()
                highlightGalleryButton()
                viewModel.stopRecording()
            }
            Log.d(TAG, "recording $recording")
        })

        button_settings.setOnClickListener {
            Log.d(TAG, "show settings")
            SettingsDialog().show(supportFragmentManager, "settings")
        }

        button_gallery.setOnClickListener {
            Intent(this, GalleryActivity::class.java).also {
                startActivity(it)
            }
        }

        val snapHelper = LinearSnapHelper()
        val layoutManager = effect_list.layoutManager as LinearLayoutManager
        effect_list.adapter = EffectAdapter {
            val view = layoutManager.findViewByPosition(it)!!
            val snapDistance = snapHelper.calculateDistanceToFinalSnap(layoutManager, view)!!
            if (snapDistance[0] != 0 || snapDistance[1] != 0) {
                effect_list.smoothScrollBy(snapDistance[0], snapDistance[1])
            }
        }

        effect_list.doOnLayout {
            Log.d(TAG, "onLayout")
            it.setPadding(it.width / 2, 0, it.width / 2, 0)
            snapHelper.attachToRecyclerView(effect_list)
        }

        effect_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var oldPos = -1
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val view = snapHelper.findSnapView(layoutManager)
                    val pos = view?.let { effect_list.getChildAdapterPosition(it) } ?: 0
                    if (oldPos != pos) {
                        val effect = Effect.values()[pos]
                        val changed = viewModel.setEffect(effect)
                        if (changed) {
                            oldPos = pos
                        }
                        Log.d(TAG, "pos $pos $effect")
                    }
                }
            }
        })
    }

    private fun highlightGalleryButton() {
        button_gallery.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .setInterpolator {
                kotlin.math.sin(it * 4 * Math.PI).toFloat()
            }
            .setDuration(500)
            .start()
    }

    private fun focusMode() {
        listOf(
            button_camera,
            button_settings,
//            button_color,
            effect_list
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
//            button_color,
            effect_list
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

    override fun onResume() {
        super.onResume()
        orientationEventListener.enable()
        viewModel.startExecution()
    }

    override fun onPause() {
        super.onPause()
        orientationEventListener.disable()
        viewModel.stopExecution()
    }

    private fun hideSystemUI() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        val PERMISSIONS = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) else  listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}

private data class Permission(val name: String, val granted: Boolean, val showRationale: Boolean)

enum class Effect(
    val title: String
) {
    None("none"),
    TimeWarp("time warp"),
    Sparkle("sparkle"),
    Trip("trip"),
    Topography("topography")
}

private class EffectAdapter(
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<EffectViewHolder>() {
    private val effects = Effect.values()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EffectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_effect, parent, false)
        return EffectViewHolder(view, onClick)
    }

    override fun getItemCount(): Int {
        return effects.size
    }

    override fun onBindViewHolder(holder: EffectViewHolder, position: Int) {
        (holder.itemView as TextView).text = effects[position].title
    }
}

private class EffectViewHolder(
    itemView: View,
    onClick: (Int) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    init {
        itemView.setOnClickListener {
            onClick(adapterPosition)
        }
    }
}