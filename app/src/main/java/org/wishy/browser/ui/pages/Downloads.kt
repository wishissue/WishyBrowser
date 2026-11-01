package org.wishy.browser.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wishy.browser.BrowserController
import org.wishy.browser.downloads.DlState
import org.wishy.browser.downloads.DownloadItem
import org.wishy.browser.downloads.DownloadsManager
import org.wishy.browser.downloads.SavedFile
import org.wishy.browser.ui.*

@Composable
fun DownloadsPage(c: BrowserController) {
    val list = c.downloads.items
    var saved by remember { mutableStateOf(emptyList<SavedFile>()) }
    var refresh by remember { mutableIntStateOf(0) }
    val finished = list.count { it.state == DlState.DONE }
    LaunchedEffect(finished, refresh) { saved = withContext(Dispatchers.IO) { c.downloads.savedFiles() } }

    PageScaffold("Downloads", { c.popPage() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ActionRow(Sym.Folder, "Open Downloads folder", "Uses the file manager on this TV, if it has one") { c.openDownloadsFolder() } }
            if (list.isEmpty() && saved.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Sym.Download, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No downloads yet", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "When a page offers a file, it shows up here with its progress.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                    )
                }
            }
            if (list.isNotEmpty()) {
                item { SectionLabel("This session") }
                items(list, key = { it.id }) { DownloadRow(c, it) }
                item { ActionRow(Sym.DeleteSweep, "Clear finished", "Removes finished, failed and canceled entries from this list") { c.downloads.clearFinished() } }
            }
            if (saved.isNotEmpty()) {
                item { SectionLabel("Files in Downloads") }
                items(saved, key = { it.uri.toString() }) { f ->
                    ActionRow(
                        Sym.Description, f.name, DownloadsManager.formatSize(f.size),
                        trailing = { TvIconButton(Sym.DeleteOutline, "Delete file", { if (c.downloads.deleteSaved(f)) refresh++ }, size = 44.dp) },
                    ) { c.openSaved(f) }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(c: BrowserController, d: DownloadItem) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(cs.secondaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(if (d.state == DlState.DONE) Sym.CheckCircle else if (d.state == DlState.FAILED) Sym.Error else Sym.Download, null, Modifier.size(24.dp), tint = cs.onSecondaryContainer)
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status(d), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    d.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error, maxLines = 2) }
                }
            }
            if (d.state == DlState.RUNNING || d.state == DlState.QUEUED || d.state == DlState.PAUSED) {
                val modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                if (d.total > 0) LinearProgressIndicator({ (d.done.toFloat() / d.total).coerceIn(0f, 1f) }, modifier)
                else if (d.state == DlState.RUNNING) LinearProgressIndicator(modifier)
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (d.state) {
                    DlState.RUNNING, DlState.QUEUED -> {
                        TvIconButton(Sym.Pause, "Pause", { c.pauseDownload(d) }, size = 44.dp)
                        TvIconButton(Sym.Close, "Cancel", { c.cancelDownload(d) }, size = 44.dp)
                    }
                    DlState.PAUSED -> {
                        TvIconButton(Sym.PlayArrow, "Resume", { c.resumeDownload(d) }, size = 44.dp)
                        TvIconButton(Sym.Close, "Cancel", { c.cancelDownload(d) }, size = 44.dp)
                    }
                    DlState.FAILED, DlState.CANCELED -> {
                        TvIconButton(Sym.Refresh, "Retry", { c.downloads.retry(d) }, size = 44.dp)
                        TvIconButton(Sym.DeleteOutline, "Remove", { c.removeDownload(d) }, size = 44.dp)
                    }
                    DlState.DONE -> {
                        TvIconButton(Sym.OpenInNew, if (d.isApk) "Install" else "Open", { c.openDownload(d) }, size = 44.dp)
                        TvIconButton(Sym.DeleteOutline, "Remove from list", { c.removeDownload(d) }, size = 44.dp)
                    }
                }
            }
        }
    }
}

private fun status(d: DownloadItem): String = when (d.state) {
    DlState.QUEUED -> "Waiting to start"
    DlState.RUNNING -> if (d.total > 0) "${DownloadsManager.formatSize(d.done)} of ${DownloadsManager.formatSize(d.total)}" else "${DownloadsManager.formatSize(d.done)} so far"
    DlState.PAUSED -> "Paused at ${DownloadsManager.formatSize(d.done)}"
    DlState.DONE -> "Saved to Downloads · ${DownloadsManager.formatSize(d.total)}"
    DlState.FAILED -> "Failed: ${d.error ?: "unknown problem"}"
    DlState.CANCELED -> "Canceled"
}
