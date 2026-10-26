@file:OptIn(ExperimentalMaterial3Api::class)

package com.ballknowledge.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ballknowledge.data.Repository
import com.ballknowledge.data.RestorePlan
import com.ballknowledge.export.ExportActions
import com.ballknowledge.logic.TimeFormat
import com.ballknowledge.model.TeamRef
import java.time.LocalDate

@Composable
fun SettingsScreen(repo: Repository) {
    val teams by repo.teams.collectAsState()
    val cache by repo.fixtures.collectAsState()
    val sync by repo.sync.collectAsState()
    val context = LocalContext.current

    var addTeam by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<TeamRef?>(null) }
    var restorePlan by remember { mutableStateOf<RestorePlan?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            repo.flush()
            runCatching { ExportActions.writeText(context, uri, repo.backupJson()) }
                .onSuccess { Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Couldn't save the backup: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { repo.planRestore(ExportActions.readText(context, uri)) }
                .onSuccess { plan ->
                    if (plan.conflicts > 0) {
                        restorePlan = plan
                    } else {
                        val n = repo.applyRestore(plan, replaceConflicts = false)
                        Toast.makeText(context, "Restored $n match ${if (n == 1) "page" else "pages"}", Toast.LENGTH_LONG).show()
                    }
                }
                .onFailure { Toast.makeText(context, it.message ?: "Couldn't read that file.", Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("Teams you follow")
            if (teams.isEmpty()) {
                Text("None yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            teams.forEach { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { removing = t }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${t.name}") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            OutlinedButton(onClick = { addTeam = true }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add a team")
            }

            SectionTitle("Fixtures")
            Text(
                if (cache.lastSyncMillis > 0) "Last updated ${TimeFormat.syncTime(cache.lastSyncMillis)} IST · ${TimeFormat.seasonLabel(cache.season)} season"
                else "Not updated yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { repo.requestRefresh(fullSeason = true) },
                enabled = !sync.running,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(if (sync.running) "Updating…" else "Re-sync whole season") }

            SectionTitle("Backup")
            Text(
                "A backup holds every match page and your team list. Keep a copy somewhere safe (e.g. Google Drive) so you never lose your diary if you change phones.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { backupLauncher.launch("ball-knowledge-backup-${LocalDate.now()}.json") }) { Text("Back up") }
                Text("  ")
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Restore") }
            }

            SectionTitle("About")
            Text(
                "Ball Knowledge 1.0\nMatch data comes from ESPN's public (unofficial) feed. If it ever stops working, everything still works by hand — only the auto-fill pauses.\nTimes are shown in IST, with UK time in brackets. * means the venue's local time differs from UK time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }

    removing?.let { t ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Stop following ${t.name}?") },
            text = { Text("Their fixtures will disappear from the list. Match pages you've written stay in the Archive.") },
            confirmButton = { TextButton(onClick = { repo.removeTeam(t.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    restorePlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { restorePlan = null },
            title = { Text("Some pages already exist") },
            text = {
                Text(
                    "${plan.conflicts} match ${if (plan.conflicts == 1) "page" else "pages"} in this backup already exist here with different content. " +
                        "Replace them with the backup's version, or keep what's on the phone? " +
                        "(${plan.newPages} new ${if (plan.newPages == 1) "page" else "pages"} will be added either way.)"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = repo.applyRestore(plan, replaceConflicts = true)
                    restorePlan = null
                    Toast.makeText(context, "Restored $n match ${if (n == 1) "page" else "pages"}", Toast.LENGTH_LONG).show()
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val n = repo.applyRestore(plan, replaceConflicts = false)
                    restorePlan = null
                    Toast.makeText(context, "Restored $n match ${if (n == 1) "page" else "pages"}", Toast.LENGTH_LONG).show()
                }) { Text("Keep mine") }
            },
        )
    }

    if (addTeam) {
        AddTeamDialog(repo, onDismiss = { addTeam = false })
    }
}

@Composable
private fun AddTeamDialog(repo: Repository, onDismiss: () -> Unit) {
    val following by repo.teams.collectAsState()
    var options by remember { mutableStateOf<List<TeamRef>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { repo.availableTeams() }
            .onSuccess { options = it }
            .onFailure { failed = true }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a team") },
        text = {
            when {
                failed -> Text("Couldn't load the team list. Check your connection and try again.")
                options == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    val list = options.orEmpty().filter { o -> following.none { it.id == o.id } }
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(list, key = { it.id }) { t ->
                            Text(
                                t.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        repo.addTeam(t)
                                        onDismiss()
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
