package io.github.jabbottiii.aegis.testing.common

import io.github.jabbottiii.aegis.core.common.coroutines.AegisDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * Routes every dispatcher to one [TestDispatcher] on the test's own scheduler, so `runTest`
 * controls all scheduling, including virtual time:
 *
 * ```
 * @Test fun scans() = runTest {
 *     val dispatchers = TestAegisDispatchers(testScheduler)
 *     ...
 *     advanceUntilIdle()
 * }
 * ```
 */
class TestAegisDispatchers(
    scheduler: TestCoroutineScheduler,
) : AegisDispatchers {
    constructor(scope: TestScope) : this(scope.testScheduler)

    val testDispatcher: TestDispatcher = StandardTestDispatcher(scheduler)

    override val default: TestDispatcher get() = testDispatcher
    override val io: TestDispatcher get() = testDispatcher
    override val main: TestDispatcher get() = testDispatcher
}
