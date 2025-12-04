package com.example.lapdelta.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Stream data for a built-in Karoo data type (like lap time, last lap time, etc.)
fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> {
    return callbackFlow {
        val listenerId =
            addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
                trySendBlocking(event.state)
            }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

// (extra utility if you want KarooEvent streams later)
inline fun <reified T : KarooEvent> KarooSystemService.consumerFlow(): Flow<T> {
    return callbackFlow {
        val listenerId = addConsumer<T> {
            trySend(it)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}


