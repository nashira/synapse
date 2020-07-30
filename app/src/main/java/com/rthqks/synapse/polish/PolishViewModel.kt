package com.rthqks.synapse.polish

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.widget.TextView
import androidx.lifecycle.*
import com.rthqks.flow.assets.AssetManager
import com.rthqks.flow.assets.VideoStorage
import com.rthqks.flow.exec.ExecutionContext
import com.rthqks.flow.logic.Network
import com.rthqks.flow.logic.Property
import com.rthqks.flow.logic.User
import com.rthqks.synapse.data.PropertyData
import com.rthqks.synapse.data.SeedData
import com.rthqks.synapse.data.SeedData.BaseEffectId
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.logic.*
import com.rthqks.flow.logic.NodeDef.BCubeImport.LutUri
import com.rthqks.flow.logic.NodeDef.Lut3d.LutStrength
import com.rthqks.flow.logic.NodeDef.MediaEncoder.Recording
import com.rthqks.flow.logic.NodeDef.MediaEncoder.Rotation
import com.rthqks.synapse.ops.Analytics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.*
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
    private var currentEffectLD: LiveData<Network?>? = null
    val currentEffectLive = MediatorLiveData<Network>()
    val deviceSupported = MutableLiveData<Boolean>()
    var baseNetwork: Network? = null
    var currentEffect: Network? = null
    var currentUser: User? = null
    private var recordingStart = 0L
    private val recordingDuration: Long get() = SystemClock.elapsedRealtime() - recordingStart
    private var svSetup = false
    private var needsNewContext = false
    private var context = ExecutionContext(contxt, videoStorage, assetManager)
    private var effectExecutor = EffectExecutor(context)
    private var surfaceView: SurfaceView? = null

    var resumingEffectId: String? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (!dao.hasNetwork(BaseEffectId)) {
                    syncLogic.refreshEffects()
                }
                baseNetwork = syncLogic.getNetwork(BaseEffectId).first()
                currentUser = syncLogic.currentUser()
            }
            effectExecutor.setBaseNetwork(baseNetwork!!)
            effectExecutor.setup()
            deviceSupported.value = context.glesManager.supportedDevice
        }
    }

    private suspend fun recreateContext() {
        val effectId = resumingEffectId ?: currentEffect?.id
        resumingEffectId = null
        Log.d(TAG, "recreateContext $effectId")

        effectExecutor.setup()
        effectExecutor.initializeEffect()
        effectId?.let { setEffect(it) }
    }

    fun initializeEffect() {
        viewModelScope.launch {
            effectExecutor.initializeEffect()
            val ld = withContext(Dispatchers.IO) {
                dao.getNetworks().map { list ->
                    list.filter {
                        it.id != BaseEffectId
                    }.map { it.toNetwork() }
                }.asLiveData()
            }

            effects.addSource(ld) {
                effects.postValue(it)
            }

            setEffect(SeedData.SeedNetworks.first().id)
        }
    }

    fun releaseContext() {
        needsNewContext = true
        viewModelScope.launch {
            effectExecutor.removeAll()
            effectExecutor.release()
            baseNetwork = withContext(Dispatchers.IO) {
                syncLogic.getNetwork(BaseEffectId).first()
            }
            context = ExecutionContext(contxt, videoStorage, assetManager)
            effectExecutor = EffectExecutor(context)
            effectExecutor.setBaseNetwork(baseNetwork!!)
        }
    }

    fun setSurfaceView(surfaceView: SurfaceView?) {
        this.surfaceView = surfaceView
    }

    fun startExecution() {
        Log.d(TAG, "resume $needsNewContext")
        viewModelScope.launch(Dispatchers.Default) {
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

    fun setEffect(effectId: String): Boolean {
        viewModelScope.launch {
            val ld = withContext(Dispatchers.IO) {
                syncLogic.getNetwork(effectId).asLiveData()
            }

            currentEffectLD?.let { currentEffectLive.removeSource(it) }
            currentEffectLD = ld
            currentEffectLive.addSource(ld) {
                if (it != null) {
                    currentEffect = it
                    currentEffectLive.postValue(it)
                    Log.d(TAG, "current effect ${it.name}")
                    viewModelScope.launch(Dispatchers.Default) {
                        effectExecutor.swapEffect(it)
                    }
                }
            }

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
            effectExecutor.setProperty(
                property.nodeId,
                property.key as Property.Key<Any>,
                property.value
            )
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
                var hundredths: Long
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

    fun makeCopy(effect: Network): String {
        val id = UUID.randomUUID().toString()
        viewModelScope.launch(Dispatchers.IO) {
            val copy = effect.copy(id, currentUser!!.id, "${effect.name} (copy)")
            dao.insertFullNetwork(copy.toData())
        }
        return id
    }

    companion object {
        const val TAG = "PolishViewModel"
    }
}
