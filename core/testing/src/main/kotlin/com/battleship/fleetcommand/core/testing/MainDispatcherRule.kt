// FILE: core/testing/src/main/kotlin/com/battleship/fleetcommand/core/testing/MainDispatcherRule.kt

package com.battleship.fleetcommand.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension that replaces [Dispatchers.Main] with a [TestDispatcher]
 * before each test and restores it afterwards.
 *
 * Usage:
 * ```kotlin
 * @ExtendWith(MainDispatcherRule::class)
 * class MyViewModelTest { ... }
 * ```
 */
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}