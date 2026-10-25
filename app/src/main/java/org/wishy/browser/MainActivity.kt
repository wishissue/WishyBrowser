package org.wishy.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.wishy.browser.ui.WishyApp

/**
 * Thin host: everything you see is Compose (see ui/), and everything the browser does lives in
 * [BrowserController]. This class only does what an Activity must: key events, result launchers,
 * system bars, and the first page load.
 */
class MainActivity : ComponentActivity(), ActivityBridge {

    private lateinit var controller: BrowserController

    private var filesCallback: ((Array<Uri>?) -> Unit)? = null
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = filesCallback
        filesCallback = null
        val data = result.data
        val uris: Array<Uri>? = when {
            result.resultCode != RESULT_OK -> null
            data?.clipData != null -> data.clipData!!.let { clip -> Array(clip.itemCount) { i -> clip.getItemAt(i).uri } }
            data?.data != null -> arrayOf(data.data!!)
            else -> null
        }
        cb?.invoke(uris)
    }

    private var permissionCallback: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val cb = permissionCallback
        permissionCallback = null
        cb?.invoke(grants.values.all { it })
    }

    override fun pickFiles(intent: Intent, onResult: (Array<Uri>?) -> Unit) {
        filesCallback = onResult
        filePicker.launch(intent)
    }

    override fun requestPermissions(perms: Array<String>, onResult: (Boolean) -> Unit) {
        permissionCallback = onResult
        permissionLauncher.launch(perms)
    }

    override fun finishApp() = finish()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = BrowserApplication.from(application)
        controller = BrowserController(this, app.runtime, this)

        setContent {
            val view = LocalView.current
            LaunchedEffect(controller.isFullScreen) {
                val bars = WindowCompat.getInsetsController(window, view)
                if (controller.isFullScreen) {
                    bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    bars.hide(WindowInsetsCompat.Type.systemBars())
                } else bars.show(WindowInsetsCompat.Type.systemBars())
            }
            LaunchedEffect(Unit) {
                // Give Wishy Core the pointer-snapping channel as soon as it exists.
                launch { controller.attachCore(snapshotFlow { Shields.core }.filterNotNull().first()) }
                // Count what uBlock blocks (it also changes after "Reset blocker", so keep collecting).
                launch { snapshotFlow { Shields.ublockExt }.filterNotNull().collect { controller.attachBlocker(it) } }
                // Load the initial target immediately so navigation starts instantly.
                controller.session.loadUri(viewIntentUrl(intent) ?: controller.homeTarget())
            }
            WishyApp(controller)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewIntentUrl(intent)?.let { controller.open(it) }
    }

    private fun viewIntentUrl(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        (::controller.isInitialized && controller.handleKey(event)) || super.dispatchKeyEvent(event)

    override fun onPause() {
        if (::controller.isInitialized) controller.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.onDestroy()
        super.onDestroy()
    }
}
