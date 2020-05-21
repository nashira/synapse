package com.rthqks.synapse.polish

import android.content.Context
import android.graphics.SurfaceTexture
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
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.effect.EffectExecutor
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.NodeDef.BCubeImport.LutUri
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
    val effects = MediatorLiveData<List<Network>>()
    val deviceSupported = MutableLiveData<Boolean>()
    var baseNetwork: Network? = null
    var currentEffect: Network? = null
    private var recordingStart = 0L
    private val recordingDuration: Long get() = SystemClock.elapsedRealtime() - recordingStart
    private var svSetup = false
    private var stopped = true
    private var needsNewContext = false
    private var context = ExecutionContext(contxt, videoStorage, assetManager)
    private lateinit var effectExecutor: EffectExecutor
    private var surfaceView: SurfaceView? = null

    init {
        viewModelScope.launch {
            baseNetwork = withContext(Dispatchers.IO) {
                if (!dao.hasNetwork0()) {
                    syncLogic.refreshEffects()
                }
                syncLogic.getNetwork(100)
            }
            effectExecutor = EffectExecutor(context, baseNetwork!!)
            effectExecutor.setup()
            deviceSupported.value = context.glesManager.supportedDevice
        }
    }

    fun recreateContext() {
        if (!needsNewContext) return
        needsNewContext = false
        svSetup = false
        context = ExecutionContext(contxt, videoStorage, assetManager)

        viewModelScope.launch {
//            baseNetwork = withContext(Dispatchers.IO) {
//                syncLogic.getNetwork(0)
//            }

            effectExecutor = EffectExecutor(context, baseNetwork!!)
            effectExecutor.setup()
            effectExecutor.initializeEffect()
            currentEffect?.let { setEffect(it) }
        }
    }

    fun initializeEffect() {
        viewModelScope.launch(Dispatchers.IO) {
            effectExecutor.initializeEffect()
            val networks = dao.getNetworks().map { it.toNetwork() }
            effects.postValue(networks)
        }
    }

    fun releaseContext() {
        needsNewContext = true
        viewModelScope.launch {
            effectExecutor.removeAll()
            effectExecutor.release()
        }
    }

    fun setSurfaceView(surfaceView: SurfaceView?) {
        this.surfaceView = surfaceView
    }

    fun flipCamera() {
//        val facing = currentEffect?.let {
//            val facing = when (properties[CameraFacing]) {
//                CameraCharacteristics.LENS_FACING_BACK ->
//                    CameraCharacteristics.LENS_FACING_FRONT
//                CameraCharacteristics.LENS_FACING_FRONT ->
//                    CameraCharacteristics.LENS_FACING_BACK
//                else -> CameraCharacteristics.LENS_FACING_BACK
//            }
//            properties[CameraFacing] = facing
//            facing
//        }
//        val string = if (facing == 0) "front" else "back"
//        analytics.logEvent(Analytics.Event.EditSetting(CameraFacing.name, string))
    }

    fun startExecution() {
        currentEffect ?: return

        viewModelScope.launch {
            if (stopped) {
                stopped = false
                Log.d(TAG, "resume")

                effectExecutor.resume()
            }
        }
    }

    fun stopExecution() {
        currentEffect ?: return
        viewModelScope.launch {
            if (!stopped) {
                stopped = true
                Log.d(TAG, "pause")

//            properties[Recording] = false
                effectExecutor.pause()
            }
        }
    }

    fun startRecording() {
        val effectName = currentEffect?.name ?: "unknown"
        analytics.logEvent(Analytics.Event.RecordStart(effectName))
        recordingStart = SystemClock.elapsedRealtime()
//        properties[Recording] = true
    }

    fun stopRecording() {
        val effectName = currentEffect?.name ?: "unknown"
        analytics.logEvent(
            Analytics.Event.RecordStop(
                effectName,
                SystemClock.elapsedRealtime() - recordingStart
            )
        )
//        properties[Recording] = false
    }

    fun <T: Any> editProperty(
        key: Property.Key<T>,
        value: T
    ) {
//        properties[key] = value
//        val property = properties.getProperty(key)!!
//        val type = property.getType()
//        val string = property.getString()
//        analytics.logEvent(Analytics.Event.EditSetting(key.name, string))
//
//        viewModelScope.launch(Dispatchers.IO) {
//            dao.insertProperty(
//                PropertyData(
//                    0, 0,
//                    type,
//                    key.name,
//                    string,
//                    property.exposed
//                )
//            )
//        }
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
//        properties[Rotation] = orientation
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
//        properties[LutStrength] = strength
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

    fun getLutStrength(): Float = effectExecutor.getLutStrength()

    companion object {
        const val TAG = "PolishViewModel"
    }
}
