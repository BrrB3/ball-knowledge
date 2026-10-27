@file:OptIn(ExperimentalMaterial3Api::class)

package com.ballknowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.SnackbarDuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ballknowledge.data.Repository
import com.ballknowledge.export.ExportActions
import com.ballknowledge.export.HtmlExporter
import com.ballknowledge.export.PlainTextExporter
import com.ballknowledge.logic.Autofill
import com.ballknowledge.logic.MatchClock
import com.ballknowledge.logic.UndoHistory
import com.ballknowledge.model.DiaryEntry
import com.ballknowledge.model.Fields
import com.ballknowledge.model.MatchPage
import com.ballknowledge.model.Phase
import com.ballknowledge.model.Stats
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun MatchScreen(repo: Repository, pageId: String, onBack: () -> Unit) {
    val initial = remember(pageId) { repo.page(pageId) }
    if (initial == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var page by remember(pageId) { mutableStateOf(initial) }
    val history = remember(pageId) { UndoHistory<MatchPage>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun show(p: MatchPage) {
        page = p
        repo.updatePage(p)
    }

    /**
     * Every change goes through here so it can be undone. [key] groups typing in one box.
     * Only my own changes move "Last edited"; automatic refreshes don't.
     */
    fun change(p: MatchPage, key: String?, byUser: Boolean = true) {
        if (p == page) return
        val now = System.currentTimeMillis()
        history.record(page, key, now)
        show(if (byUser) p.copy(editedAt = now, updatedAt = now) else p)
    }

    fun setField(key: String, value: String) {
        change(page.copy(fields = page.fields + (key to value), edited = page.edited + key), key)
    }

    fun undoRedo(target: MatchPage?) {
        target ?: return
        val now = System.currentTimeMillis()
        show(target.copy(editedAt = now, updatedAt = now))
    }

    fun refresh(quiet: Boolean) {
        if (refreshing || page.fixtureId == null) return
        scope.launch {
            refreshing = true
            val result = repo.fetchAutofill(page)
            if (result.values.isNotEmpty()) {
                change(Autofill.apply(page, result.values, System.currentTimeMillis(), result.sortMillis), null, byUser = false)
            }
            refreshing = false
            // A quiet refresh (on opening the page) only speaks up when something actually failed.
            val message = if (quiet) result.errors.joinToString("\n").ifBlank { null } else result.message ?: "Match data updated."
            message?.let { snackbar.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long) }
        }
    }

    // Quietly fetch lineups / result when they're likely available and still missing.
    LaunchedEffect(pageId) {
        if (Autofill.wantsDetails(page, repo.fixture(page.fixtureId), System.currentTimeMillis())) refresh(quiet = true)
    }

    val home = page.field(Fields.HOME).ifBlank { "Home" }
    val away = page.field(Fields.AWAY).ifBlank { "Away" }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    Text(HtmlExporter.teamsLine(page), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                },
                actions = {
                    IconButton(onClick = { undoRedo(history.undo(page)) }, enabled = history.canUndo) {
                        Icon(AppIcons.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { undoRedo(history.redo(page)) }, enabled = history.canRedo) {
                        Icon(AppIcons.Redo, contentDescription = "Redo")
                    }
                    if (page.fixtureId != null) {
                        if (refreshing) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = { refresh(quiet = false) }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh match data") }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Share as text") }, onClick = {
                                menuOpen = false
                                ExportActions.shareText(context, PlainTextExporter.match(page), HtmlExporter.teamsLine(page))
                            })
                            DropdownMenuItem(text = { Text("Share as HTML") }, onClick = {
                                menuOpen = false
                                repo.flush()
                                val name = HtmlExporter.teamsLine(page)
                                ExportActions.shareHtml(context, HtmlExporter.build(listOf(page), name), name)
                            })
                            DropdownMenuItem(text = { Text("Save as PDF") }, onClick = {
                                menuOpen = false
                                val name = HtmlExporter.teamsLine(page)
                                ExportActions.printHtml(context, HtmlExporter.build(listOf(page), name), name)
                            })
                            DropdownMenuItem(text = { Text("Delete match page") }, onClick = {
                                menuOpen = false
                                confirmDelete = true
                            })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            LastEdited(page.editedAt)
            MatchDetailsCard(page, ::setField)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextBox("Score", page.field(Fields.SCORE), { setField(Fields.SCORE, it) }, Modifier.weight(1f), singleLine = true)
                TextBox("Fever pitch", page.field(Fields.FEVER_PITCH), { setField(Fields.FEVER_PITCH, it) }, Modifier.weight(1f), singleLine = true)
            }

            LabeledBox("Title", page.field(Fields.TITLE), { setField(Fields.TITLE, it) }, minLines = 1)
            LabeledBox("Pre-match context", page.field(Fields.PRE_MATCH), { setField(Fields.PRE_MATCH, it) }, minLines = 6)

            LineupsSection(page, home, away, ::setField)

            PartHeader("During the match")
            DiarySection(page) { change(it, null) }
            LabeledBox("Scorers", page.field(Fields.SCORERS), { setField(Fields.SCORERS, it) }, minLines = 2)
            EventsSection(page, ::setField)

            PartHeader("After the match")
            StatsTable(page, home, away, ::setField)
            LabeledBox("Player highlight — $home", page.field(Fields.HOME_HIGHLIGHT), { setField(Fields.HOME_HIGHLIGHT, it) }, minLines = 1)
            LabeledBox("Player highlight — $away", page.field(Fields.AWAY_HIGHLIGHT), { setField(Fields.AWAY_HIGHLIGHT, it) }, minLines = 1)
            LabeledBox("Man of the match", page.field(Fields.MOTM), { setField(Fields.MOTM, it) }, minLines = 1)
            LabeledBox("Moment of the match", page.field(Fields.MOMENT), { setField(Fields.MOMENT, it) }, minLines = 2)
            LabeledBox("Post-match verdict", page.field(Fields.VERDICT), { setField(Fields.VERDICT, it) }, minLines = 6)
            LabeledBox("The narrative", page.field(Fields.NARRATIVE), { setField(Fields.NARRATIVE, it) }, minLines = 6)
            LabeledBox("Media headline", page.field(Fields.MEDIA_HEADLINE), { setField(Fields.MEDIA_HEADLINE, it) }, minLines = 1)
            LabeledBox("What happened elsewhere", page.field(Fields.ELSEWHERE), { setField(Fields.ELSEWHERE, it) }, minLines = 3)
            LabeledBox("Next opponent", page.field(Fields.NEXT_OPPONENT), { setField(Fields.NEXT_OPPONENT, it) }, minLines = 2)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                val scores = listOf("") + (1..10).map { it.toString() }
                DropdownField("Match rating", page.field(Fields.RATING), scores, { change(page.copy(fields = page.fields + (Fields.RATING to it)), null) },
                    Modifier.weight(1f), display = { if (it.isBlank()) "–" else "$it / 10" })
                DropdownField("Match attention", page.field(Fields.ATTENTION), scores, { change(page.copy(fields = page.fields + (Fields.ATTENTION to it)), null) },
                    Modifier.weight(1f), display = { if (it.isBlank()) "–" else "$it / 10" })
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this match page?") },
            text = { Text("Everything you've written for ${HtmlExporter.teamsLine(page)} will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    repo.deletePage(page.id)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Match details collapse to one summary line once filled; tap to open and edit. */
@Composable
private fun MatchDetailsCard(page: MatchPage, setField: (String, String) -> Unit) {
    val summary = listOf(Fields.COMPETITION, Fields.MATCHDAY, Fields.DATE, Fields.VENUE)
        .map { page.field(it) }.filter { it.isNotBlank() }.joinToString(" · ")
    var open by remember { mutableStateOf(summary.isBlank()) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 3.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .clickable { open = !open }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (open) "Match details" else summary.ifBlank { "Match details" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!open) {
                val kickoff = page.field(Fields.KICKOFF)
                if (kickoff.isNotBlank()) Text(kickoff, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (open) "Hide match details" else "Show match details",
        )
    }
    if (!open) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextBox("Competition", page.field(Fields.COMPETITION), { setField(Fields.COMPETITION, it) }, Modifier.weight(1f), singleLine = true)
        TextBox("Matchday", page.field(Fields.MATCHDAY), { setField(Fields.MATCHDAY, it) }, Modifier.weight(1f), singleLine = true)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextBox("Date", page.field(Fields.DATE), { setField(Fields.DATE, it) }, Modifier.weight(1f), minLines = 1)
        TextBox("Kickoff", page.field(Fields.KICKOFF), { setField(Fields.KICKOFF, it) }, Modifier.weight(1f), minLines = 1)
    }
    TextBox("Venue", page.field(Fields.VENUE), { setField(Fields.VENUE, it) }, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextBox("Home team", page.field(Fields.HOME), { setField(Fields.HOME, it) }, Modifier.weight(1f), singleLine = true)
        TextBox("Away team", page.field(Fields.AWAY), { setField(Fields.AWAY, it) }, Modifier.weight(1f), singleLine = true)
    }
}

/** Possession, xG, shots, shots on target, corners — one editable box per team. */
@Composable
private fun StatsTable(page: MatchPage, home: String, away: String, setField: (String, String) -> Unit) {
    Text("Match stats", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1.1f))
        Text(home, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
        Text(away, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
    }
    Stats.ALL.forEach { stat ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            Text(stat.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.1f))
            CompactField(page.field(Fields.stat(stat.key, true)), { setField(Fields.stat(stat.key, true), it) }, Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(6.dp))
            CompactField(page.field(Fields.stat(stat.key, false)), { setField(Fields.stat(stat.key, false), it) }, Modifier.weight(1f), singleLine = true)
        }
    }
}

@Composable
private fun LineupsSection(page: MatchPage, home: String, away: String, setField: (String, String) -> Unit) {
    val hasAny = listOf(Fields.HOME_XI, Fields.AWAY_XI, Fields.HOME_SUBS, Fields.AWAY_SUBS, Fields.HOME_FORMATION, Fields.AWAY_FORMATION)
        .any { page.field(it).isNotBlank() }
    var open by remember { mutableStateOf(hasAny) }
    CollapsibleHeader("Lineups", open, { open = !open }, hint = if (hasAny) "Tap to show lineups" else "Tap to add lineups")
    if (!open) return
    Text(home, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    TextBox("Formation", page.field(Fields.HOME_FORMATION), { setField(Fields.HOME_FORMATION, it) }, singleLine = true)
    TextBox("Starting XI", page.field(Fields.HOME_XI), { setField(Fields.HOME_XI, it) }, minLines = 4)
    TextBox("Subs", page.field(Fields.HOME_SUBS), { setField(Fields.HOME_SUBS, it) }, minLines = 3)
    Text(away, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    TextBox("Formation", page.field(Fields.AWAY_FORMATION), { setField(Fields.AWAY_FORMATION, it) }, singleLine = true)
    TextBox("Starting XI", page.field(Fields.AWAY_XI), { setField(Fields.AWAY_XI, it) }, minLines = 4)
    TextBox("Subs", page.field(Fields.AWAY_SUBS), { setField(Fields.AWAY_SUBS, it) }, minLines = 3)
}

@Composable
private fun EventsSection(page: MatchPage, setField: (String, String) -> Unit) {
    val hasContent = page.field(Fields.EVENTS).isNotBlank()
    var open by remember { mutableStateOf(hasContent) }
    // Open automatically the first time events arrive (e.g. after a refresh).
    LaunchedEffect(hasContent) { if (hasContent) open = true }
    CollapsibleHeader("Match events", open, { open = !open }, hint = if (hasContent) "Tap to show goals, cards and subs" else "Tap to add match events")
    if (!open) return
    TextBox("Goals, cards, subs", page.field(Fields.EVENTS), { setField(Fields.EVENTS, it) }, minLines = 4)
}

private data class ClockDialogSpec(val phase: Phase, val minute: String)

@Composable
private fun DiarySection(page: MatchPage, change: (MatchPage) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(page.clock) {
        now = System.currentTimeMillis()
        while (MatchClock.isRunning(page.clock)) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    var clockDialog by remember { mutableStateOf<ClockDialogSpec?>(null) }
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var adding by remember { mutableStateOf<String?>(null) } // minute pre-filled for a new entry

    val clock = page.clock
    Text("Match diary", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .clickable {
                        val current = MatchClock.rawMinute(clock, System.currentTimeMillis())
                        val phase = if (clock.phase == Phase.NONE) Phase.H1 else clock.phase
                        clockDialog = ClockDialogSpec(phase, (current ?: MatchClock.defaultStart(phase)).toString())
                    }
                    .padding(end = 12.dp),
            ) {
                Text(
                    MatchClock.display(clock, now).let { if (it == "–" || it == "FT") it else "$it'" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(MatchClock.phaseName(clock.phase), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (clock.phase) {
                    Phase.NONE -> FilledTonalButton(onClick = { clockDialog = ClockDialogSpec(Phase.H1, "1") }) { Text("Kick off") }
                    Phase.H1 -> FilledTonalButton(onClick = { clockDialog = ClockDialogSpec(Phase.H2, "46") }) { Text("2nd half") }
                    Phase.H2 -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { clockDialog = ClockDialogSpec(Phase.ET1, "91") }) { Text("Extra time") }
                        FilledTonalButton(onClick = { change(page.copy(clock = MatchClock.anchor(Phase.FT, 0, System.currentTimeMillis()))) }) { Text("Full time") }
                    }
                    Phase.ET1 -> FilledTonalButton(onClick = { clockDialog = ClockDialogSpec(Phase.ET2, "106") }) { Text("ET 2nd half") }
                    Phase.ET2 -> FilledTonalButton(onClick = { change(page.copy(clock = MatchClock.anchor(Phase.FT, 0, System.currentTimeMillis()))) }) { Text("Full time") }
                    Phase.FT -> TextButton(onClick = { clockDialog = ClockDialogSpec(Phase.H2, "90") }) { Text("Adjust clock") }
                }
            }
        }
    }
    Text(
        "Tap the minute to set it — e.g. if you started watching late.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 2.dp),
    )

    MatchClock.sorted(page.diary).forEach { entry ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { editing = entry }
                .padding(vertical = 6.dp),
        ) {
            Text(
                MatchClock.label(entry.minute),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(60.dp),
            )
            Text(entry.text.ifBlank { "(empty)" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    Button(
        onClick = { adding = MatchClock.stamp(page.clock, System.currentTimeMillis()) },
        modifier = Modifier.padding(top = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Entry")
    }

    clockDialog?.let { spec ->
        ClockDialog(
            spec = spec,
            onDismiss = { clockDialog = null },
            onSet = { phase, minute ->
                clockDialog = null
                change(page.copy(clock = MatchClock.anchor(phase, minute, System.currentTimeMillis()), updatedAt = System.currentTimeMillis()))
            },
        )
    }

    adding?.let { minute ->
        EntryDialog(
            title = "New entry",
            initialMinute = minute,
            initialText = "",
            onDismiss = { adding = null },
            onSave = { m, t ->
                adding = null
                if (t.isNotBlank() || m.isNotBlank()) {
                    val now2 = System.currentTimeMillis()
                    change(page.copy(diary = page.diary + DiaryEntry(UUID.randomUUID().toString(), m.trim(), t.trim(), now2), updatedAt = now2))
                }
            },
            onDelete = null,
        )
    }

    editing?.let { entry ->
        EntryDialog(
            title = "Edit entry",
            initialMinute = entry.minute,
            initialText = entry.text,
            onDismiss = { editing = null },
            onSave = { m, t ->
                editing = null
                change(page.copy(
                    diary = page.diary.map { if (it.id == entry.id) it.copy(minute = m.trim(), text = t.trim()) else it },
                    updatedAt = System.currentTimeMillis(),
                ))
            },
            onDelete = {
                editing = null
                change(page.copy(diary = page.diary.filterNot { it.id == entry.id }, updatedAt = System.currentTimeMillis()))
            },
        )
    }
}

@Composable
private fun ClockDialog(spec: ClockDialogSpec, onDismiss: () -> Unit, onSet: (Phase, Int) -> Unit) {
    var phase by remember { mutableStateOf(spec.phase) }
    var minuteText by remember { mutableStateOf(spec.minute) }
    val parsed = MatchClock.parseMinute(minuteText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set the match clock") },
        text = {
            Column {
                listOf(Phase.H1, Phase.H2, Phase.ET1, Phase.ET2).forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (phase != p) minuteText = MatchClock.defaultStart(p).toString()
                                phase = p
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = phase == p, onClick = {
                            if (phase != p) minuteText = MatchClock.defaultStart(p).toString()
                            phase = p
                        })
                        Text(MatchClock.phaseName(p))
                    }
                }
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { minuteText = it },
                    label = { Text("Minute right now") },
                    supportingText = { Text("e.g. 7, or 45+2") },
                    singleLine = true,
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let { onSet(phase, it) } }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EntryDialog(
    title: String,
    initialMinute: String,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var minute by remember { mutableStateOf(initialMinute) }
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = minute,
                    onValueChange = { minute = it },
                    label = { Text("Minute") },
                    singleLine = true,
                    modifier = Modifier.width(140.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("What happened") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(minute, text) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
