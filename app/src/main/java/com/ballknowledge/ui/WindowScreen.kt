@file:OptIn(ExperimentalMaterial3Api::class)

package com.ballknowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ballknowledge.export.PlainTextExporter
import com.ballknowledge.logic.TimeFormat
import com.ballknowledge.logic.TransferMath
import com.ballknowledge.logic.UndoHistory
import com.ballknowledge.model.Currency
import com.ballknowledge.model.Fee
import com.ballknowledge.model.InStatus
import com.ballknowledge.model.IncomingLink
import com.ballknowledge.model.OutStatus
import com.ballknowledge.model.OutgoingLink
import com.ballknowledge.model.SpendKeys
import com.ballknowledge.model.TransferWindow
import com.ballknowledge.model.WindowKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Composable
fun WindowScreen(repo: Repository, windowId: String, onBack: () -> Unit) {
    val initial = remember(windowId) { repo.window(windowId) }
    if (initial == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var w by remember(windowId) { mutableStateOf(initial) }
    val history = remember(windowId) { UndoHistory<TransferWindow>() }
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf<String?>(null) } // "start" or "end"
    var rateStatus by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    fun show(n: TransferWindow) {
        w = n
        repo.updateWindow(n)
    }

    fun change(n: TransferWindow, key: String?) {
        if (n == w) return
        history.record(w, key, System.currentTimeMillis())
        show(n.copy(updatedAt = System.currentTimeMillis()))
    }

    // Fetch the ECB rate for the start date once it's set and reached (never over a typed rate).
    LaunchedEffect(w.startDate, w.rate, w.rateEdited) {
        if (repo.wantsRate(w)) {
            rateStatus = "Fetching the ECB rate…"
            val forStart = w.startDate
            val r = repo.fetchRate(w)
            if (r != null && w.startDate == forStart && repo.wantsRate(w)) {
                show(w.copy(rate = r.poundsPerEuro, rateDate = r.date))
                rateStatus = null
            } else if (r == null) {
                rateStatus = "Couldn't fetch the rate right now. It will retry next time, or type it in."
            }
        } else {
            rateStatus = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = { Text(w.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = { history.undo(w)?.let { show(it) } }, enabled = history.canUndo) {
                        Icon(AppIcons.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { history.redo(w)?.let { show(it) } }, enabled = history.canRedo) {
                        Icon(AppIcons.Redo, contentDescription = "Redo")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Share as text") }, onClick = {
                                menuOpen = false
                                ExportActions.shareText(context, PlainTextExporter.window(w), w.title)
                            })
                            DropdownMenuItem(text = { Text("Share as HTML") }, onClick = {
                                menuOpen = false
                                repo.flush()
                                ExportActions.shareHtml(context, HtmlExporter.buildTransfers(listOf(w), w.title), w.title)
                            })
                            DropdownMenuItem(text = { Text("Save as PDF") }, onClick = {
                                menuOpen = false
                                ExportActions.printHtml(context, HtmlExporter.buildTransfers(listOf(w), w.title), w.title)
                            })
                            DropdownMenuItem(text = { Text("Delete window") }, onClick = {
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
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ---------- Window details ----------
            SectionTitle("Window")
            TextBox("Team", w.team, { change(w.copy(team = it), "team") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WindowKind.values().forEach { k ->
                    FilterChip(selected = w.kind == k, onClick = { change(w.copy(kind = k), null) }, label = { Text(k.label) })
                }
                // Local text so half-typed years ("202") don't fight the field; saved once valid.
                var yearText by remember(w.year) { mutableStateOf(w.year.toString()) }
                TextBox(
                    "Year", yearText,
                    { t ->
                        yearText = t
                        t.trim().toIntOrNull()?.takeIf { it in 1900..2200 }?.let { change(w.copy(year = it), "year") }
                    },
                    Modifier.weight(1f), singleLine = true, numeric = true,
                )
            }
            Text(
                "Season ${TimeFormat.seasonLabel(w.seasonStart)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                DateButton("Opens", w.startDate, Modifier.weight(1f)) { pickDate = "start" }
                DateButton("Closes", w.endDate, Modifier.weight(1f)) { pickDate = "end" }
            }
            TextBox(
                "Exchange rate: €1 = £",
                w.rate,
                { t ->
                    // Clearing the box hands the rate back to the automatic ECB lookup.
                    change(if (t.isBlank()) w.copy(rate = "", rateEdited = false, rateDate = "") else w.copy(rate = t, rateEdited = true), "rate")
                },
                singleLine = true,
                numeric = true,
            )
            Text(
                rateStatus ?: when {
                    w.rateEdited -> "Typed by you. Clear it to use the ECB rate for the start date."
                    w.rate.isNotBlank() && w.rateDate.isNotBlank() -> "ECB rate for ${TimeFormat.isoToDisplay(w.rateDate)} — locked in."
                    w.startDate.isBlank() -> "Set the opening date and the ECB rate for that day is fetched automatically."
                    else -> "The ECB rate is fetched automatically once the window opens."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(TransferMath.summary(w), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }

            LabeledBox("Pre-transfer wishlist", w.wishlist, { change(w.copy(wishlist = it), "wishlist") }, minLines = 9)

            // ---------- Incoming ----------
            PartHeader("Incoming")
            w.incoming.sortedWith(compareBy<IncomingLink> { it.status.ordinal }.thenBy { it.createdAt }).forEach { link ->
                IncomingCard(
                    link = link,
                    expanded = link.id in expanded,
                    onToggle = { expanded = if (link.id in expanded) expanded - link.id else expanded + link.id },
                    onChange = { updated, key -> change(w.copy(incoming = w.incoming.map { if (it.id == link.id) updated else it }), key) },
                    onDelete = { change(w.copy(incoming = w.incoming.filterNot { it.id == link.id }), null) },
                )
            }
            FilledTonalButton(onClick = {
                val id = UUID.randomUUID().toString()
                expanded = expanded + id
                change(w.copy(incoming = w.incoming + IncomingLink(id = id, createdAt = System.currentTimeMillis())), null)
            }, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add incoming player")
            }

            // ---------- Exits ----------
            PartHeader("Exits")
            w.outgoing.sortedWith(compareBy<OutgoingLink> { it.status.ordinal }.thenBy { it.createdAt }).forEach { link ->
                OutgoingCard(
                    link = link,
                    expanded = link.id in expanded,
                    onToggle = { expanded = if (link.id in expanded) expanded - link.id else expanded + link.id },
                    onChange = { updated, key -> change(w.copy(outgoing = w.outgoing.map { if (it.id == link.id) updated else it }), key) },
                    onDelete = { change(w.copy(outgoing = w.outgoing.filterNot { it.id == link.id }), null) },
                )
            }
            FilledTonalButton(onClick = {
                val id = UUID.randomUUID().toString()
                expanded = expanded + id
                change(w.copy(outgoing = w.outgoing + OutgoingLink(id = id, createdAt = System.currentTimeMillis())), null)
            }, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add exit")
            }

            // ---------- Spend ----------
            PartHeader("Spend")
            val totals = TransferMath.totals(w)
            val auto = mapOf(
                SpendKeys.INCOMING to TransferMath.format(totals.incoming),
                SpendKeys.OUTGOING to TransferMath.format(totals.outgoing),
                SpendKeys.NET to TransferMath.format(totals.net),
            )
            if (totals.missingRate) {
                Text(
                    "Some fees are in euros but there's no exchange rate yet, so they aren't counted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                "Main fees and add-ons are totalled separately. Signed and loan fees count as incoming; sold and loaned-out fees as outgoing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf("Incoming spend" to SpendKeys.INCOMING, "Outgoing (sales)" to SpendKeys.OUTGOING, "Net spend" to SpendKeys.NET).forEach { (label, key) ->
                val override = w.spendOverrides[key]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextBox(
                        label,
                        override ?: auto.getValue(key),
                        { t -> change(w.copy(spendOverrides = w.spendOverrides + (key to t)), "spend-$key") },
                        Modifier.weight(1f),
                        singleLine = true,
                    )
                    if (override != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 6.dp)) {
                            Text("edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            TextButton(onClick = { change(w.copy(spendOverrides = w.spendOverrides - key), null) }) { Text("Auto") }
                        }
                    }
                }
            }

            LabeledBox("Window verdict", w.verdict, { change(w.copy(verdict = it), "verdict") }, minLines = 9)
            Spacer(Modifier.height(48.dp))
        }
    }

    pickDate?.let { which ->
        val current = if (which == "start") w.startDate else w.endDate
        val initialMillis = runCatching { LocalDate.parse(current).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton(onClick = {
                    val iso = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() } ?: ""
                    pickDate = null
                    if (which == "start") {
                        // A new start date means a new automatic rate (unless the rate was typed).
                        val reset = if (!w.rateEdited && iso != w.startDate) w.copy(rate = "", rateDate = "") else w
                        change(reset.copy(startDate = iso), null)
                    } else {
                        change(w.copy(endDate = iso), null)
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pickDate = null
                        if (which == "start") change(w.copy(startDate = ""), null) else change(w.copy(endDate = ""), null)
                    }) { Text("Clear") }
                    TextButton(onClick = { pickDate = null }) { Text("Cancel") }
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this window?") },
            text = { Text("Everything in ${w.title} will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    repo.deleteWindow(w.id)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DateButton(label: String, iso: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(
            "$label: " + TimeFormat.isoToDisplay(iso).ifBlank { "set date" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayerCardFrame(
    name: String,
    position: String,
    statusLabel: String,
    fee: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    body: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "New player" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (position.isNotBlank()) Text(position, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (fee.isNotBlank()) {
                Text(fee, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
            }
            StatusPill(statusLabel, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        }
        if (expanded) body()
    }
}

@Composable
private fun FeeRow(fee: Fee, enabled: Boolean, onChange: (Fee, String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextBox("Fee (m)", fee.main, { onChange(fee.copy(main = it), "fee-main") }, Modifier.weight(1f), singleLine = true, enabled = enabled, numeric = true)
        TextBox("Add-ons (m)", fee.addons, { onChange(fee.copy(addons = it), "fee-addons") }, Modifier.weight(1f), singleLine = true, enabled = enabled, numeric = true)
        Column {
            Currency.values().forEach { c ->
                FilterChip(
                    selected = fee.currency == c,
                    onClick = { onChange(fee.copy(currency = c), "fee-currency") },
                    label = { Text(c.symbol) },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun IncomingCard(
    link: IncomingLink,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (IncomingLink, String) -> Unit,
    onDelete: () -> Unit,
) {
    val fee = if (link.status.hasPrice) TransferMath.formatFee(link.fee) else ""
    PlayerCardFrame(link.name, link.position, link.status.short, fee, expanded, onToggle) {
        val k = "in-${link.id}"
        TextBox("Player", link.name, { onChange(link.copy(name = it), "$k-name") }, singleLine = true)
        TextBox("Position", link.position, { onChange(link.copy(position = it), "$k-pos") }, singleLine = true)
        TextBox("First linked (e.g. early June)", link.firstLinked, { onChange(link.copy(firstLinked = it), "$k-first") }, singleLine = true)
        TextBox("Sources", link.sources, { onChange(link.copy(sources = it), "$k-src") }, minLines = 1)
        TextBox("Also linked with", link.alsoLinked, { onChange(link.copy(alsoLinked = it), "$k-also") }, minLines = 1)
        Text("Status", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InStatus.values().forEach { s ->
                FilterChip(selected = link.status == s, onClick = { onChange(link.copy(status = s), "$k-status-${s.name}") }, label = { Text(s.label) })
            }
        }
        FeeRow(link.fee, link.status.hasPrice) { f, key -> onChange(link.copy(fee = f), "$k-$key") }
        TextBox("Note", link.note, { onChange(link.copy(note = it), "$k-note") }, minLines = 2)
        TextButton(onClick = onDelete) { Text("Remove player", color = MaterialTheme.colorScheme.tertiary) }
    }
}

@Composable
private fun OutgoingCard(
    link: OutgoingLink,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (OutgoingLink, String) -> Unit,
    onDelete: () -> Unit,
) {
    val fee = if (link.status.hasPrice) TransferMath.formatFee(link.fee) else ""
    PlayerCardFrame(link.name, link.position, link.status.short, fee, expanded, onToggle) {
        val k = "out-${link.id}"
        TextBox("Player", link.name, { onChange(link.copy(name = it), "$k-name") }, singleLine = true)
        TextBox("Position", link.position, { onChange(link.copy(position = it), "$k-pos") }, singleLine = true)
        TextBox("Linked clubs", link.linkedClubs, { onChange(link.copy(linkedClubs = it), "$k-clubs") }, minLines = 1)
        Text("Status", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutStatus.values().forEach { s ->
                FilterChip(selected = link.status == s, onClick = { onChange(link.copy(status = s), "$k-status-${s.name}") }, label = { Text(s.label) })
            }
        }
        FeeRow(link.fee, link.status.hasPrice) { f, key -> onChange(link.copy(fee = f), "$k-$key") }
        TextBox("Note", link.note, { onChange(link.copy(note = it), "$k-note") }, minLines = 2)
        TextButton(onClick = onDelete) { Text("Remove player", color = MaterialTheme.colorScheme.tertiary) }
    }
}
