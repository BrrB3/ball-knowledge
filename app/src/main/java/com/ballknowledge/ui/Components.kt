package com.ballknowledge.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        minLines = if (singleLine) 1 else minLines,
        singleLine = singleLine,
        enabled = enabled,
        colors = boxColors(),
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            heading,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            colors = boxColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/** A big section divider: "During the match", "After the match". */
@Composable
fun PartHeader(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    }
}

/** A tappable heading with a show/hide arrow. */
@Composable
fun CollapsibleHeader(title: String, open: Boolean, onToggle: () -> Unit, hint: String? = null) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
        modifier = modifier.padding(top = 18.dp, bottom = 4.dp),
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
