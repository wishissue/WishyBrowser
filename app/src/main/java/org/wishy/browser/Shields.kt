package org.wishy.browser

/**
 * Status of the built-in protections. The blocker is native code inside the app now (see engine/Blocker.kt),
 * so there is nothing to install or start: it is on whenever the app runs.
 */
object Shields {
    val ready: Boolean = true
    val blockerActive: Boolean = true
}
