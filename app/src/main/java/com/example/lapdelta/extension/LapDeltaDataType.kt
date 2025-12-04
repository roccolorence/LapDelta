package com.example.lapdelta.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.data.DataStream
import io.hammerhead.karooext.data.GraphicValue
import io.hammerhead.karooext.data.NumericValue
import io.hammerhead.karooext.extension.DataTypeImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * DataType for Lap Delta - shows the time difference between
 * the current lap and the best lap time.
 *
 * Features:
 * - Compares to best lap (not just last lap)
 * - Shows both time delta and percentage
 * - Color coded: Green = faster, Red = slower, Gray = first lap
 * - Always displays sign (+ or -)
 *
 * The TYPE_ID string must match the typeId in res/xml/extension_info.xml.
 */
class LapDeltaDataType(
    private val karooSystem: KarooSystemService,
    extensionId: String
) : DataTypeImpl(
    extensionId,   // extension id ("lapdelta")
    TYPE_ID,       // data type id ("lap_delta")
    true           // graphical = true (enables color coding)
) {

    companion object {
        // MUST match <DataType typeId="lap_delta" .../> in extension_info.xml
        const val TYPE_ID = "lap_delta"
    }

    override fun onCreateDataStream(subscriber: DataStream.Subscriber): DataStream {
        // Create a stream that feeds enhanced lap delta values
        return LapDeltaStream(
            karooSystem = karooSystem,
            subscriber = subscriber
        )
    }
}

/**
 * Enhanced stream that calculates lap delta with advanced features:
 *
 * Features:
 * 1. Compares to BEST lap (not just last lap)
 * 2. Shows formatted time with +/- sign
 * 3. Displays percentage faster/slower
 * 4. Color coding: Green (faster), Red (slower), Gray (first lap)
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
     * Calculate and send the enhanced lap delta to the Karoo display.
     *
     * Display format:
     * - Time delta with sign (e.g., "+5s" or "-3s")
     * - Percentage (e.g., "2.5%" faster/slower)
     * - Color: Green if faster, Red if slower, Gray if first lap
     */
    private fun updateDelta() {
        val current = currentLapTime
        val best = bestLapTime

        // First lap - show neutral state
        if (best == null || current == null || current <= 0.0) {
            subscriber.onNext(
                GraphicValue(
                    singleLineText = "--",
                    multiLineText = "First Lap",
                    color = GraphicValue.Color.GRAY
                )
            )
            return
        }

        // Calculate delta: current - best
        // Positive = slower than best, Negative = faster than best
        val deltaSeconds = current - best
        val percentageDelta = (deltaSeconds / best) * 100.0

        // Format the time delta with sign
        val sign = if (deltaSeconds >= 0) "+" else "-"
        val absSeconds = abs(deltaSeconds)
        val timeDisplay = "${sign}${formatTime(absSeconds)}"

        // Format the percentage
        val percentDisplay = "${abs(percentageDelta).toInt()}%"

        // Determine color based on performance
        val color = when {
            deltaSeconds < -0.5 -> GraphicValue.Color.GREEN  // Faster than best by >0.5s
            deltaSeconds > 0.5 -> GraphicValue.Color.RED     // Slower than best by >0.5s
            else -> GraphicValue.Color.GRAY                  // Within 0.5s (neutral)
        }

        // Send the enhanced graphic value
        subscriber.onNext(
            GraphicValue(
                singleLineText = timeDisplay,           // e.g., "+5s" or "-3s"
                multiLineText = "$timeDisplay\n$percentDisplay",  // e.g., "+5s\n2%"
                color = color
            )
        )
    }

    /**
     * Format seconds into a readable time string.
     *
     * Examples:
     * - 5.2 seconds → "5s"
     * - 65.0 seconds → "1:05"
     * - 0.8 seconds → "1s"
     */
    private fun formatTime(seconds: Double): String {
        val totalSeconds = seconds.toInt()

        return when {
            totalSeconds < 60 -> "${totalSeconds}s"
            else -> {
                val minutes = totalSeconds / 60
                val secs = totalSeconds % 60
                "$minutes:%02d".format(secs)
            }
        }
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
