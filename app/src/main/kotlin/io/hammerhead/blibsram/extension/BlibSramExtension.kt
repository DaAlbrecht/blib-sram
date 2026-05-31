package io.hammerhead.blibsram.extension

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.PerformHardwareAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BlibSramExtension : KarooExtension("blibSram", "1.0") {

    private val TAG = "BlibSramExtension"

    private val extensionScope = CoroutineScope(Dispatchers.IO)
    private lateinit var karooSystem: KarooSystemService
    private var logcatProcess: Process? = null
    private var lastEventCount = -1

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Extension Created")

        karooSystem = KarooSystemService(applicationContext)
        karooSystem.connect { connected ->
            Log.d(TAG, "Foo connected: $connected")
        }

        startLogcatSniffer()
    }

    override fun onDestroy() {
        Log.d(TAG, "Extension Destroyed")
        logcatProcess?.destroy()
        karooSystem.disconnect()
        extensionScope.cancel()
        super.onDestroy()
    }

    private fun startLogcatSniffer() {
        extensionScope.launch(Dispatchers.IO) {
            try {
                logcatProcess = Runtime.getRuntime().exec("logcat -T 1 -b main -b system -b radio -v raw -s antradio:V")
                val reader = logcatProcess?.inputStream?.bufferedReader() ?: return@launch
                Log.d(TAG, "Logcat sniffer started")
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.contains("Rx [A4]")) {
                        parseAntPacket(line)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Logcat sniffer error: ${e.message}")
            } finally {
                logcatProcess?.destroy()
                logcatProcess = null
                Log.d(TAG, "Logcat sniffer stopped")
            }
        }
    }

    private fun parseAntPacket(line: String) {
        if (!karooSystem.connected) return

        try {
            val regex = Regex("\\[([0-9A-F]{2})]")
            val matches = regex.findAll(line).map { it.groupValues[1] }.toList()

            // TODO: better filter for page 02
            if (matches.size < 11) return

            val page = matches[4]
            if (page == "02") {
                val eventCount = matches[5].toInt(16)
                if (eventCount != lastEventCount) {
                    val statusByte = matches[6].toInt(16)
                    Log.i(TAG, "EVENT: Count=$eventCount, Status=0x${matches[6]}, Payload=${matches.subList(4, 12).joinToString("")}")
                    
                    val action = when (statusByte) {
                        0x01 -> PerformHardwareAction.TopLeftPress
                        0x02 -> PerformHardwareAction.TopRightPress
                        else -> null
                    }

                    if (action != null) {
                        val success = karooSystem.dispatch(action)
                        Log.d(TAG, "Dispatching $action Success: $success")
                    }
                    lastEventCount = eventCount
                }
            }
        } catch (_: Exception) {}
    }
}
