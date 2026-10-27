@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ballknowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballknowledge.logic.TimeFormat

@Composable
fun boxColors(): TextFieldColors {
    val c = boxColor()
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c,
        unfocusedContainerColor = c,
        disabledContainerColor = c.copy(alpha = 0.5f),
        errorContainerColor = c,
    )
}

/**
 * A slimmer text box than Material's default (about 20% shorter), with the same look.
 * Every text box in the app is built on this.
 */
@Composable
fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    minLines: Int = 1,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    numeric: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = boxColors()
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        interactionSource = interaction,
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = inner,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interaction,
                label = label?.let { l -> { Text(l, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                trailingIcon = trailing,
                colors = colors,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interaction,
                        colors = colors,
                        shape = RoundedCornerShape(8.dp),
                    )
                },
            )
        },
    )
}

/** A short field with its label inside the box (match details, card fields). */
@Composable
fun TextBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 2,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    numeric: Boolean = false,
) {
    CompactField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.padding(vertical = 3.dp),
        label = label,
        minLines = minLines,
        singleLine = singleLine,
        enabled = enabled,
        numeric = numeric,
    )
}

/** A writing box with a visible heading above it. Boxes grow as you type. */
@Composable
fun LabeledBox(
    heading: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 3,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        CompactField(value = value, onValueChange = onValueChange, minLines = minLines)
    }
}

/** A read-only box that opens a list to pick from (ratings, seasons). */
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    display: (String) -> String = { it },
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.padding(vertical = 3.dp)) {
        CompactField(
            value = display(value),
            onValueChange = {},
            label = label,
            readOnly = true,
            singleLine = true,
            trailing = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(display(o)) }, onClick = { open = false; onSelect(o) })
            }
        }
    }
}

/** "Last edited Tue 27 Oct 2026, 10:56 pm". */
@Composable
fun LastEdited(millis: Long, modifier: Modifier = Modifier) {
    if (millis <= 0L) return
    Text(
        "Last edited " + TimeFormat.editedLine(millis),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp),
    )
}

/** The app name, lightly styled: a serif wordmark in the app's green. */
@Composable
fun AppTitle() {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)) { append("Ball") }
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurface)) {
                append(" Knowledge")
            }
        },
        fontFamily = FontFamily.Serif,
        fontSize = 23.sp,
        letterSpacing = 0.2.sp,
    )
}

/** A big section divider: "During the match", "After the match". */
@Composable
fun PartHeader(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 2.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    }
}

/** A tappable heading with a show/hide arrow. */
@Composable
fun CollapsibleHeader(title: String, open: Boolean, onToggle: () -> Unit, hint: String? = null) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(top = 8.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Hide $title" else "Show $title",
            )
        }
        if (!open && hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 1–10 picker in two rows; tapping the selected number clears it. */
@Composable
fun ScorePicker(value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(1..5, 6..10).forEach { range ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                range.forEach { n ->
                    val selected = value == n.toString()
                    FilterChip(
                        selected = selected,
                        onClick = { onChange(if (selected) "" else n.toString()) },
                        label = { Text(n.toString(), modifier = Modifier.widthIn(min = 20.dp), maxLines = 1) },
                    )
                }
            }
        }
    }
}

/** "Sat 31 Oct · 8:30 pm IST (3:00 pm UK*)" with the star as a superscript. */
fun kickoffText(k: TimeFormat.Kickoff, includeDate: Boolean = true): AnnotatedString = buildAnnotatedString {
    if (includeDate) append("${k.date} · ")
    append("${k.ist} IST (${k.uk} UK")
    if (k.star) {
        withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 9.sp, fontWeight = FontWeight.Bold)) {
            append("*")
        }
    }
    append(")")
}

@Composable
fun CompBadge(label: String, modifier: Modifier = Modifier) {
    if (label.isBlank()) return
    Box(
        modifier = modifier
            .widthIn(min = 40.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun StatusPill(text: String, background: Color, foreground: Color) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = foreground)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

/** Small icons not in the core icon set, drawn from Material's own path data. */
object AppIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    val Undo: ImageVector by lazy {
        icon("Undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z")
    }
    val Redo: ImageVector by lazy {
        icon("Redo", "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z")
    }
    val Transfers: ImageVector by lazy {
        icon("Transfers", "M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z")
    }
}
