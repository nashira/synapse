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
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rthqks.synapse.R
import com.rthqks.synapse.build.BuilderActivity
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Property
import com.rthqks.synapse.ops.Analytics
import com.rthqks.synapse.ui.Choice
import com.rthqks.synapse.ui.PropertiesAdapter
import com.rthqks.synapse.ui.propertiesUi
import com.rthqks.synapse.util.throttleClick
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.android.synthetic.main.activity_polish.*
import kotlinx.android.synthetic.main.layout_effect.view.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject


class PolishActivity : DaggerAppCompatActivity() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var analytics: Analytics
    lateinit var viewModel: PolishViewModel
    private lateinit var orientationEventListener: OrientationEventListener
    private lateinit var preferences: SharedPreferences
    private val deviceSupported = CompletableDeferred<Boolean>()
    private val permissionsGranted = CompletableDeferred<Boolean>()
    private var uiAngle = 0
    private val interpolator = AccelerateDecelerateInterpolator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polish)
        viewModel = ViewModelProvider(this, viewModelFactory)[PolishViewModel::class.java]

        val behavior = BottomSheetBehavior.from(bottom_sheet)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN

        preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val finishedIntro = preferences.getBoolean(FINISHED_INTRO, false)

        val states = checkPermissions()

        Log.d(TAG, "permissions $states")

        val neededPermissions = states.filter { !it.granted }.map { it.name }

        if (neededPermissions.isNotEmpty()) {
            requestPermissions(neededPermissions.toTypedArray(), 123)
        } else {
            permissionsGranted.complete(true)
        }

        viewModel.deviceSupported.observe(this, Observer {
            deviceSupported.complete(it)
        })

        var rotation = 0
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(angle: Int) {
                val update = when (angle) {
                    in 45..135 -> 90
                    in 135..225 -> 180
                    in 225..315 -> 270
                    else -> 0
                }

                if (rotation != update) {
                    rotation = update
                    viewModel.setDeviceOrientation(rotation)

                    uiAngle = when (angle) {
                        in 45..135 -> -90
                        in 135..225 -> 180
                        in 225..315 -> 90
                        else -> 0
                    }
                    setUiOrientation(uiAngle)
                }
            }
        }

        viewModel.viewModelScope.launch {
            val supported = deviceSupported.await()
            val permissions = permissionsGranted.await()

            analytics.logEvent(Analytics.Event.PolishInit(supported, permissions))

            when {
                !supported -> {
                    Toast.makeText(this@PolishActivity, "device not supported", Toast.LENGTH_LONG)
                        .show()
                    finish()
                }
                !permissions -> {
                    Toast.makeText(this@PolishActivity, "Permissions Required", Toast.LENGTH_LONG)
                        .show()
                    finish()
                }
                else -> {
                    if (finishedIntro) {
                        onReady()
                    } else {
                        showIntro()
                    }
                }
            }
        }
    }

    private fun showIntro() {
        preferences.edit {
            putBoolean(FINISHED_INTRO, true)
        }
        onReady()
    }

    private fun onReady() {
        viewModel.initializeEffect()
        var recording = false
        viewModel.setSurfaceView(surface_view)
        val behavior = BottomSheetBehavior.from(bottom_sheet)

        viewModel.bottomSheetState.observe(this, Observer {
            behavior.state = it
        })

//        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
//            private var started = false
//
//            override fun onSlide(bottomSheet: View, slideOffset: Float) {
//            }
//
//            override fun onStateChanged(bottomSheet: View, newState: Int) {
//                Log.d(TAG, "state changed $newState")
//                when (newState) {
//                    BottomSheetBehavior.STATE_HIDDEN -> {
//                        Log.d(TAG, "stop lut previews")
//                        started = false
//                        viewModel.stopLutPreview()
//                    }
//                    else -> {
//                        if (!started) {
//                            Log.d(TAG, "start lut previews")
//                            started = true
//                            viewModel.startLutPreview()
//                        }
//                    }
//                }
//            }
//        })

        button_edit.setOnClickListener {
            viewModel.currentEffect?.id?.let { id ->
                viewModel.releaseContext()
                startActivity(BuilderActivity.getIntent(this, id))
            }
        }

        button_color.setOnClickListener {
            Log.d(TAG, "show luts")
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            supportFragmentManager.commit {
                replace(R.id.bottom_sheet, LutFragment())
            }
        }

        button_settings.setOnClickListener(throttleClick {
            Log.d(TAG, "show settings")
            analytics.logEvent(Analytics.Event.OpenSettings())

            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            supportFragmentManager.commit {
                replace(R.id.bottom_sheet, SettingsFragment())
            }
//            Log.d(TAG, "flip camera")
//            viewModel.flipCamera()
        })

        var updateRecordingTime: Job? = null
        button_record.setOnClickListener(throttleClick {
            recording = !recording
            if (recording) {
                focusMode()
                viewModel.startRecording()
                video_duration.visibility = View.VISIBLE
                updateRecordingTime = viewModel.updateRecordingTime(video_duration)
            } else {
                exploreMode()
                highlightGalleryButton()
                video_duration.visibility = View.GONE
                updateRecordingTime?.cancel()
                viewModel.stopRecording()
            }
            Log.d(TAG, "recording $recording")
        })

        button_effects.setOnClickListener {
        }

        button_gallery.setOnClickListener {
            analytics.logEvent(Analytics.Event.OpenGallery())
            Intent(this, GalleryActivity::class.java).also {
                startActivity(it)
            }
        }

        val effectAdapter = EffectAdapter { property, choice ->
            viewModel.setEffectProperty(property)
        }
        effect_list.adapter = effectAdapter

        var effects = emptyList<Network>()
        viewModel.effects.observe(this, Observer {
            effects = it
            effectAdapter.setEffects(it)
        })

        effect_list.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val effect = effects[position]
                viewModel.setEffect(effect)
                Log.d(TAG, "pos $position $effect")
            }
        })
    }

    private fun setUiOrientation(rotation: Int) {
        listOf(
            button_settings,
            button_effects,
            button_color,
            button_gallery
        ).forEach {
            it.animate()
                .rotation(rotation.toFloat())
                .setInterpolator(interpolator)
                .setDuration(200)
        }
    }

    private fun highlightGalleryButton() {
        button_gallery.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .setInterpolator {
                kotlin.math.sin(it * 6 * Math.PI).toFloat()
            }
            .setDuration(600)
            .withEndAction {
                button_gallery.scaleX = 1f
                button_gallery.scaleY = 1f
            }
            .start()
    }

    private fun focusMode() {
        listOf(
            button_settings,
            button_effects,
            button_gallery
        ).forEach {
            it.animate()
                .setInterpolator(interpolator)
                .alpha(0f)
                .setDuration(200)
                .withEndAction { it.visibility = View.GONE }
                .start()
        }
    }

    private fun exploreMode() {
        listOf(
            button_settings,
            button_effects,
            button_gallery
        ).forEach {
            it.visibility = View.VISIBLE
            it.animate()
                .setInterpolator(interpolator)
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
        permissionsGranted.complete(neededPermissions.isEmpty())
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
        ) else listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}

private data class Permission(val name: String, val granted: Boolean, val showRationale: Boolean)

private class EffectAdapter(
    private val onClick: (Property, Choice<*>) -> Unit
) : RecyclerView.Adapter<EffectViewHolder>() {
    private var effects: List<Network> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EffectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_effect, parent, false)
        return EffectViewHolder(view, onClick)
    }

    override fun getItemCount(): Int {
        return effects.size
    }

    override fun onBindViewHolder(holder: EffectViewHolder, position: Int) {
        holder.bind(effects[position])
    }

    fun setEffects(list: List<Network>) {
        effects = list
        notifyDataSetChanged()
    }
}

private class EffectViewHolder(
    itemView: View,
    onClick: (Property, Choice<*>) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val nameView = itemView.name
    private val settingsList = itemView.settings_list
    private val adapter = PropertiesAdapter(onClick)
    private var network: Network? = null

    init {
        settingsList.adapter = adapter
    }

    fun bind(network: Network) {
        this.network = network
        nameView.text = network.name
        adapter.setProperties(network.propertiesUi())
    }
}

