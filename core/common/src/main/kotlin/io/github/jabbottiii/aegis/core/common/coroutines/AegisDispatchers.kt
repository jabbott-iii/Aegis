package io.github.jabbottiii.aegis.core.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Injected coroutine dispatchers. Code never names `Dispatchers.*` directly. The app's DI root
 * provides the platform dispatchers, and tests provide a test dispatcher.
 */
public interface AegisDispatchers {
    public val default: CoroutineDispatcher
    public val io: CoroutineDispatcher
    public val main: CoroutineDispatcher
}
