package org.wishy.browser

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import com.google.android.material.button.MaterialButton

/**
 * One place for the "pop" animation when the remote lands on something:
 * buttons and tiles spring up slightly, list rows lift a little, and they
 * settle back when focus leaves. Attach once per window.
 */
object FocusAnim {

    /** Scale a view should have while focused (1f = don't animate). */
    fun scaleFor(v: View): Float = when {
        v is EditText -> 1f
        v.tag == "row" -> 1.03f
        v.tag == "tile" -> 1.1f
        v is MaterialButton -> 1.12f
        else -> 1f
    }

    fun attach(root: View) {
        root.viewTreeObserver.addOnGlobalFocusChangeListener { old, new ->
            old?.let { animate(it, false) }
            new?.let { animate(it, true) }
        }
    }

    private fun animate(v: View, focused: Boolean) {
        val target = scaleFor(v)
        if (target == 1f) return
        val to = if (focused) target else 1f
        v.animate().cancel()
        v.animate()
            .scaleX(to).scaleY(to)
            .setDuration(if (focused) 150L else 100L)
            .setInterpolator(if (focused) OvershootInterpolator(1.5f) else DecelerateInterpolator())
            .start()
    }
}
