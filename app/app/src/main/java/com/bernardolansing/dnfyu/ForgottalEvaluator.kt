package com.bernardolansing.dnfyu

import android.util.Log
import kotlin.collections.ArrayDeque
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Helper class for keeping track of the advertisement packets income.
 */
class ForgottalEvaluator {
    private val packetsReceivedDeque = ArrayDeque<Pair<TimeSource.Monotonic.ValueTimeMark, Int>>()
    private var packetRate = 0
    private var inReach = true

    fun reportPacketReceipt(intensity: Int) {
        packetsReceivedDeque.add(Pair(TimeSource.Monotonic.markNow(), intensity))
    }

    /**
     * Updates the internal state. Call this before calling [isUmbrellaInReach] and
     * [getPacketReceiptRate] .
     */
    fun update() {
        Log.i(null, "Updating ForgottalEvaluator state")
        // Remove old packets from history:
        packetsReceivedDeque.removeIf { event ->
            event.first.elapsedNow().inWholeSeconds >= Constants.PACKET_LIFETIME
        }
        // Recalculate packet rate.
        packetRate = (packetsReceivedDeque.size.toFloat() / Constants.PACKET_LIFETIME).roundToInt()

        // Check if we should change "inReach" state true -> false.
        if (inReach && packetRate <= Constants.LOW_PACKET_RATE_THRESHOLD) {
            inReach = averagePacketSignalIntensity() > Constants.LOW_SIGNAL_STRENGTH_THRESHOLD
        }

        // Check if we should change "inReach" false -> true. This check uses different (stricter)
        // parameters than the previous one, so we can avoid getting stuck in an intermitent state
        // switch when signal is weak.
        if (! inReach && packetRate >= Constants.DECENT_PACKET_RATE_THRESHOLD) {
            inReach = averagePacketSignalIntensity() > Constants.DECENT_SIGNAL_STRENGTH_THRESHOLD
        }
    }

    fun getPacketReceiptRate(): Int {
        return packetRate
    }

    fun isUmbrellaInReach(): Boolean {
        return inReach
    }

    fun wasUmbrellaTurnedOff(): Boolean {
        return inReach && packetRate == 0
    }

    private fun averagePacketSignalIntensity(): Int {
        if (packetsReceivedDeque.isEmpty()) {
            return 0
        }
        val sum = packetsReceivedDeque.fold(0) { acc, event -> acc + event.second}
        return sum / packetsReceivedDeque.size
    }
}

private object Constants {
    const val PACKET_LIFETIME = 3
    const val DECENT_PACKET_RATE_THRESHOLD = 3
    const val DECENT_SIGNAL_STRENGTH_THRESHOLD = -70
    const val LOW_PACKET_RATE_THRESHOLD = 1
    const val LOW_SIGNAL_STRENGTH_THRESHOLD = -92
}
