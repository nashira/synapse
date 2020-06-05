package com.rthqks.synapse.polish

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.widget.TextView
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.assets.VideoStorage
import com.rthqks.synapse.data.PropertyData
import com.rthqks.synapse.data.SeedData.BaseEffectId
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.NodeDef.BCubeImport.LutUri
import com.rthqks.synapse.logic.NodeDef.Camera.CameraFacing
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
    private val contxt: Context,
    private val videoStorage: VideoStorage,
    private val assetManager: AssetManager,
    private val dao: SynapseDao,
    private val syncLogic: SyncLogic,
    private val analytics: Analytics
) : ViewModel() {
    val bottomSheetState = MutableLiveData<Int>()
    val effects = MediatorLiveData<List<Network>>()
    val deviceSupported = MutableLiveData<Boolean>()
    var baseNetwork: Network? = null
    var currentEffect: Network? = null
    private var recordingStart = 0L
    private val recordingDuration: Long get() = SystemClock.elapsedRealtime() - recordingStart
    private var svSetup = false
    private var needsNewContext = false
    private var context = ExecutionContext(contxt, videoStorage, assetManager)
    private var effectExecutor = EffectExecutor(context)
    private var surfaceView: SurfaceView? = null

    init {
        viewModelScope.launch {
            baseNetwork = withContext(Dispatchers.IO) {
                if (!dao.hasNetwork(BaseEffectId)) {
                    syncLogic.refreshEffects()
                }
                syncLogic.getNetwork(BaseEffectId)
            }
            effectExecutor.setBaseNetwork(baseNetwork!!)
            effectExecutor.setup()
            deviceSupported.value = context.glesManager.supportedDevice
        }
    }

    private suspend fun recreateContext() {
        Log.d(TAG, "recreateContext")

        effectExecutor.setup()
        effectExecutor.initializeEffect()
        currentEffect?.let { setEffect(it) }
    }

    fun initializeEffect() {
        viewModelScope.launch(Dispatchers.IO) {
            effectExecutor.initializeEffect()
            val networks = dao.getNetworks().filter {
                it.id != BaseEffectId
            }.map { it.toNetwork() }
            effects.postValue(networks)
        }
    }

    fun releaseContext() {
        needsNewContext = true
        viewModelScope.launch {
            effectExecutor.removeAll()
            effectExecutor.release()
            baseNetwork = withContext(Dispatchers.IO) {
                syncLogic.getNetwork(BaseEffectId)
            }
            context = ExecutionContext(contxt, videoStorage, assetManager)
            effectExecutor = EffectExecutor(context)
            effectExecutor.setBaseNetwork(baseNetwork!!)
        }
    }

    fun setSurfaceView(surfaceView: SurfaceView?) {
        this.surfaceView = surfaceView
    }

    fun flipCamera() {
        val facing =
            effectExecutor.getProperty(EffectExecutor.ID_CAMERA, CameraFacing.name)
        val new = when (facing.value) {
            CameraCharacteristics.LENS_FACING_BACK ->
                CameraCharacteristics.LENS_FACING_FRONT
            CameraCharacteristics.LENS_FACING_FRONT ->
                CameraCharacteristics.LENS_FACING_BACK
            else -> CameraCharacteristics.LENS_FACING_BACK
        }
        editProperty(EffectExecutor.ID_CAMERA, CameraFacing, new)
    }

    fun startExecution() {
        Log.d(TAG, "resume $needsNewContext")
        viewModelScope.launch(Dispatchers.IO) {
            effectExecutor.resume()
            Log.d(TAG, "resumed")
            if (needsNewContext) {
                needsNewContext = false
                svSetup = false
                recreateContext()
            }
        }
    }

    fun stopExecution() {
        Log.d(TAG, "pause")
        viewModelScope.launch {
            effectExecutor.setProperty(EffectExecutor.ID_ENCODER, Recording, false)
            effectExecutor.pause()
        }
    }

    fun startRecording() {
        val effectName = currentEffect?.name ?: "unknown"
        analytics.logEvent(Analytics.Event.RecordStart(effectName))
        recordingStart = SystemClock.elapsedRealtime()
        effectExecutor.setProperty(EffectExecutor.ID_ENCODER, Recording, true)
    }

    fun stopRecording() {
        val effectName = currentEffect?.name ?: "unknown"
        analytics.logEvent(
            Analytics.Event.RecordStop(
                effectName,
                SystemClock.elapsedRealtime() - recordingStart
            )
        )
        effectExecutor.setProperty(EffectExecutor.ID_ENCODER, Recording, false)
    }

    fun <T : Any> editProperty(
        nodeId: Int,
        key: Property.Key<T>,
        value: T
    ) {
        val property = effectExecutor.getProperty(nodeId, key.name)
        property.value = value
        val type = property.type
        val string = property.stringValue
        analytics.logEvent(Analytics.Event.EditSetting(key.name, string))

        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProperty(
                PropertyData(
                    BaseEffectId, property.nodeId,
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
        effectExecutor.setProperty(EffectExecutor.ID_ENCODER, Rotation, orientation)
    }

    fun setLut(lut: String) {
        viewModelScope.launch {
            val uri = Uri.parse("assets:///cube/$lut.bcube")
            editProperty(EffectExecutor.ID_LUT_IMPORT, LutUri, uri)
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

    fun getLutStrength(): Float = effectExecutor.getLutStrength()

    fun setLutStrength(strength: Float) {
        effectExecutor.setProperty(EffectExecutor.ID_LUT, LutStrength, strength)
    }

    fun setEffectProperty(property: Property) {
        currentEffect?.let {
            viewModelScope.launch(Dispatchers.IO) {
                dao.insertProperty(
                    PropertyData(
                        property.networkId,
                        property.nodeId,
                        property.type,
                        property.key.name,
                        property.stringValue,
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
                var hundredths: Long = 0
                if (d >= 60000) {
                    minutes = d / 60000
                    d -= minutes * 60000
                }
                if (d >= 1000) {
                    seconds = d / 1000
                    d -= seconds * 1000
                }
                hundredths = d / 10
                textView.text = String.format("%d:%02d.%02d", minutes, seconds, hundredths)
                delay(16)
            }
        }
    }

    companion object {
        const val TAG = "PolishViewModel"
    }
}
