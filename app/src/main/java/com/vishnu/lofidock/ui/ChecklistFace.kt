package com.vishnu.lofidock.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vishnu.lofidock.core.Checklist
import com.vishnu.lofidock.core.ChecklistItem
import com.vishnu.lofidock.core.TimeFormatting

/**
 * A study checklist beside the clock: add tasks at the top, tap one to tick it
 * off, long-press to delete. The add field sits at the top because the
 * landscape keyboard covers the bottom half of the screen.
 */
@Composable
fun ChecklistFace(
    now: Long,
    sessionSeconds: Double,
    widthDp: Float,
    scale: Float,
    checklist: Checklist,
    onAdd: (String) -> Unit,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearDone: () -> Unit,
) {
    val d = rememberDims(scale)
    Box(Modifier.fillMaxSize()) {
        LofiBackdrop()
        Row(
            Modifier.fillMaxSize().padding(start = d.dp(72f), end = d.dp(64f), top = d.dp(26f), bottom = d.dp(26f)),
            horizontalArrangement = Arrangement.spacedBy(d.dp(36f)),
        ) {
            Column(Modifier.width((widthDp * 0.52f).dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(d.dp(10f))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CHECKLIST", style = lofiText(d.sp(12f), FontWeight.SemiBold, alpha = 0.6f, tracking = d.sp(2.5f)))
                    Spacer(Modifier.weight(1f))
                    if (checklist.doneCount > 0) {
                        Text(
                            "Clear done",
                            Modifier.combinedClickable(onClick = onClearDone).padding(4.dp),
                            style = lofiText(d.sp(13f), FontWeight.SemiBold, color = Palette.lamp, alpha = 0.9f),
                        )
                    }
                }
                AddTaskField(onAdd, d)
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (checklist.items.isEmpty()) {
                        item {
                            Text(
                                "Nothing here yet. Add what you're working on.",
                                Modifier.padding(top = d.dp(10f), start = d.dp(6f)),
                                style = lofiText(d.sp(15f), FontWeight.Medium, alpha = 0.45f),
                            )
                        }
                    }
                    items(checklist.items, key = { it.id }) { item ->
                        ChecklistRow(item, d, onToggle = { onToggle(item.id) }, onRemove = { onRemove(item.id) })
                    }
                }
            }
            Summary(now, sessionSeconds, checklist, d, Modifier.weight(1f))
        }
    }
}

/**
 * Uses the TextFieldState API: the field owns its text, so keystrokes that
 * arrive faster than recomposition (fast typing, autocorrect, a slow phone)
 * can't be dropped. The older value/onValueChange form lost characters that
 * way in testing.
 */
@Composable
private fun AddTaskField(onAdd: (String) -> Unit, d: Dims) {
    val draft = rememberTextFieldState()
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().height(d.dp(44f))
            .background(Palette.cream.copy(alpha = if (focused) 0.12f else 0.07f), RoundedCornerShape(d.dp(12f)))
            .padding(horizontal = d.dp(14f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.dp(10f)),
    ) {
        Icon(Icons.Filled.AddCircle, null, Modifier.size(d.dp(20f)), tint = Palette.lamp)
        BasicTextField(
            state = draft,
            modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { focused = it.isFocused },
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = lofiText(d.sp(17f), FontWeight.Medium, glow = Color.Transparent, glowRadius = 0f),
            cursorBrush = SolidColor(Palette.lamp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            // Adds the task and keeps the keyboard up for the next one; Done on an
            // empty field puts the keyboard away.
            onKeyboardAction = {
                val text = draft.text.toString()
                if (text.isBlank()) {
                    focusManager.clearFocus()
                } else {
                    onAdd(text)
                    draft.clearText()
                    focus.requestFocus()
                }
            },
            decorator = { field ->
                if (draft.text.isEmpty()) {
                    Text("Add a task", style = lofiText(d.sp(17f), FontWeight.Medium, alpha = 0.4f, glow = Color.Transparent, glowRadius = 0f))
                }
                field()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistRow(item: ChecklistItem, d: Dims, onToggle: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onToggle, onLongClick = { menu = true })
                .padding(vertical = d.dp(8f), horizontal = d.dp(6f))
                .semantics {
                    contentDescription = item.title
                    stateDescription = if (item.isDone) "Done" else "Not done"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.dp(12f)),
        ) {
            Icon(
                if (item.isDone) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                Modifier.size(d.dp(22f)),
                tint = if (item.isDone) Palette.lamp else Palette.cream.copy(alpha = 0.55f),
            )
            Text(
                item.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = lofiText(
                    d.sp(17f), FontWeight.Medium, alpha = if (item.isDone) 0.45f else 0.95f,
                    glow = Color.Transparent, glowRadius = 0f,
                ).copy(textDecoration = if (item.isDone) TextDecoration.LineThrough else null),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                onClick = {
                    menu = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun Summary(now: Long, sessionSeconds: Double, list: Checklist, d: Dims, modifier: Modifier) {
    val clock = clockOf(now)
    val total = list.items.size
    val progress by animateFloatAsState(list.progress.toFloat(), tween(400), label = "progress")
    Column(modifier.padding(top = d.dp(14f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(d.dp(12f))) {
        Row {
            Text(clock.time, Modifier.alignByBaseline(), style = lofiText(d.sp(46f), FontWeight.Light, glowRadius = 12f))
            clock.period?.let {
                Spacer(Modifier.width(d.dp(5f)))
                Text(it, Modifier.alignByBaseline(), style = lofiText(d.sp(16f), FontWeight.Medium, alpha = 0.6f))
            }
        }
        Text(dateLineOf(now), style = lofiText(d.sp(14f), FontWeight.Medium, alpha = 0.6f))
        Box(
            Modifier.padding(vertical = d.dp(6f)).size(d.dp(92f))
                .semantics { contentDescription = "${list.doneCount} of $total tasks done" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx() * d.scale
                inset(stroke / 2) {
                    drawArc(Palette.cream.copy(alpha = 0.1f), 0f, 360f, false, style = Stroke(stroke))
                    drawArc(Palette.lamp, -90f, 360f * progress, false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${list.doneCount}/$total", style = lofiText(d.sp(22f), FontWeight.Medium))
                Text(
                    if (total > 0 && list.doneCount == total) "all done" else "done",
                    style = lofiText(d.sp(11f), FontWeight.SemiBold, alpha = 0.55f),
                )
            }
        }
        Row {
            Text("SESSION", Modifier.alignByBaseline(), style = lofiText(d.sp(11f), FontWeight.SemiBold, alpha = 0.55f, tracking = d.sp(2.5f)))
            Spacer(Modifier.width(d.dp(8f)))
            Text(TimeFormatting.session(sessionSeconds), Modifier.alignByBaseline(), style = lofiText(d.sp(18f), FontWeight.Normal))
        }
    }
}
