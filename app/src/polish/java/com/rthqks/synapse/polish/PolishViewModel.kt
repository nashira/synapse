package com.rthqks.synapse.polish

import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.ExecutorLegacy
import com.rthqks.synapse.logic.*
import com.rthqks.synapse.ops.Analytics
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class PolishViewModel @Inject constructor(
//    private val executor: ExecutorLegacy,
    private val context: ExecutionContext,
    private val analytics: Analytics
) : ViewModel() {
    val deviceSupported = MutableLiveData<Boolean>()
    private var currentEffect: Effect? = null
    private var recordingStart = 0L
    private var svSetup = false
    val properties: Properties? get() = currentEffect?.properties

    private val effectExecutor = EffectExecutor(context)

    init {
        viewModelScope.launch {
//            executor.initialize(false)
//            executor.await()
            effectExecutor.setup()
            deviceSupported.value = context.glesManager.supportedDevice
        }
    }

    private var surfaceView: SurfaceView? = null
    private var network: Network? = null

    fun setSurfaceView(surfaceView: SurfaceView?) {
        this.surfaceView = surfaceView
    }

    fun flipCamera() {
        val facing = currentEffect?.flipCamera()
        restartNetwork()

        val string = if (facing == 0) "front" else "back"
        analytics.logEvent(Analytics.Event.EditSetting(CameraFacing.name, string))
    }

    private fun restartNetwork() {
//        executor.stop()
//        executor.start()
//        viewModelScope.launch {
//            effectExecutor.removeAllLinks()
//        }
    }

    fun startExecution() {
//        executor.start()
    }

    fun stopExecution() {
//        executor.stop()
    }

    fun startRecording() {
        val effectName = currentEffect?.title ?: "unknown"
        analytics.logEvent(Analytics.Event.RecordStart(effectName))
        recordingStart = SystemClock.elapsedRealtime()
        network?.apply {
            getNode(4)?.properties?.set(Recording, true)
        }
    }

    fun stopRecording() {
        val effectName = currentEffect?.title ?: "unknown"
        analytics.logEvent(
            Analytics.Event.RecordStop(
                effectName,
                SystemClock.elapsedRealtime() - recordingStart
            )
        )
        network?.apply {
            getNode(4)?.properties?.set(Recording, false)
        }
    }

    fun <T> editProperty(
        key: Property.Key<T>,
        value: T,
        restart: Boolean = false,
        recreate: Boolean = false
    ) {
        val properties = currentEffect?.properties ?: return
        analytics.logEvent(Analytics.Event.EditSetting(key.name, value.toString()))
        properties[key] = value
        Log.d(TAG, "${key.name} = $value")
//        when {
//            recreate -> currentEffect?.let { recreateNetwork(it) }
//            restart -> restartNetwork()
//        }
    }

    fun setEffect(effect: Effect): Boolean {
        currentEffect = effect

        effect.network.getNode(Effect.ID_CAMERA)?.properties?.plusAssign(effect.properties)

        analytics.logEvent(Analytics.Event.SetEffect(effect.title))
        recreateNetwork(effect)
        return true
//        return when (effect) {
//            PolishEffect.None -> {
////                Effects.none.getNode(1)?.properties?.plusAssign(properties)
////                recreateNetwork(Effects.none)
//                true
//            }
////            PolishEffect.TimeWarp -> {
////                Effects.timeWarp.getNode(1)?.properties?.plusAssign(properties)
////                Effects.timeWarp.getNode(5)?.properties?.plusAssign(properties)
////                recreateNetwork(Effects.timeWarp)
////                true
////            }
////            PolishEffect.RotoHue -> {
////                Effects.rotoHue.getNode(1)?.properties?.plusAssign(properties)
////                Effects.rotoHue.getNode(5)?.properties?.plusAssign(properties)
////                recreateNetwork(Effects.rotoHue)
////                true
////            }
//            PolishEffect.More -> {
//                Toast.makeText(context, "Coming Soon!", Toast.LENGTH_LONG).show()
//                false
//            }
//        }
    }

    private fun recreateNetwork(effect: Effect) {
        this.network = effect.network
        viewModelScope.launch {
//            executor.stop()
//            executor.releaseNetwork()
//            executor.initializeNetwork(effect.network)
//            executor.start()
//            executor.await()
//            surfaceView?.let { executor.setSurfaceView(it) }
        }

        viewModelScope.launch {
            effectExecutor.swapEffect(effect).await()
            if (!svSetup) {
                svSetup = true
                surfaceView?.let { effectExecutor.setSurfaceView(it) }
            }
        }
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared")

        runBlocking {
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
        network?.apply {
            getNode(4)?.properties?.set(Rotation, orientation)
        }
    }


    companion object {
        const val TAG = "PolishViewModel"
    }
}
