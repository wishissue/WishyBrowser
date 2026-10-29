package org.wishy.browser.ui.pages

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.wishy.browser.BrowserController
import org.wishy.browser.DlItem
import org.wishy.browser.DlState
import org.wishy.browser.DownloadHub
import org.wishy.browser.ui.ActionRow
import org.wishy.browser.ui.PageScaffold
import org.wishy.browser.ui.Pause
import org.wishy.browser.ui.Sym
import org.wishy.browser.ui.TvIconButton

/** Downloads: progress, pause, resume, cancel, retry and open. Every control is a button the remote can reach. */
@Composable
fun DownloadsPage(c: BrowserController) {
    val list = c.downloads.toList().asReversed()
    PageScaffold("Downloads", { c.popPage() }) {
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Sym.Download, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("No downloads yet", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "When a page offers a file, you are asked first. Finished files are saved in your Downloads folder.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (list.any { it.state == DlState.DONE || it.state == DlState.FAILED }) item {
                    ActionRow(Sym.DeleteSweep, "Clear finished", "Take finished and failed items off the list (files stay)") { DownloadHub.clearFinished() }
                }
                items(list, key = { it.id }) { d -> DownloadRow(c, d) }
            }
        }
    }
}

@Composable
private fun DownloadRow(c: BrowserController, d: DlItem) {
    val ctx = LocalContext.current
    fun fmt(n: Long) = Formatter.formatShortFileSize(ctx, n)
    val shape = RoundedCornerShape(24.dp)
    val state = d.state
    val running = state == DlState.RUNNING || state == DlState.QUEUED
    val total = d.total
    val pct = if (total > 0) (d.done * 100 / total).toInt().coerceIn(0, 100) else -1
    val status = when (state) {
        DlState.QUEUED -> "Waiting"
        DlState.RUNNING ->
            (if (pct >= 0) "$pct%  \u00B7  ${fmt(d.done)} of ${fmt(total)}" else fmt(d.done)) +
                (if (d.speed > 0) "  \u00B7  ${fmt(d.speed)}/s" else "")
        DlState.PAUSED -> if (pct >= 0) "Paused  \u00B7  $pct%" else "Paused"
        DlState.FAILED -> "Failed: ${d.error ?: "unknown problem"}"
        DlState.DONE -> "Saved in ${d.savedWhere ?: "Downloads"}" + if (total > 0) "  \u00B7  ${fmt(total)}" else ""
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(
                        if (state == DlState.FAILED) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(14.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (state) { DlState.DONE -> Sym.CheckCircleFilled; DlState.FAILED -> Sym.Error; else -> Sym.Download },
                        null, Modifier.size(24.dp),
                        tint = if (state == DlState.FAILED) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        status, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (state == DlState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (state) {
                        DlState.QUEUED, DlState.RUNNING -> {
                            TvIconButton(Sym.Pause, "Pause", { DownloadHub.pause(d) }, size = 44.dp)
                            TvIconButton(Sym.Close, "Cancel", { DownloadHub.cancel(d) }, size = 44.dp)
                        }
                        DlState.PAUSED -> {
                            TvIconButton(Sym.PlayArrow, "Resume", { DownloadHub.resume(d) }, size = 44.dp)
                            TvIconButton(Sym.Close, "Cancel", { DownloadHub.cancel(d) }, size = 44.dp)
                        }
                        DlState.FAILED -> {
                            TvIconButton(Sym.Refresh, "Try again", { DownloadHub.resume(d) }, size = 44.dp)
                            TvIconButton(Sym.DeleteOutline, "Remove", { DownloadHub.remove(d) }, size = 44.dp)
                        }
                        DlState.DONE -> {
                            TvIconButton(Sym.OpenInNew, "Open", { c.openDownload(d) }, size = 44.dp)
                            TvIconButton(Sym.DeleteOutline, "Remove from list", { DownloadHub.remove(d) }, size = 44.dp)
                        }
                    }
                }
            }
            if (running || state == DlState.PAUSED) {
                Spacer(Modifier.height(10.dp))
                if (pct >= 0) LinearProgressIndicator({ pct / 100f }, Modifier.fillMaxWidth())
                else if (state == DlState.RUNNING) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
