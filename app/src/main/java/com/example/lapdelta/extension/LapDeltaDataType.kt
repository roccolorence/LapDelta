package com.example.lapdelta.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.data.DataStream
import io.hammerhead.karooext.data.NumericValue
import io.hammerhead.karooext.extension.DataTypeImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * DataType for Lap Delta - shows the time difference between
 * the current lap and the best lap time.
 *
 * Features:
 * - Compares to best lap (not just last lap)
 * - Shows time delta with sign (+ or -)
 * - Negative = faster, Positive = slower
 *
 * The TYPE_ID string must match the typeId in res/xml/extension_info.xml.
 */
class LapDeltaDataType(
    private val karooSystem: KarooSystemService,
    extensionId: String
) : DataTypeImpl(
    extensionId,   // extension id ("lapdelta")
    TYPE_ID,       // data type id ("lap_delta")
    false          // graphical = false (numeric field - SDK limitation)
) {

    companion object {
        // MUST match <DataType typeId="lap_delta" .../> in extension_info.xml
        const val TYPE_ID = "lap_delta"
    }

    override fun onCreateDataStream(subscriber: DataStream.Subscriber): DataStream {
        // Create a stream that feeds lap delta values
        return LapDeltaStream(
            karooSystem = karooSystem,
            subscriber = subscriber
        )
    }
}

/**
 * Stream that calculates lap delta compared to best lap.
 *
 * Features:
 * - Compares to BEST lap (not just last lap)
 * - Displays delta in seconds (negative = faster, positive = slower)
 *
 * Subscribes to Karoo's built-in lap timing data:
 * - "duration.lap" = elapsed time in current lap
 * - "duration.lastLap" = total time of last completed lap
 *
 * Delta calculation:
 * - Tracks all lap times to find the best (fastest) lap
 * - Compares current position to where you were in best lap
 * - Positive delta = going slower, Negative delta = going faster
 */
class LapDeltaStream(
    private val karooSystem: KarooSystemService,
    subscriber: DataStream.Subscriber
) : DataStream(subscriber) {

    private var currentLapTime: Double? = null
    private var lastLapTime: Double? = null
    private var bestLapTime: Double? = null  // Track the fastest lap
    private var lapHistory = mutableListOf<Double>()  // Store all completed laps
    private var streamJob: Job? = null

    override fun onStart() {
        // Reset state when starting
        bestLapTime = null
        lapHistory.clear()

        // Start listening to Karoo's lap timing data streams
        streamJob = CoroutineScope(Dispatchers.Main).launch {
            // Combine two data streams: current lap time and last lap time
            combine(
                karooSystem.streamDataFlow("duration.lap"),
                karooSystem.streamDataFlow("duration.lastLap")
            ) { currentLapState, lastLapState ->
                // Pair the two states together
                Pair(currentLapState, lastLapState)
            }.collect { (currentState, lastState) ->
                // Extract numeric values from the stream states
                val newCurrentLapTime = (currentState.dataPoint as? NumericValue)?.value?.toDouble()
                val newLastLapTime = (lastState.dataPoint as? NumericValue)?.value?.toDouble()

                // Check if a new lap was completed (lastLapTime changed)
                if (newLastLapTime != null && newLastLapTime != lastLapTime && newLastLapTime > 0.0) {
                    // A lap was completed - add to history
                    lapHistory.add(newLastLapTime)

                    // Update best lap time (find minimum)
                    bestLapTime = lapHistory.minOrNull()
                }

                currentLapTime = newCurrentLapTime
                lastLapTime = newLastLapTime

                // Update the delta display
                updateDelta()
            }
        }
    }

    /**
     * Calculate and send the lap delta to the Karoo display.
     *
     * Display:
     * - Delta in seconds (numeric value)
     * - Negative = faster than best
     * - Positive = slower than best
     * - Zero on first lap
     */
    private fun updateDelta() {
        val current = currentLapTime
        val best = bestLapTime

        // First lap or no data - show zero
        if (best == null || current == null || current <= 0.0) {
            subscriber.onNext(NumericValue(0f))
            return
        }

        // Calculate delta: current - best
        // Positive = slower than best, Negative = faster than best
        val deltaSeconds = current - best

        // Send the delta value (in seconds) to Karoo
        // Karoo will format as time automatically
        subscriber.onNext(NumericValue(deltaSeconds.toFloat()))
    }

    override fun onUpdate() {
        // No-op: data updates come from the coroutine stream
        // The combine() flow automatically calls updateDelta() when data changes
    }

    override fun onStop() {
        // Clean up the coroutine when the stream stops
        streamJob?.cancel()
        streamJob = null
        currentLapTime = null
        lastLapTime = null
        bestLapTime = null
        lapHistory.clear()
    }
}
