@file:OptIn(ExperimentalMaterial3Api::class)

package com.ballknowledge.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.ballknowledge.model.Fields
import com.ballknowledge.model.MatchPage
import java.time.LocalDate

@Composable
fun ArchiveScreen(repo: Repository, onOpenPage: (String) -> Unit) {
    val pages by repo.pages.collectAsState()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val teams by repo.teams.collectAsState()

    // Season (newest first) -> your teams in Settings order -> that team's matches, newest first.
    // A match between two of your teams appears under both; unmatched pages go under "Other".
    val grouped: List<Pair<Int, List<Pair<String, List<MatchPage>>>>> = remember(pages, teams) {
        pages.sortedByDescending { it.sortMillis }
            .groupBy { TimeFormat.seasonStartYear(it.sortMillis) }
            .toList()
            .map { (season, list) ->
                val byTeam = teams.mapNotNull { t ->
                    val mine = list.filter { p ->
                        TeamNames.find(p.field(Fields.HOME), listOf(t)) != null || TeamNames.find(p.field(Fields.AWAY), listOf(t)) != null
                    }
                    if (mine.isEmpty()) null else t.name to mine
                }
                val other = list.filter { p ->
                    TeamNames.find(p.field(Fields.HOME), teams) == null && TeamNames.find(p.field(Fields.AWAY), teams) == null
                }
                season to (if (other.isEmpty()) byTeam else byTeam + ("Other" to other))
            }
    }
    val seasonCounts: Map<Int, Int> = remember(pages) {
        pages.groupingBy { TimeFormat.seasonStartYear(it.sortMillis) }.eachCount()
    }

    val exportName = "Ball Knowledge — match pages (${LocalDate.now()})"
    val saveHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) {
            runCatching { ExportActions.writeText(context, uri, HtmlExporter.build(repo.pages.value, exportName)) }
                .onSuccess { Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Archive", fontWeight = FontWeight.Bold) },
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = pages.isNotEmpty()) {
                        Icon(Icons.Filled.Share, contentDescription = "Export")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Share all as HTML") }, onClick = {
                            menuOpen = false
                            repo.flush()
                            ExportActions.shareHtml(context, HtmlExporter.build(repo.pages.value, exportName), "ball-knowledge-${LocalDate.now()}")
                        })
                        DropdownMenuItem(text = { Text("Save all as HTML file") }, onClick = {
                            menuOpen = false
                            repo.flush()
                            saveHtml.launch("ball-knowledge-${LocalDate.now()}.html")
                        })
                        DropdownMenuItem(text = { Text("Save all as PDF") }, onClick = {
                            menuOpen = false
                            ExportActions.printHtml(context, HtmlExporter.build(repo.pages.value, exportName), "Ball Knowledge ${LocalDate.now()}")
                        })
                    }
                }
            },
        )

        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No match pages yet. Pick a match from Fixtures to start one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            grouped.forEach { (season, teamGroups) ->
                val count = seasonCounts[season] ?: 0
                item(key = "season-$season") {
                    Text(
                        "${TimeFormat.seasonLabel(season)} · $count ${if (count == 1) "match" else "matches"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 2.dp),
                    )
                }
                teamGroups.forEach { (team, list) ->
                    item(key = "team-$season-$team") {
                        Text(
                            "$team · ${list.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { "p-$season-$team-${it.id}" }) { p ->
                        ArchiveRow(p) { onOpenPage(p.id) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveRow(p: MatchPage, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompBadge(p.field(Fields.COMPETITION).take(6))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(HtmlExporter.teamsLine(p), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val title = p.field(Fields.TITLE)
            Text(
                listOf(TimeFormat.shortDate(p.sortMillis), p.field(Fields.MATCHDAY), title).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val rating = p.field(Fields.RATING)
        val attention = p.field(Fields.ATTENTION)
        if (rating.isNotBlank() || attention.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (rating.isNotBlank()) Text("R $rating", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (attention.isNotBlank()) Text("A $attention", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
