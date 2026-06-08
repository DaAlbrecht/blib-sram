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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry

class BlibSramExtension : KarooExtension("blibSram", "1.0") {

    companion object {
        private const val TAG = "BlibSramExtension"
        private const val PREFS_NAME = "blib_prefs"
        private const val KEY_LEFT_ACTION = "left_action"
        private const val KEY_RIGHT_ACTION = "right_action"
        private const val DEFAULT_LEFT = "Page Left"
        private const val DEFAULT_RIGHT = "Page Right"
    }

    private val extensionScope = CoroutineScope(Dispatchers.IO)
    private lateinit var karooSystem: KarooSystemService
    private var snifferJob: Job? = null
    private var lastEventCount = -1
    private var currentRideState: RideState = RideState.Idle
    private var rideStateConsumerId: String? = null

    private var lastActionTime = 0L

    private val antParser = AntParser { lastEventCount }

    private val prefs by lazy { applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        karooSystem.connect { connected ->
            if (connected) {
                rideStateConsumerId = karooSystem.addConsumer<RideState> { rideState ->
                    Log.d(TAG, "Ride state changed: $rideState")
                    currentRideState = rideState
                    updateSnifferState()
                }
                updateSnifferState()
            }
        }
    }

    override fun onDestroy() {
        rideStateConsumerId?.let { karooSystem.removeConsumer(it) }
        stopLogcatSniffer()
        karooSystem.disconnect()
        extensionScope.cancel()
        super.onDestroy()
    }

    private fun updateSnifferState() {
        Log.d(TAG, "Updating sniffer state: $currentRideState")
        if (currentRideState !is RideState.Idle) {
            startLogcatSniffer()
        } else {
            stopLogcatSniffer()
        }
    }

    private fun startLogcatSniffer() {
        if (snifferJob?.isActive == true) return

        snifferJob = logcatFlow()
            .onEach { line ->
                antParser.parseAntPacket(line)?.let { result ->
                    Log.d(TAG, "Parsed action: ${result.action} (EC: ${result.eventCount})")
                    lastEventCount = result.eventCount
                    handleBlibAction(result.action)
                }
            }
            .retry { e ->
                Log.w(TAG, "Logcat sniffer error: ${e.message}, retrying in 2s...")
                delay(2000)
                true
            }
            .launchIn(extensionScope)
    }

    private fun stopLogcatSniffer() {
        snifferJob?.cancel()
        snifferJob = null
    }

    private fun logcatFlow(): Flow<String> = flow {
        val command = arrayOf("logcat", "-T", "1", "-v", "raw", "-b", "main", "-b", "system", "-b", "radio", "antradio:V", "*:S", "-e", "4E.*02")
        val process = Runtime.getRuntime().exec(command)
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    emit(line)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Logcat process error", e)
        } finally {
            process.destroy()
            Log.d(TAG, "Logcat process destroyed")
        }
    }.flowOn(Dispatchers.IO)

    private fun handleBlibAction(blibAction: BlibAction) {
        if (!karooSystem.connected) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < 300) return
        lastActionTime = currentTime

        val actionStr = when (blibAction) {
            BlibAction.LEFT_PRESS -> prefs.getString(KEY_LEFT_ACTION, DEFAULT_LEFT)
            BlibAction.RIGHT_PRESS -> prefs.getString(KEY_RIGHT_ACTION, DEFAULT_RIGHT)
        } ?: return

        mapToAction(actionStr)?.let { hardwareAction ->
            val success = karooSystem.dispatch(hardwareAction)
            Log.d(TAG, "Dispatched $hardwareAction: $success")
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
