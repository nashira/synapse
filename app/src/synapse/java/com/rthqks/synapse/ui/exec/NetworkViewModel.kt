package com.rthqks.synapse.ui.exec

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.assets.VideoStorage
import com.rthqks.synapse.data.SynapseDao
import com.rthqks.synapse.exec.CameraManager
import com.rthqks.synapse.exec.NetworkExecutor
import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.logic.Network
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import javax.inject.Inject

class NetworkViewModel @Inject constructor(
    private val context: Context,
    private val videoStorage: VideoStorage,
    private val dao: SynapseDao
) : ViewModel() {
    private lateinit var surfaceView: SurfaceView
    val networkLoaded = MutableLiveData<Network>()
    private lateinit var networkExecutor: NetworkExecutor
    private var initJob: Job? = null
    private var startJob: Job? = null
    private var stopJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dispatcher = Executors.newFixedThreadPool(6).asCoroutineDispatcher()
    private val glesManager = GlesManager()
    private val cameraManager = CameraManager(context)
    private val assetManager = AssetManager(context)

    fun loadNetwork(networkId: Int) {
        Log.d(TAG, "loadNetwork")
        initJob = scope.launch {
            val network = dao.getFullNetwork(networkId)
            networkLoaded.postValue(network)
            Log.d(TAG, "loaded network: $network")

            cameraManager.initialize()
            glesManager.glContext { it.initialize() }

            networkExecutor = NetworkExecutor(
                context, dispatcher, glesManager, cameraManager, assetManager, videoStorage, network
            )

            Log.d(TAG, "initialize")
            networkExecutor.initialize()
            Log.d(TAG, "initialized")

            networkExecutor.tmpSetSurfaceView(surfaceView)
        }
    }

    fun startExecution() {
        Log.d(TAG, "startExecution")
        startJob = scope.launch {
            initJob?.join()
            stopJob?.join()
            Log.d(TAG, "starting")
            networkExecutor.start()
            // right now start launches coroutines and does not join them.
            // there is a period after start during which calling stop
            // will result in hung coroutines.
            // this delay will not block any threads, it will simply keep
            // startJob active to allow for the nodes to settle
            // TODO: have start not return until it would be safe to call stop
            delay(250)
            Log.d(TAG, "done starting")
        }
    }

    fun stopExecution() {
        Log.d(TAG, "stopExecution")
        stopJob = scope.launch {
            Log.d(TAG, "stopping")
            startJob?.join()
            Log.d(TAG, "calling network.stop")
            networkExecutor.stop()
            Log.d(TAG, "done stopping")
        }
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared")
        scope.launch {
            Log.d(TAG, "release")
            withTimeoutOrNull(2000) {
                stopJob?.join()
            } ?: run {
                Log.w(TAG, "timeout waiting for stop")
//                Toast.makeText(context, "TIMEOUT", Toast.LENGTH_LONG).show()
                stopJob?.cancel()
            }
            networkExecutor.release()
            glesManager.release()
            cameraManager.release()
            dispatcher.close()

            scope.cancel()
            Log.d(TAG, "released")
        }
        super.onCleared()
    }

    fun setSurfaceView(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
        initJob?.let {
            scope.launch {
                networkExecutor.tmpSetSurfaceView(surfaceView)
            }
        }
    }

    companion object {
        private val TAG = NetworkViewModel::class.java.simpleName
    }
}
