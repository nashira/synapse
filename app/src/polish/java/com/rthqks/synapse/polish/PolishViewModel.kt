package com.rthqks.synapse.polish

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import android.view.TextureView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rthqks.synapse.R
import com.rthqks.synapse.data.PropertyData
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.logic.*
import com.rthqks.synapse.ops.Analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PolishViewModel @Inject constructor(
//    private val executor: ExecutorLegacy,
    private val context: ExecutionContext,
    private val dao: SynapseDao,
    private val analytics: Analytics
) : ViewModel() {
    val deviceSupported = MutableLiveData<Boolean>()
    private var currentEffect: Effect? = null
    private var recordingStart = 0L
    private var svSetup = false
    private var stopped = true
    private val effectExecutor = EffectExecutor(context)

    val properties = context.properties

    init {
        context.properties.apply {
            put(
                Property(
                    CameraFacing,
                    ChoiceType(
                        R.string.property_name_camera_device,
                        R.drawable.ic_switch_camera,
                        Choice(
                            CameraCharacteristics.LENS_FACING_BACK,
                            R.string.property_label_camera_lens_facing_back
                        ),
                        Choice(
                            CameraCharacteristics.LENS_FACING_FRONT,
                            R.string.property_label_camera_lens_facing_front
                        )
                    ), CameraCharacteristics.LENS_FACING_BACK, true
                ), IntConverter
            )
            put(
                Property(
                    FrameRate,
                    ChoiceType(
                        R.string.property_name_frame_rate,
                        R.drawable.ic_speed,
                        Choice(
                            10,
                            R.string.property_label_camera_fps_10
                        ),
                        Choice(
                            15,
                            R.string.property_label_camera_fps_15
                        ),
                        Choice(
                            20,
                            R.string.property_label_camera_fps_20
                        ),
                        Choice(
                            30,
                            R.string.property_label_camera_fps_30
                        ),
                        Choice(
                            60,
                            R.string.property_label_camera_fps_60
                        )
                    ), 30, true
                ), IntConverter
            )
            put(
                Property(
                    VideoSize,
                    ChoiceType(
                        R.string.property_name_camera_capture_size,
                        R.drawable.ic_photo_size_select,
                        Choice(
                            Size(3840, 2160),
                            R.string.property_label_camera_capture_size_2160
                        ),
                        Choice(
                            Size(1920, 1080),
                            R.string.property_label_camera_capture_size_1080
                        ),
                        Choice(
                            Size(1280, 720),
                            R.string.property_label_camera_capture_size_720
                        ),
                        Choice(
                            Size(640, 480),
                            R.string.property_label_camera_capture_size_480
                        )
                    ), Size(1280, 720), true
                ), SizeConverter
            )
            put(
                Property(
                    Stabilize,
                    ToggleType(
                        R.string.property_name_camera_stabilize, R.drawable.ic_texture,
                        R.string.property_label_on,
                        R.string.property_label_off
                    ), value = true, requiresRestart = true
                ), BooleanConverter
            )
            put(
                Property(
                    Recording,
                    ToggleType(
                        R.string.property_name_recording,
                        R.drawable.ic_movie,
                        R.string.property_name_recording,
                        R.string.property_name_recording
                    ), false
                ), BooleanConverter
            )
            put(
                Property(
                    Rotation,
                    ChoiceType(
                        R.string.property_name_rotation,
                        R.drawable.ic_rotate_left,
                        Choice(0, R.string.property_label_rotation_0),
                        Choice(90, R.string.property_label_rotation_90),
                        Choice(270, R.string.property_label_rotation_270),
                        Choice(180, R.string.property_label_rotation_180)
                    ), 0
                ), IntConverter
            )
            put(
                Property(
                    LutUri,
                    UriType(R.string.property_name_uri, R.drawable.ic_image, "*/*"),
                    Uri.parse("assets:///cube/identity.bcube"), true
                ), UriConverter
            )
            put(
                Property(
                    LutStrength,
                    PropertyType.RangeType(
                        R.string.property_name_lut_strength,
                        R.drawable.ic_rotate_left,
                        0f..1f
                    ),
                    1f
                ), FloatConverter
            )
        }

        viewModelScope.launch {
//            executor.initialize(false)
//            executor.await()
            val p = withContext(Dispatchers.IO) {
                dao.getProperties(0)
            }

            p.forEach { properties[it.key] = it.value }

            effectExecutor.setup()
            deviceSupported.value = context.glesManager.supportedDevice
        }
    }

    fun initializeEffect() {
        viewModelScope.launch {
            effectExecutor.initializeEffect()
        }
    }

    private var surfaceView: SurfaceView? = null
    private var network: Network? = null

    fun setSurfaceView(surfaceView: SurfaceView?) {
        this.surfaceView = surfaceView
    }

    fun flipCamera() {
        val facing = currentEffect?.let {
            val facing = when (properties[CameraFacing]) {
                CameraCharacteristics.LENS_FACING_BACK ->
                    CameraCharacteristics.LENS_FACING_FRONT
                CameraCharacteristics.LENS_FACING_FRONT ->
                    CameraCharacteristics.LENS_FACING_BACK
                else -> CameraCharacteristics.LENS_FACING_BACK
            }
            properties[CameraFacing] = facing
            facing
        }
        val string = if (facing == 0) "front" else "back"
        analytics.logEvent(Analytics.Event.EditSetting(CameraFacing.name, string))
    }

    fun startExecution() = viewModelScope.launch {
        if (stopped) {
            stopped = false
            Log.d(TAG, "resume")
            effectExecutor.resume()
        }
    }

    fun stopExecution() = viewModelScope.launch {
        if (!stopped) {
            stopped = true
            Log.d(TAG, "pause")

            properties[Recording] = false
            effectExecutor.pause()
        }
    }

    fun startRecording() {
        val effectName = currentEffect?.title ?: "unknown"
        analytics.logEvent(Analytics.Event.RecordStart(effectName))
        recordingStart = SystemClock.elapsedRealtime()
        properties[Recording] = true
    }

    fun stopRecording() {
        val effectName = currentEffect?.title ?: "unknown"
        analytics.logEvent(
            Analytics.Event.RecordStop(
                effectName,
                SystemClock.elapsedRealtime() - recordingStart
            )
        )
        properties[Recording] = false
    }

    fun <T> editProperty(
        key: Property.Key<T>,
        value: T
    ) {
        analytics.logEvent(Analytics.Event.EditSetting(key.name, value.toString()))
        properties[key] = value

        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProperty(
                PropertyData(
                    0, 0,
                    key.name,
                    properties.toString(key)
                )
            )
        }
    }

    fun setEffect(effect: Effect): Boolean {
        currentEffect = effect
        network = effect.network

        analytics.logEvent(Analytics.Event.SetEffect(effect.title))
        viewModelScope.launch {
            effectExecutor.swapEffect(effect).await()
            if (!svSetup) {
                svSetup = true
                surfaceView?.let { effectExecutor.setSurfaceView(it) }
            }
        }
        return true
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared")

        // viewModelScope is already cancelled here
        CoroutineScope(Dispatchers.IO).launch {
            effectExecutor.removeAllLinks()
            effectExecutor.removeAllNodes()
            effectExecutor.release()
        }

//        executor.releaseNetwork()
//        executor.release()

        Log.d(TAG, "released")
        super.onCleared()
    }

    fun setDeviceOrientation(orientation: Int) {
        properties[Rotation] = orientation
    }

    fun setLut(lut: String) {
        viewModelScope.launch {
            val uri = Uri.parse("assets:///cube/$lut.bcube")
            editProperty(LutUri, uri)
            effectExecutor.setLut(lut)
        }
    }

    fun startLutPreview() {
        viewModelScope.launch {
            effectExecutor.startLutPreview()
        }
    }

    fun stopLutPreview() {
        viewModelScope.launch {
            effectExecutor.stopLutPreview()
        }
    }

    fun registerLutPreview(textureView: TextureView, lut: String) {
        viewModelScope.launch {
            effectExecutor.registerLutPreview(textureView, lut)
        }
    }

    fun unregisterLutPreview(surfaceTexture: SurfaceTexture) {
        viewModelScope.launch {
            effectExecutor.unregisterLutPreview(surfaceTexture)
        }
    }

    fun setLutStrength(strength: Float) {
        properties[LutStrength] = strength
    }

    companion object {
        const val TAG = "PolishViewModel"
    }
}
