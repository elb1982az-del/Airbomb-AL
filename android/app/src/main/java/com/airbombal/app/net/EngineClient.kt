package com.airbombal.app.net

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reconnecting NDJSON client for the Airbomb-Al engine on 127.0.0.1:8767.
 *
 * A single writer coroutine preserves command order. Request ids are tracked so
 * replies are re-emitted as `{"event":"reply","cmd":...}` events for the ViewModel.
 */
object EngineClient {
    private const val TAG = "AirbombEngine"
    const val HOST = "127.0.0.1"
    const val PORT = 8767

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var writer: BufferedWriter? = null
    private var connectionJob: Job? = null

    private val outbox = Channel<JSONObject>(capacity = 256)
    private val pending = ConcurrentHashMap<Int, String>()
    private val sequence = AtomicInteger(1)

    private val _events = MutableSharedFlow<JSONObject>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<JSONObject> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    init {
        scope.launch {
            for (message in outbox) {
                val stream = writer ?: continue
                try {
                    stream.write(message.toString())
                    stream.newLine()
                    stream.flush()
                } catch (e: Exception) {
                    Log.w(TAG, "write failed: ${e.message}")
                }
            }
        }
    }

    fun connect() {
        if (connectionJob?.isActive == true) return
        connectionJob = scope.launch { runLoop() }
    }

    fun reconnect() {
        connectionJob?.cancel()
        connectionJob = null
        pending.clear()
        connect()
    }

    /** Non-blocking, ordered command send. */
    fun send(cmd: String, vararg params: Pair<String, Any?>) {
        val id = sequence.getAndIncrement()
        if (!_connected.value) {
            _events.tryEmit(
                JSONObject()
                    .put("event", "engine_error")
                    .put("message", "Engine offline — \"$cmd\" was not sent. Start it with: airbomb-engine start"),
            )
            return
        }
        val message = JSONObject().put("id", id).put("cmd", cmd)
        for ((key, value) in params) {
            if (value != null) message.put(key, value)
        }
        pending[id] = cmd
        if (pending.size > 256) pending.clear()
        outbox.trySend(message)
    }

    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(HOST, PORT), 2500)
                    val out = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                    writer = out
                    _connected.value = true
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
                        if (obj.has("ok") && obj.has("id")) {
                            val id = obj.optInt("id", -1)
                            val cmd = pending.remove(id) ?: ""
                            val reply = JSONObject()
                                .put("event", "reply")
                                .put("cmd", cmd)
                                .put("ok", obj.optBoolean("ok", false))
                            if (!obj.optBoolean("ok", false)) {
                                reply.put("error", obj.optString("error", "unknown error"))
                            }
                            if (obj.has("data")) reply.put("data", obj.get("data"))
                            _events.emit(reply)
                        } else {
                            _events.emit(obj)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "connection lost: ${e.message}")
            } finally {
                writer = null
                _connected.value = false
            }
            if (currentCoroutineContext().isActive) delay(1500)
        }
    }
}
