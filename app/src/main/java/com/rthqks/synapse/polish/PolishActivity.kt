package com.rthqks.synapse.polish

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rthqks.synapse.R
import com.rthqks.synapse.build.BuilderActivity
import com.rthqks.synapse.databinding.ActivityPolishBinding
import com.rthqks.synapse.ops.Analytics
import com.rthqks.synapse.ui.ConfirmDialog
import com.rthqks.synapse.ui.PropertiesAdapter
import com.rthqks.synapse.ui.propertiesUi
import com.rthqks.synapse.util.throttleClick
import dagger.android.support.DaggerAppCompatActivity
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
    private lateinit var binding: ActivityPolishBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPolishBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this, viewModelFactory)[PolishViewModel::class.java]

        val behavior = BottomSheetBehavior.from(binding.bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN

        preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val finishedIntro = preferences.getBoolean(FINISHED_INTRO, false)

        val states = checkPermissions()

        Log.d(TAG, "permissions: $states")

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

        lifecycleScope.launch {
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
        viewModel.setSurfaceView(binding.surfaceView)
        val behavior = BottomSheetBehavior.from(binding.bottomSheet)

        viewModel.bottomSheetState.observe(this, Observer {
            behavior.state = it
        })

        val adapter = PropertiesAdapter { p, c ->
            viewModel.setEffectProperty(p)
        }

        binding.effectSettings.adapter = adapter
        viewModel.currentEffectLive.observe(this, Observer {
            binding.effectName.text = it.name
            adapter.setProperties(it.propertiesUi())
        })

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                Log.d(TAG, "bottomsheet state: $newState")
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    Log.d(TAG, "stop lut previews")
                    viewModel.stopLutPreview()
                }
            }
        })

        binding.buttonEdit.setOnClickListener {
            viewModel.currentEffect?.let { effect ->
                if (effect.creatorId != viewModel.currentUser?.id) {
                    ConfirmDialog(
                        R.string.title_copy_effect,
                        R.string.button_cancel,
                        R.string.button_copy
                    ) {
                        if (it) {
                            val id = viewModel.makeCopy(effect)
                            viewModel.resumingEffectId = id
                            viewModel.releaseContext()
                            startActivity(BuilderActivity.getIntent(this, id))
                        }
                    }.show(supportFragmentManager, null)
                } else {
                    viewModel.releaseContext()
                    startActivity(BuilderActivity.getIntent(this, effect.id))
                }
            }
        }

        binding.buttonColor.setOnClickListener {
            Log.d(TAG, "show luts")
            analytics.logEvent(Analytics.Event.OpenLuts())
            supportFragmentManager.commitNow {
                replace(R.id.bottom_sheet, LutFragment())
            }
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            viewModel.startLutPreview()
        }

        binding.buttonSettings.setOnClickListener {
            Log.d(TAG, "show settings")
            analytics.logEvent(Analytics.Event.OpenSettings())

            supportFragmentManager.commitNow {
                replace(R.id.bottom_sheet, SettingsFragment())
            }
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        var updateRecordingTime: Job? = null
        binding.buttonRecord.setOnClickListener(throttleClick {
            recording = !recording
            if (recording) {
                focusMode()
                viewModel.startRecording()
                binding.videoDuration.visibility = View.VISIBLE
                updateRecordingTime = viewModel.updateRecordingTime(binding.videoDuration)
            } else {
                exploreMode()
                highlightGalleryButton()
                binding.videoDuration.visibility = View.GONE
                updateRecordingTime?.cancel()
                viewModel.stopRecording()
            }
            Log.d(TAG, "recording $recording")
        })

        binding.buttonEffects.setOnClickListener {
            showEffects(behavior)
        }

        binding.effectName.setOnClickListener {
            showEffects(behavior)
        }

        binding.buttonGallery.setOnClickListener {
            analytics.logEvent(Analytics.Event.OpenGallery())
            Intent(this, GalleryActivity::class.java).also {
                startActivity(it)
            }
        }
    }

    private fun showEffects(behavior: BottomSheetBehavior<FrameLayout>) {
        Log.d(TAG, "show effects")
        analytics.logEvent(Analytics.Event.OpenEffects())

        supportFragmentManager.commitNow {
            replace(R.id.bottom_sheet, EffectsFragment())
        }
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun setUiOrientation(rotation: Int) {
        listOf(
            binding.buttonSettings,
            binding.buttonEffects,
            binding.buttonColor,
            binding.buttonGallery
        ).forEach {
            it.animate()
                .rotation(rotation.toFloat())
                .setInterpolator(interpolator)
                .setDuration(200)
        }
    }

    private fun highlightGalleryButton() {
        binding.buttonGallery.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .setInterpolator {
                kotlin.math.sin(it * 6 * Math.PI).toFloat()
            }
            .setDuration(600)
            .withEndAction {
                binding.buttonGallery.scaleX = 1f
                binding.buttonGallery.scaleY = 1f
            }
            .start()
    }

    private fun focusMode() {
        listOf(
            binding.buttonSettings,
            binding.buttonEffects,
            binding.buttonGallery
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
            binding.buttonSettings,
            binding.buttonEffects,
            binding.buttonGallery
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