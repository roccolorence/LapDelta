package com.example.lapdelta.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.data.DataStream
import io.hammerhead.karooext.data.NumericValue
import io.hammerhead.karooext.extension.DataTypeImpl

/**
 * DataType for Lap Delta — currently just outputs a
 * simple counter so we can verify the field works on Karoo.
 *
 * The important bit is the TYPE_ID string, which must match
 * the typeId in res/xml/extension_info.xml.
 */
class LapDeltaDataType(
    private val karooSystem: KarooSystemService,
    extensionId: String
) : DataTypeImpl(
    extensionId,   // extension id ("lapdelta")
    TYPE_ID,       // data type id ("lap_delta")
    false          // graphical = false (numeric field)
) {

    companion object {
        // MUST match <DataType typeId="lap_delta" .../> in extension_info.xml
        const val TYPE_ID = "lap_delta"
    }

    override fun onCreateDataStream(subscriber: DataStream.Subscriber): DataStream {
        // Create a stream that feeds values to the data field
        return LapDeltaStream(
            karooSystem = karooSystem,
            subscriber = subscriber
        )
    }
}

/**
 * Simple test stream: counts 1, 2, 3, ... once per update.
 * Once this is working on the Karoo, we can swap the logic
 * to real lap-delta timing.
 */
class LapDeltaStream(
    private val karooSystem: KarooSystemService,
    subscriber: DataStream.Subscriber
) : DataStream(subscriber) {

    private var counter = 0

    override fun onStart() {
        counter = 0
    }

    override fun onUpdate() {
        counter += 1

        // Send a numeric value to the Karoo field
        subscriber.onNext(
            NumericValue(
                counter.toFloat()
            )
        )
    }

    override fun onStop() {
        // nothing special for now
    }
}
