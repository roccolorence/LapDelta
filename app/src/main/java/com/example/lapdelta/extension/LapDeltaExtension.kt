package com.example.lapdelta.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension

/**
 * Main Karoo extension service for the Lap Delta data field.
 *
 * Registered in AndroidManifest as a <service> with
 * action "io.hammerhead.karooext.KAROO_EXTENSION"
 * and meta-data pointing at extension_info.xml.
 */
class LapDeltaExtension : KarooExtension(
    "lapdelta",   // MUST match id in extension_info.xml
    "1"           // version string (can be anything)
) {

    private val karooSystem by lazy { KarooSystemService(this) }

    override val types: List<DataTypeImpl> by lazy {
        listOf(
            // Register our single Lap Delta data type
            LapDeltaDataType(
                karooSystem = karooSystem,
                extensionId = extension
            )
        )
    }
}
