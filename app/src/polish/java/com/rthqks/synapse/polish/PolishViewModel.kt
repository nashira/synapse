package com.rthqks.synapse.polish

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import android.view.TextureView
import android.widget.TextView
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rthqks.synapse.data.PropertyData
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.NodeDef.BCubeImport.LutUri
import com.rthqks.synapse.logic.NodeDef.Camera.CameraFacing
import com.rthqks.synapse.logic.NodeDef.Camera.FrameRate
import com.rthqks.synapse.logic.NodeDef.Camera.Stabilize
import com.rthqks.synapse.logic.NodeDef.Camera.VideoSize
import com.rthqks.synapse.logic.NodeDef.Lut3d.LutStrength
import com.rthqks.synapse.logic.NodeDef.MediaEncoder.Recording
import com.rthqks.synapse.logic.NodeDef.MediaEncoder.Rotation
import com.rthqks.synapse.logic.Property
import com.rthqks.synapse.logic.SyncLogic
import com.rthqks.synapse.logic.toNetwork
import com.rthqks.synapse.ops.Analytics
import kotlinx.coroutines.*
import javax.inject.Inject

class PolishViewModel @Inject constructor(
    private val context: ExecutionContext,
    private val dao: SynapseDao,
    private val syncLogic: SyncLogic,
    private val analytics: Analytics
) : ViewModel() {
    val effects = MediatorLiveData<List<Network>>()
    val deviceSupported = MutableLiveData<Boolean>()
    private var currentEffect: Network? = null
    private var recordingStart = 0L
    private val recordingDuration: Long get() = SystemClock.elapsedRealtime() - recordingStart
    private var svSetup = false
    private var stopped = true
    private val effectExecutor = EffectExecutor(context)

    val properties = context.properties

    init {
        properties[CameraFacing] = CameraCharacteristics.LENS_FACING_BACK
        properties[FrameRate] = 30
        properties[VideoSize] = Size(1280, 720)
        properties[Stabilize] = true
        properties[Recording] = false
        properties[Rotation] = 0
        properties[LutUri] = Uri.parse("assets:///cube/identity.bcube")
        properties[LutStrength] = 1f

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.getProperties(0).forEach { properties.fromString(it.type, it.key, it.value) }

//                listOf(Effects.timeWarp, Effects.rotoHue, Effects.quantizer).forEach { effect ->
//                    dao.getProperties(effect.network.id)
//                        .forEach { effect.properties.fromString(it.type, it.key, it.value) }
//                }
            }

            effectExecutor.setup()
            deviceSupported.value = context.glesManager.supportedDevice
        }
    }

    fun initializeEffect() {
        viewModelScope.launch {
            effectExecutor.initializeEffect()
            syncLogic.refreshEffects()
            val networks = dao.getNetworks().map { it.toNetwork() }
            effects.postValue(networks)
        }
    }

    private var surfaceView: SurfaceView? = null

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
        val effectName = currentEffect?.name ?: "unknown"
        analytics.logEvent(Analytics.Event.RecordStart(effectName))
        recordingStart = SystemClock.elapsedRealtime()
        properties[Recording] = true
    }

    fun stopRecording() {
        val effectName = currentEffect?.name ?: "unknown"
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
        properties[key] = value
        val property = properties.getProperty(key)!!
        val type = property.getType()
        val string = property.getString()
        analytics.logEvent(Analytics.Event.EditSetting(key.name, string))

        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProperty(
                PropertyData(
                    0, 0,
                    type,
                    key.name,
                    string,
                    property.exposed
                )
            )
        }
    }

    fun setEffect(effect: Network): Boolean {
        currentEffect = effect

        analytics.logEvent(Analytics.Event.SetEffect(effect.name))
        viewModelScope.launch {
            effectExecutor.swapEffect(effect)
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
            effectExecutor.removeAll()
            effectExecutor.release()
            Log.d(TAG, "released")
        }

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
        effectExecutor.registerLutPreview(textureView, lut)
    }

    fun unregisterLutPreview(surfaceTexture: SurfaceTexture) {
        effectExecutor.unregisterLutPreview(surfaceTexture)
    }

    fun setLutStrength(strength: Float) {
        properties[LutStrength] = strength
    }

    fun setEffectProperty(property: Property<*>) {
        currentEffect?.let {
            viewModelScope.launch(Dispatchers.IO) {
                dao.insertProperty(
                    PropertyData(
                        it.id, 0,
                        property.getType(),
                        property.key.name,
                        property.getString(),
                        property.exposed
                    )
                )
            }
        }
    }

    fun updateRecordingTime(textView: TextView): Job {
        return viewModelScope.launch {
            while (isActive) {
                var d = recordingDuration

                var minutes: Long = 0
                var seconds: Long = 0
                var hundreths: Long = 0
                if (d >= 60000) {
                    minutes = d / 60000
                    d -= minutes * 60000
                }
                if (d >= 1000) {
                    seconds = d / 1000
                    d -= seconds * 1000
                }
                hundreths = d / 10
                textView.text = String.format("%d:%02d.%02d", minutes, seconds, hundreths)
                delay(16)
            }
        }
    }

    companion object {
        const val TAG = "PolishViewModel"
    }
}
