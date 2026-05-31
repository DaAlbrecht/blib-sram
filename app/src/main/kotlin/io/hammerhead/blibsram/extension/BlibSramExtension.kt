package io.hammerhead.blibsram.extension

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.PerformHardwareAction
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BlibSramExtension : KarooExtension("blibSram", "1.0") {

    enum class BlibAction {
        LEFT_PRESS,
        RIGHT_PRESS
    }
    private val tag = "BlibSramExtension"

    private val extensionScope = CoroutineScope(Dispatchers.IO)
    private lateinit var karooSystem: KarooSystemService
    private var logcatProcess: Process? = null
    private var snifferJob: Job? = null
    private var lastEventCount = -1
    private var currentRideState: RideState = RideState.Idle
    private var rideStateConsumerId: String? = null

    private val prefs by lazy { applicationContext.getSharedPreferences("blib_prefs", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Extension Created")

        karooSystem = KarooSystemService(applicationContext)
        karooSystem.connect { connected ->
            Log.d(tag, "BlibSram connected: $connected")
            if (connected) {
                rideStateConsumerId = karooSystem.addConsumer<RideState> { rideState ->
                    Log.d(tag, "Ride state changed: $rideState")
                    currentRideState = rideState
                    updateSnifferState()
                }
                updateSnifferState()
            }
        }
    }

    override fun onDestroy() {
        Log.d(tag, "Extension Destroyed")
        rideStateConsumerId?.let { karooSystem.removeConsumer(it) }
        stopLogcatSniffer()
        karooSystem.disconnect()
        extensionScope.cancel()
        super.onDestroy()
    }

    private fun updateSnifferState() {
        if (currentRideState !is RideState.Idle) {
            startLogcatSniffer()
        } else {
            stopLogcatSniffer()
        }
    }

    private fun stopLogcatSniffer() {
        if (logcatProcess != null || snifferJob != null) {
            Log.d(tag, "Stopping logcat sniffer")
            logcatProcess?.destroy()
            logcatProcess = null
            snifferJob?.cancel()
            snifferJob = null
        }
    }

    private fun startLogcatSniffer() {
        if (snifferJob?.isActive == true) {
            Log.d(tag, "Logcat sniffer already running")
            return
        }
        snifferJob = extensionScope.launch(Dispatchers.IO) {
            try {
                Log.d(tag, "Starting logcat sniffer")
                // Use logcat native filtering to minimize CPU ussage.
                // This "subscribes" only to Broadcast Data (4E) on Page 2 (02).
                val cmd = "logcat -T 1 -b system -b radio -v raw -s antradio:V -e \"Rx \\[A4\\].*4E.*02\""
                logcatProcess = Runtime.getRuntime().exec(cmd)
                val reader = logcatProcess?.inputStream?.bufferedReader() ?: return@launch
                Log.d(tag, "Logcat sniffer started")
                while (true) {
                    val line = reader.readLine() ?: break
                    parseAntPacket(line)?.let { action ->
                        handleBlibAction(action)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Logcat sniffer error: ${e.message}")
            } finally {
                logcatProcess?.destroy()
                logcatProcess = null
                Log.d(tag, "Logcat sniffer exited")
            }
        }
    }

    private fun parseAntPacket(line: String): BlibAction? {
        try {
            // Split by '[' to get the hex values between brackets
            // Example: "Rx [A4][11][4E][01][02][39][01][02][01][01][01][00][C0][0A][97][22][05][20][B9][B0][50][35]"
            val parts = line.split("[").map { it.substringBefore("]").trim() }

            // parts[6]=EventCount, parts[7]=Status
            if (parts.size < 8) return null

            val eventCount = parts[6].toIntOrNull(16) ?: return null
            // Debounce the events using the ANT event count byte
            if (eventCount != lastEventCount) {
                lastEventCount = eventCount
                return when (parts[7].toIntOrNull(16)) {
                    0x01 -> BlibAction.LEFT_PRESS
                    0x02 -> BlibAction.RIGHT_PRESS
                    else -> null
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun handleBlibAction(blibAction: BlibAction) {
        if (!karooSystem.connected) return

        val actionStr = when (blibAction) {
            BlibAction.LEFT_PRESS -> prefs.getString("left_action", "Page Left")
            BlibAction.RIGHT_PRESS -> prefs.getString("right_action", "Page Right")
        } ?: return

        val hardwareAction = mapToAction(actionStr)
        if (hardwareAction != null) {
            val success = karooSystem.dispatch(hardwareAction)
            Log.d(tag, "Dispatching $hardwareAction Success: $success")
        }
    }

    private fun mapToAction(actionStr: String): PerformHardwareAction? {
        return when (actionStr) {
            "Page Left" -> PerformHardwareAction.TopLeftPress
            "Page Right" -> PerformHardwareAction.TopRightPress
            else -> null
        }
    }
}
