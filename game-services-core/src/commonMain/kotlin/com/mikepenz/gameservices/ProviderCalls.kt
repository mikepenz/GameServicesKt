package com.mikepenz.gameservices

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Implementation helpers shared by the provider modules; not application API. */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public annotation class InternalGameServicesApi

/** Keeps a cancelled UI request reserved until its native result arrives. */
@InternalGameServicesApi
public class ProviderUiRequest<T> {
    private sealed interface State<out T> {
        data object Idle : State<Nothing>
        data object Closed : State<Nothing>
        class Pending<T>(val result: CompletableDeferred<T>) : State<T>
    }
    private val state = MutableStateFlow<State<T>>(State.Idle)

    public suspend fun launchAndAwait(launch: () -> Unit): T {
        val request = State.Pending(CompletableDeferred<T>())
        check(state.compareAndSet(State.Idle, request)) { "A provider screen is already open or its owner was destroyed" }
        try {
            launch()
        } catch (error: Throwable) {
            state.compareAndSet(request, State.Idle)
            request.result.cancel()
            throw error
        }
        return request.result.await()
    }

    public fun complete(value: T) {
        val previous = state.getAndUpdate { if (it is State.Pending) State.Idle else it }
        if (previous is State.Pending) previous.result.complete(value)
    }

    public fun close() {
        val previous = state.getAndUpdate { State.Closed }
        if (previous is State.Pending) previous.result.cancel()
    }
}
