@file:OptIn(ExperimentalMaterial3Api::class)

package com.ballknowledge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ballknowledge.data.Repository
import com.ballknowledge.logic.TimeFormat
import com.ballknowledge.model.Competitions
import com.ballknowledge.model.Fixture
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.MatchState
import com.ballknowledge.model.isFollowedBy
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

private sealed interface FixtureRowItem {
    data class Header(val title: String) : FixtureRowItem
    data class Item(val fixture: Fixture) : FixtureRowItem
}

@Composable
fun FixturesScreen(repo: Repository, onOpenPage: (String) -> Unit) {
    val cache by repo.fixtures.collectAsState()
    val archive by repo.archive.collectAsState()
    val teams by repo.teams.collectAsState()
    val pages by repo.pages.collectAsState()
    val sync by repo.sync.collectAsState()

    // null = the current season; otherwise a finished season from the archive.
    var viewSeason by rememberSaveable { mutableStateOf<Int?>(null) }
    val pastSeason = viewSeason?.takeIf { it in archive && it != cache.season }
    val source = if (pastSeason != null) archive.getValue(pastSeason) else cache.fixtures
    val fixtures = remember(source, teams) { source.filter { it.isFollowedBy(teams) }.sortedBy { it.kickoffMillis } }
    val pageByFixture: Map<String, MatchPage> = remember(pages) {
        pages.mapNotNull { p -> p.fixtureId?.let { it to p } }.toMap()
    }
    val rows: List<FixtureRowItem> = remember(fixtures) {
        val out = mutableListOf<FixtureRowItem>()
        var lastMonth = ""
        fixtures.forEach { f ->
            val month = TimeFormat.monthHeader(f.kickoffMillis)
            if (month != lastMonth) {
                out.add(FixtureRowItem.Header(month))
                lastMonth = month
            }
            out.add(FixtureRowItem.Item(f))
        }
        out
    }

    var confirm by remember { mutableStateOf<Fixture?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Open at "today": the first match that hasn't finished yet.
    var scrolled by remember { mutableStateOf(false) }
    LaunchedEffect(rows.size, pastSeason) {
        if (pastSeason != null) {
            listState.scrollToItem(0)
            scrolled = false
            return@LaunchedEffect
        }
        if (!scrolled && rows.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val target = rows.indexOfFirst {
                it is FixtureRowItem.Item && (it.fixture.state == MatchState.LIVE || it.fixture.kickoffMillis > now - 3 * 60 * 60 * 1000L)
            }.let { if (it < 0) rows.lastIndex else it }
            val withHeader = if (target > 0 && rows[target - 1] is FixtureRowItem.Header) target - 1 else target
            listState.scrollToItem(withHeader.coerceAtLeast(0))
            scrolled = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { AppTitle() },
            actions = {
                if (pastSeason != null) {
                    // Finished seasons are a frozen record; nothing to refresh.
                } else if (sync.running) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    IconButton(onClick = { repo.requestRefresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh fixtures") }
                }
                IconButton(onClick = { showNew = true }) { Icon(Icons.Filled.Add, contentDescription = "New match") }
            },
        )

        val seasons = (listOf(cache.season) + archive.keys).filter { it > 0 }.distinct().sortedDescending()
        if (seasons.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                seasons.forEach { s ->
                    val selected = (pastSeason ?: cache.season) == s
                    FilterChip(
                        selected = selected,
                        onClick = { viewSeason = if (s == cache.season) null else s },
                        label = { Text(TimeFormat.seasonLabel(s) + if (s == cache.season) " (now)" else "") },
                    )
                }
            }
        }

        sync.error?.let { message ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { repo.clearSyncError() }) { Icon(Icons.Filled.Close, contentDescription = "Dismiss") }
                }
            }
        }

        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        sync.running -> "Loading this season's fixtures…"
                        teams.isEmpty() -> "You're not following any teams. Add some in Settings."
                        else -> "No fixtures saved yet. Tap refresh to load them, or add a match by hand with +."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(rows, key = { row ->
                    when (row) {
                        is FixtureRowItem.Header -> "h-" + row.title
                        is FixtureRowItem.Item -> "f-" + row.fixture.id
                    }
                }) { row ->
                    when (row) {
                        is FixtureRowItem.Header -> Text(
                            row.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
                        )
                        is FixtureRowItem.Item -> {
                            val page = pageByFixture[row.fixture.id]
                            FixtureRow(row.fixture, page, past = pastSeason != null) {
                                if (page != null) onOpenPage(page.id) else confirm = row.fixture
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
                item { Spacer(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }

    confirm?.let { f ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Start a match page?") },
            text = { Text("${f.homeName} vs ${f.awayName}\n${Competitions.label(f.league)} · ${TimeFormat.shortDate(f.kickoffMillis)}") },
            confirmButton = {
                TextButton(onClick = {
                    val page = repo.createPageFor(f)
                    confirm = null
                    onOpenPage(page.id)
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }

    if (showNew) {
        NewMatchDialog(
            onDismiss = { showNew = false },
            onCreate = { home, away, comp, date ->
                val page = repo.createManualPage(home, away, comp, date)
                showNew = false
                onOpenPage(page.id)
            },
        )
    }
}

@Composable
private fun FixtureRow(f: Fixture, page: MatchPage?, past: Boolean, onClick: () -> Unit) {
    // In a finished season every match that kicked off is over, even if the last sync missed its result.
    val finished = f.state == MatchState.FINAL || (past && f.state != MatchState.POSTPONED && f.kickoffMillis < System.currentTimeMillis())
    val dnw = finished && page == null
    val score = if (f.homeScore != null && f.awayScore != null && (finished || f.state == MatchState.LIVE)) "${f.homeScore}–${f.awayScore}" else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (dnw) 0.55f else 1f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompBadge(Competitions.label(f.league))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (score != null) "${f.homeName} $score ${f.awayName}" else "${f.homeName} vs ${f.awayName}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (page != null) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                kickoffText(TimeFormat.kickoff(f.kickoffMillis, f.venueCountry)) +
                    androidx.compose.ui.text.AnnotatedString(f.matchday?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            page != null -> Icon(Icons.Filled.Edit, contentDescription = "Has a match page", tint = MaterialTheme.colorScheme.primary)
            dnw -> StatusPill("DNW", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            f.state == MatchState.LIVE -> StatusPill("LIVE", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.surface)
            f.state == MatchState.POSTPONED -> StatusPill("PP", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NewMatchDialog(onDismiss: () -> Unit, onCreate: (String, String, String, Long) -> Unit) {
    var home by remember { mutableStateOf("") }
    var away by remember { mutableStateOf("") }
    var comp by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New match") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(value = home, onValueChange = { home = it }, label = { Text("Home team") }, singleLine = true)
                OutlinedTextField(value = away, onValueChange = { away = it }, label = { Text("Away team") }, singleLine = true)
                OutlinedTextField(value = comp, onValueChange = { comp = it }, label = { Text("Competition (e.g. PL, UCL)") }, singleLine = true)
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Date: " + TimeFormat.shortDate(dateMillis))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(home, away, comp, dateMillis) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utcMidnight ->
                        // The picker returns UTC midnight; store midday IST of that date.
                        val date = Instant.ofEpochMilli(utcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
                        dateMillis = date.atTime(LocalTime.NOON).atZone(TimeFormat.IST).toInstant().toEpochMilli()
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
