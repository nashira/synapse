package xyz.rthqks.proc.ui

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import xyz.rthqks.proc.data.GraphConfig
import xyz.rthqks.proc.data.NodeType
import javax.inject.Inject

class GraphEditViewModel @Inject constructor() : ViewModel() {
    private val test: Job
    private val graph = GraphConfig(0, "Test")
    val graphChannel = MutableLiveData<GraphConfig>()


    init {
        graphChannel.value = graph

        test = viewModelScope.launch {
            val a = Channel<Int>(1)
            val b = Channel<Int>(1)
            var v = 0

            a.send(-1)
            while (isActive) {
                select<Unit> {
                    a.onReceive {
                        Log.d(TAG, "a $it")
                        v++
                        b.send(v)
                    }
                    b.onReceive {
                        Log.d(TAG, "b $it")
                        a.send(it)
                    }
                }

                delay(100)
            }
        }

        viewModelScope.launch {
            delay(1000)
            test.cancel()
        }
    }

    fun stop() {
        test.cancel()
    }

    fun addNodeType(nodeType: NodeType) {
        graph.addNodeType(nodeType)
        graphChannel.value = graph
    }

    companion object {
        private val TAG = GraphEditViewModel::class.java.simpleName
    }
}
