@file:OptIn(ExperimentalMaterial3Api::class)

package com.ballknowledge.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ballknowledge.data.Repository
import com.ballknowledge.export.ExportActions
import com.ballknowledge.export.HtmlExporter
import com.ballknowledge.logic.TeamNames
import com.ballknowledge.logic.TimeFormat
import com.ballknowledge.logic.TransferMath
import com.ballknowledge.model.SpendKeys
import com.ballknowledge.model.TransferWindow
import com.ballknowledge.model.WindowKind
import java.time.LocalDate

/** The Transfers tab: all windows, by season and then team. Separate from match pages. */
@Composable
fun TransfersScreen(repo: Repository, onOpenWindow: (String) -> Unit) {
    val windows by repo.windows.collectAsState()
    val teams by repo.teams.collectAsState()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }

    val grouped: List<Pair<Int, List<Pair<String, List<TransferWindow>>>>> = remember(windows, teams) {
        windows.groupBy { it.seasonStart }
            .toList()
            .sortedByDescending { it.first }
            .map { (season, list) ->
                val byTeam = list.groupBy { w -> TeamNames.find(w.team, teams)?.name ?: w.team.ifBlank { "No team" } }
                val order = teams.map { it.name }
                val sortedTeams = byTeam.keys.sortedWith(compareBy<String> { name ->
                    order.indexOf(name).let { if (it < 0) Int.MAX_VALUE else it }
                }.thenBy { it })
                season to sortedTeams.map { team ->
                    // Winter (later) before summer within a season: newest first.
                    team to byTeam.getValue(team).sortedWith(compareByDescending<TransferWindow> { it.year }.thenByDescending { it.kind.ordinal })
                }
            }
    }

    val exportName = "Ball Knowledge — transfer windows (${LocalDate.now()})"
    val saveHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) {
            runCatching { ExportActions.writeText(context, uri, HtmlExporter.buildTransfers(repo.windows.value, exportName)) }
                .onSuccess { Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Transfers", fontWeight = FontWeight.Bold) },
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = windows.isNotEmpty()) {
                        Icon(Icons.Filled.Share, contentDescription = "Export")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Share all as HTML") }, onClick = {
                            menuOpen = false
                            repo.flush()
                            ExportActions.shareHtml(context, HtmlExporter.buildTransfers(repo.windows.value, exportName), "ball-knowledge-transfers-${LocalDate.now()}")
                        })
                        DropdownMenuItem(text = { Text("Save all as HTML file") }, onClick = {
                            menuOpen = false
                            repo.flush()
                            saveHtml.launch("ball-knowledge-transfers-${LocalDate.now()}.html")
                        })
                        DropdownMenuItem(text = { Text("Save all as PDF") }, onClick = {
                            menuOpen = false
                            ExportActions.printHtml(context, HtmlExporter.buildTransfers(repo.windows.value, exportName), "Ball Knowledge transfers ${LocalDate.now()}")
                        })
                    }
                }
                IconButton(onClick = { showNew = true }) { Icon(Icons.Filled.Add, contentDescription = "New window") }
            },
        )

        if (windows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No transfer windows yet. Tap + to start tracking one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            grouped.forEach { (season, teamGroups) ->
                item(key = "season-$season") {
                    Text(
                        TimeFormat.seasonLabel(season),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 2.dp),
                    )
                }
                teamGroups.forEach { (team, list) ->
                    item(key = "team-$season-$team") {
                        Text(
                            team,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { "w-${it.id}" }) { w ->
                        WindowRow(w) { onOpenWindow(w.id) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    if (showNew) {
        NewWindowDialog(
            teamNames = teams.map { it.name },
            onDismiss = { showNew = false },
            onCreate = { team, kind, year ->
                showNew = false
                onOpenWindow(repo.createWindow(team, kind, year).id)
            },
        )
    }
}

@Composable
private fun WindowRow(w: TransferWindow, onClick: () -> Unit) {
    val net = TransferMath.displayTotals(w)[SpendKeys.NET].orEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${w.kind.label} ${w.year}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                TransferMath.summary(w),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("Net", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(net, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NewWindowDialog(teamNames: List<String>, onDismiss: () -> Unit, onCreate: (String, WindowKind, Int) -> Unit) {
    val today = LocalDate.now(TimeFormat.IST)
    var team by remember { mutableStateOf(teamNames.firstOrNull().orEmpty()) }
    // Default: the next window to open (summer from Feb–Aug, otherwise the coming winter).
    var kind by remember { mutableStateOf(if (today.monthValue in 2..8) WindowKind.SUMMER else WindowKind.WINTER) }
    var yearText by remember {
        mutableStateOf((if (today.monthValue >= 9) today.year + 1 else today.year).toString())
    }
    val year = yearText.trim().toIntOrNull()?.takeIf { it in 1900..2200 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New transfer window") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = team, onValueChange = { team = it }, label = { Text("Team") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    teamNames.forEach { name ->
                        FilterChip(selected = team == name, onClick = { team = name }, label = { Text(name, maxLines = 1) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WindowKind.values().forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
                    }
                }
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it },
                    label = { Text("Year") },
                    supportingText = { Text(if (year == null) "Enter a year" else "Season ${TimeFormat.seasonLabel(if (kind == WindowKind.SUMMER) year else year - 1)}") },
                    isError = year == null,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = year != null && team.isNotBlank(), onClick = { year?.let { onCreate(team, kind, it) } }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
