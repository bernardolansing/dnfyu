package com.bernardolansing.dnfyu

import android.util.Log
import kotlin.collections.ArrayDeque
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/// Helper class for keeping track of the advertisement packets income.
class ForgottalEvaluator {
    private val packetsReceivedDeque = ArrayDeque<Pair<TimeSource.Monotonic.ValueTimeMark, Int>>()

    fun reportPacketReceipt(intensity: Int) {
        packetsReceivedDeque.add(Pair(TimeSource.Monotonic.markNow(), intensity))
    }

    fun getPacketReceiptRate(): Int {
        clearOldPackets()
        val rate = (packetsReceivedDeque.size.toFloat() / Constants.PACKET_LIFETIME).roundToInt()
        if (rate < Constants.LOW_PACKET_RATE_THRESHOLD) {
            Log.i(null, "Packet receipt rate is low, checking signal strength")
            if (averagePacketSignalIntensity() < Constants.LOW_SIGNAL_STRENGTH_THRESHOLD) {
                Log.i(null, "Umbrella seems to have been forgotten")
                throw UmbrellaWasForgotten()
            }
        }
        return rate
    }

    private fun clearOldPackets() {
        packetsReceivedDeque.removeIf { event ->
            event.first.elapsedNow().inWholeSeconds >= Constants.PACKET_LIFETIME
        }
    }

    private fun averagePacketSignalIntensity(): Int {
        val sum = packetsReceivedDeque.fold(0) { acc, event -> acc + event.second}
        return sum / packetsReceivedDeque.size
    }
}

object Constants {
    const val PACKET_LIFETIME = 3
    const val LOW_PACKET_RATE_THRESHOLD = 2
    const val LOW_SIGNAL_STRENGTH_THRESHOLD = -85
}

class UmbrellaWasForgotten : Exception()
