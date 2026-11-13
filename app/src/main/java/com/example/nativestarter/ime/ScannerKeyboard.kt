package com.example.nativestarter.ime

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val BarBg = Color(0xFF0D141A)
private val KeyBg = Color(0xFF1C262F)
private val KeyBgAlt = Color(0xFF141C24)
private val KeyBgPressed = Color(0xFF2A3742)
private val AccentGreen = Color(0xFF00E5A0)
private val MutedText = Color(0xFF6F8494)
private val WarnAmber = Color(0xFFFFB300)
private val DangerRed = Color(0xFFFF5252)

private val KeyH = 42.dp
private val Gap = 5.dp
private val SWIPE_THRESHOLD_DP = 42

private const val REPEAT_START_MS = 420L
private const val REPEAT_INTERVAL_MS = 55L
private const val LONG_PRESS_MS = 350L

/** Which auxiliary panel is open above the character board. */
enum class BoardPanel { NONE, CLIPBOARD, EMOJI }

/**
 * The standalone scanner keyboard.
 *
 * Layers, top to bottom:
 *  1. status strip (scan state + last capture)
 *  2. action row (scan / auto-enter / edit keys)
 *  3. suggestion strip (predictions and corrections)
 *  4. mode tabs (NUM / ABC / SYM) plus clipboard and emoji toggles
 *  5. character board, or the open auxiliary panel
 *
 * Every key reports a press so the service can fire haptics, and every key can
 * carry long-press alternates supplied by [KeyLayout].
 */
@Composable
fun ScannerKeyboard(
    scanning: Boolean,
    status: String,
    lastValue: String?,
    autoEnter: Boolean,
    mode: InputMode,
    shift: Boolean,
    capsLock: Boolean,
    suggestions: List<String>,
    clipboardItems: List<String>,
    recentEmoji: List<String>,
    panel: BoardPanel,
    onScan: () -> Unit,
    onToggleAutoEnter: () -> Unit,
    onKey: (String) -> Unit,
    onLongPressKey: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    onSuggestionDismiss: () -> Unit,
    onModeChange: (InputMode) -> Unit,
    onPanelToggle: (BoardPanel) -> Unit,
    onClipboardPick: (String) -> Unit,
    onClipboardDelete: (String) -> Unit,
    onClipboardClear: () -> Unit,
    onEmojiPick: (String) -> Unit,
    onShift: () -> Unit,
    onBackspace: () -> Unit,
    onBackspaceHeld: () -> Unit,
    onDeleteWord: () -> Unit,
    onClear: () -> Unit,
    onEnter: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onHide: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BarBg)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        StatusBarRow(scanning = scanning, status = status, lastValue = lastValue)
        Spacer(Modifier.height(Gap))
        ActionRow(
            scanning = scanning,
            autoEnter = autoEnter,
            onScan = onScan,
            onToggleAutoEnter = onToggleAutoEnter,
            onClear = onClear,
            onEnter = onEnter,
            onHide = onHide,
            onSwitchKeyboard = onSwitchKeyboard
        )

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(Gap))
            SuggestionStrip(
                suggestions = suggestions,
                onPick = onSuggestion,
                onDismiss = onSuggestionDismiss
            )
        }

        Spacer(Modifier.height(Gap))
        ModeTabs(
            mode = mode,
            panel = panel,
            onModeChange = onModeChange,
            onPanelToggle = onPanelToggle
        )
        Spacer(Modifier.height(Gap))

        when (panel) {
            BoardPanel.CLIPBOARD -> ClipboardPanel(
                items = clipboardItems,
                onPick = onClipboardPick,
                onDelete = onClipboardDelete,
                onClear = onClipboardClear,
                onClose = { onPanelToggle(BoardPanel.CLIPBOARD) }
            )
            BoardPanel.EMOJI -> EmojiPanel(
                recent = recentEmoji,
                onPick = onEmojiPick,
                onBackspace = onBackspace,
                onClose = { onPanelToggle(BoardPanel.EMOJI) }
            )
            BoardPanel.NONE -> when (mode) {
                InputMode.NUM -> NumBoard(
                    onKey = onKey,
                    onLongPressKey = onLongPressKey,
                    onBackspace = onBackspace,
                    onBackspaceHeld = onBackspaceHeld
                )
                InputMode.ABC -> AbcBoard(
                    shift = shift,
                    capsLock = capsLock,
                    onKey = onKey,
                    onLongPressKey = onLongPressKey,
                    onShift = onShift,
                    onBackspace = onBackspace,
                    onBackspaceHeld = onBackspaceHeld,
                    onDeleteWord = onDeleteWord,
                    onMoveLeft = onMoveLeft,
                    onMoveRight = onMoveRight,
                    onEnter = onEnter
                )
                InputMode.SYM -> SymBoard(
                    onKey = onKey,
                    onLongPressKey = onLongPressKey,
                    onBackspace = onBackspace,
                    onBackspaceHeld = onBackspaceHeld
                )
            }
        }
    }
}

@Composable
private fun StatusBarRow(scanning: Boolean, status: String, lastValue: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(Color(0xFF12181F), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(if (scanning) WarnAmber else AccentGreen)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = status,
            color = if (scanning) WarnAmber else AccentGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = lastValue ?: "Ready",
            color = if (lastValue != null) Color.White else MutedText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionRow(
    scanning: Boolean,
    autoEnter: Boolean,
    onScan: () -> Unit,
    onToggleAutoEnter: () -> Unit,
    onClear: () -> Unit,
    onEnter: () -> Unit,
    onHide: () -> Unit,
    onSwitchKeyboard: () -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ButtonLike(
            label = if (scanning) "STOP" else "SCAN",
            modifier = Modifier.weight(2.4f),
            container = if (scanning) WarnAmber else AccentGreen,
            textColor = Color.Black,
            fontSize = 13.sp,
            bold = true,
            letterSpacing = 1.sp,
            hapticView = view,
            onClick = onScan
        )
        ButtonLike(
            label = if (autoEnter) "AUTO" else "MAN",
            modifier = Modifier.weight(1.3f),
            container = if (autoEnter) AccentGreen else KeyBg,
            textColor = if (autoEnter) Color.Black else MutedText,
            fontSize = 11.sp,
            bold = true,
            hapticView = view,
            onClick = onToggleAutoEnter
        )
        SmallKey("\u23CE", Modifier.weight(0.9f), view, onEnter)
        SmallKey("CLR", Modifier.weight(1f), view, onClear)
        SmallKey("HIDE", Modifier.weight(1.3f), view, onHide)
        SmallKey("\u2328", Modifier.weight(0.9f), view, onSwitchKeyboard)
    }
}

@Composable
private fun SuggestionStrip(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(0xFF12181F), RoundedCornerShape(7.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            suggestions.forEachIndexed { index, candidate ->
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index == 0) Color(0xFF183A2F) else KeyBg)
                        .clickable {
                            Haptics.tap(view)
                            onPick(candidate)
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = candidate,
                        color = if (index == 0) AccentGreen else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(34.dp, 34.dp)
                .clickable {
                    Haptics.tap(view)
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\u00D7", color = MutedText, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ModeTabs(
    mode: InputMode,
    panel: BoardPanel,
    onModeChange: (InputMode) -> Unit,
    onPanelToggle: (BoardPanel) -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Gap)
    ) {
        TabKey("123", mode == InputMode.NUM && panel == BoardPanel.NONE, Modifier.weight(1f), view) {
            onModeChange(InputMode.NUM)
        }
        TabKey("ABC", mode == InputMode.ABC && panel == BoardPanel.NONE, Modifier.weight(1f), view) {
            onModeChange(InputMode.ABC)
        }
        TabKey("#+=", mode == InputMode.SYM && panel == BoardPanel.NONE, Modifier.weight(1f), view) {
            onModeChange(InputMode.SYM)
        }
        TabKey("\u2398", panel == BoardPanel.CLIPBOARD, Modifier.weight(0.9f), view) {
            onPanelToggle(BoardPanel.CLIPBOARD)
        }
        TabKey("\u263A", panel == BoardPanel.EMOJI, Modifier.weight(0.9f), view) {
            onPanelToggle(BoardPanel.EMOJI)
        }
    }
}

@Composable
private fun NumBoard(
    onKey: (String) -> Unit,
    onLongPressKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onBackspaceHeld: () -> Unit
) {
    val view = LocalView.current
    KeyLayout.numGrid.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            row.forEach { digit ->
                Key(
                    label = digit,
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    alternates = KeyLayout.alternates(digit),
                    onLongPress = { onLongPressKey(digit) },
                    hapticView = view,
                    onClick = { onKey(digit) }
                )
            }
        }
        Spacer(Modifier.height(Gap))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Gap)
    ) {
        KeyLayout.numFooter.forEach { sym ->
            Key(
                label = sym,
                modifier = Modifier.weight(1f),
                fontSize = 17.sp,
                alternates = KeyLayout.alternates(sym),
                onLongPress = { onLongPressKey(sym) },
                hapticView = view,
                onClick = { onKey(sym) }
            )
        }
        Key(
            label = "SPACE",
            modifier = Modifier.weight(1.8f),
            fontSize = 12.sp,
            hapticView = view,
            onClick = { onKey(" ") }
        )
        Key(
            label = "\u232B",
            modifier = Modifier.weight(1.2f),
            bg = KeyBgAlt,
            fontSize = 16.sp,
            repeatOnHold = true,
            onRepeat = onBackspaceHeld,
            hapticView = view,
            onClick = onBackspace
        )
    }
}

@Composable
private fun AbcBoard(
    shift: Boolean,
    capsLock: Boolean,
    onKey: (String) -> Unit,
    onLongPressKey: (String) -> Unit,
    onShift: () -> Unit,
    onBackspace: () -> Unit,
    onBackspaceHeld: () -> Unit,
    onDeleteWord: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onEnter: () -> Unit
) {
    val view = LocalView.current
    val upper = shift || capsLock
    val row1 = if (upper) KeyLayout.qwertyShifted else KeyLayout.qwertyLower
    val row2 = if (upper) KeyLayout.homeShifted else KeyLayout.homeLower
    val row3 = if (upper) KeyLayout.bottomShifted else KeyLayout.bottomLower

    lettersRow(row1, view, onKey, onLongPressKey)
    lettersRow(row2, view, onKey, onLongPressKey)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Key(
            label = if (capsLock) "\u21EA" else "\u21E7",
            modifier = Modifier.weight(1.5f),
            bg = if (shift || capsLock) AccentGreen else KeyBgAlt,
            textColor = if (shift || capsLock) Color.Black else Color.White,
            fontSize = 16.sp,
            bold = true,
            hapticView = view,
            onClick = onShift
        )
        row3.forEach { ch ->
            Key(
                label = ch,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                alternates = KeyLayout.alternates(ch),
                onLongPress = { onLongPressKey(ch) },
                hapticView = view,
                onClick = { onKey(ch) }
            )
        }
        Key(
            label = "\u232B",
            modifier = Modifier.weight(1.5f),
            bg = KeyBgAlt,
            fontSize = 16.sp,
            repeatOnHold = true,
            onRepeat = onBackspaceHeld,
            hapticView = view,
            onClick = onBackspace
        )
    }
    Spacer(Modifier.height(Gap))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Key(
            label = "\u25C0",
            modifier = Modifier.weight(1f),
            bg = KeyBgAlt,
            fontSize = 14.sp,
            hapticView = view,
            onClick = onMoveLeft
        )
        Key(
            label = "/",
            modifier = Modifier.weight(1f),
            bg = KeyBgAlt,
            fontSize = 16.sp,
            alternates = KeyLayout.alternates("/"),
            onLongPress = { onLongPressKey("/") },
            hapticView = view,
            onClick = { onKey("/") }
        )
        Key(
            label = "SPACE",
            modifier = Modifier.weight(4.4f),
            fontSize = 12.sp,
            longPress = {
                Haptics.longPress(view)
                onEnter()
            },
            /* Swipe left on the space bar deletes the previous word, the gesture
               users expect from a modern keyboard. Swipe right nudges the cursor. */
            onSwipeLeft = onDeleteWord,
            onSwipeRight = { onMoveLeft() },
            hapticView = view,
            onClick = { onKey(" ") }
        )
        Key(
            label = ".",
            modifier = Modifier.weight(1f),
            bg = KeyBgAlt,
            fontSize = 18.sp,
            alternates = KeyLayout.alternates("."),
            onLongPress = { onLongPressKey(".") },
            hapticView = view,
            onClick = { onKey(".") }
        )
        Key(
            label = "\u25B6",
            modifier = Modifier.weight(1f),
            bg = KeyBgAlt,
            fontSize = 14.sp,
            hapticView = view,
            onClick = onMoveRight
        )
    }
}

@Composable
private fun SymBoard(
    onKey: (String) -> Unit,
    onLongPressKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onBackspaceHeld: () -> Unit
) {
    val view = LocalView.current
    lettersRow(KeyLayout.symRow1, view, onKey, onLongPressKey)
    lettersRow(KeyLayout.symRow2, view, onKey, onLongPressKey)
    lettersRow(KeyLayout.symRow3, view, onKey, onLongPressKey)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Gap)
    ) {
        Key(
            label = "SPACE",
            modifier = Modifier.weight(5f),
            fontSize = 12.sp,
            hapticView = view,
            onClick = { onKey(" ") }
        )
        Key(
            label = "\u232B",
            modifier = Modifier.weight(1.4f),
            bg = KeyBgAlt,
            fontSize = 16.sp,
            repeatOnHold = true,
            onRepeat = onBackspaceHeld,
            hapticView = view,
            onClick = onBackspace
        )
    }
}

@Composable
private fun ClipboardPanel(
    items: List<String>,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val view = LocalView.current
    if (items.isEmpty()) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(KeyBgAlt, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CLIPBOARD EMPTY\nCopy something, then paste from here",
                    color = MutedText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
            Spacer(Modifier.height(Gap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Gap)
            ) {
                SmallKey("CLOSE", Modifier.weight(1f), view, onClose)
            }
        }
        return
    }

    Column {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(KeyBgAlt, RoundedCornerShape(9.dp)),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = items, key = { it }) { entry ->
                ClipboardCard(
                    value = entry,
                    view = view,
                    onPick = { onPick(entry) },
                    onDelete = { onDelete(entry) }
                )
            }
        }
        Spacer(Modifier.height(Gap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            SmallKey("CLEAR ALL", Modifier.weight(1f), view, onClear)
            SmallKey("CLOSE", Modifier.weight(1f), view, onClose)
        }
    }
}

@Composable
private fun ClipboardCard(
    value: String,
    view: View,
    onPick: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(168.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(KeyBg)
            .clickable {
                Haptics.tap(view)
                onPick()
            }
            .padding(10.dp)
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF2A3742))
                .clickable {
                    Haptics.tap(view)
                    onDelete()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\u00D7", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmojiPanel(
    recent: List<String>,
    onPick: (String) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit
) {
    val view = LocalView.current
    var category by remember { mutableStateOf(EmojiCategory.SMILE) }

    val shown = if (category == EmojiCategory.RECENT) recent else category.emojis

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            EmojiCategory.entries.forEach { entry ->
                if (entry == EmojiCategory.RECENT && recent.isEmpty()) return@forEach
                val active = entry == category
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) AccentGreen else KeyBgAlt)
                        .clickable {
                            Haptics.tap(view)
                            category = entry
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.label,
                        color = if (active) Color.Black else MutedText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(Gap))

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .background(KeyBgAlt, RoundedCornerShape(9.dp)),
            contentPadding = PaddingValues(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            /* Indexed key: several code points repeat across categories once the
               variation selectors are stripped, so the value alone is not unique. */
            itemsIndexed(items = shown) { _, emoji ->
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            Haptics.tap(view)
                            onPick(emoji)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 22.sp)
                }
            }
        }
        Spacer(Modifier.height(Gap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Gap)
        ) {
            Key(
                label = "ABC",
                modifier = Modifier.weight(1f),
                bg = KeyBgAlt,
                fontSize = 12.sp,
                hapticView = view,
                onClick = onClose
            )
            Key(
                label = "\u232B",
                modifier = Modifier.weight(1.4f),
                bg = KeyBgAlt,
                fontSize = 16.sp,
                repeatOnHold = true,
                onRepeat = onBackspace,
                hapticView = view,
                onClick = onBackspace
            )
        }
    }
}

@Composable
private fun lettersRow(
    letters: List<String>,
    view: View,
    onKey: (String) -> Unit,
    onLongPressKey: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Gap)
    ) {
        letters.forEach { ch ->
            val alts = KeyLayout.alternates(ch)
            Key(
                label = ch,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                alternates = alts,
                onLongPress = if (alts.isNotEmpty()) ({ onLongPressKey(ch) }) else null,
                hapticView = view,
                onClick = { onKey(ch) }
            )
        }
    }
    Spacer(Modifier.height(Gap))
}

/**
 * Base key.
 *
 * One pointer loop drives every gesture so the states cannot fight each other:
 *  - release before [LONG_PRESS_MS] inside the key   → tap ([onClick])
 *  - hold past [LONG_PRESS_MS]                        → [longPress], else [onLongPress]
 *  - horizontal drag past [SWIPE_THRESHOLD_DP]        → swipe left/right
 *  - held with [repeatOnHold]                         → [onRepeat] after the delay
 *
 * The finger is not required to stay inside the key bounds, which is what makes
 * swipe-to-delete feel natural on a small board.
 */
@Composable
private fun Key(
    label: String,
    modifier: Modifier = Modifier,
    bg: Color = KeyBg,
    textColor: Color = Color.White,
    fontSize: TextUnit = 16.sp,
    bold: Boolean = false,
    alternates: List<String> = emptyList(),
    onLongPress: (() -> Unit)? = null,
    longPress: (() -> Unit)? = null,
    repeatOnHold: Boolean = false,
    onRepeat: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    hapticView: View? = null,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val swipePx = remember { SWIPE_THRESHOLD_DP }

    Box(
        modifier = modifier
            .height(KeyH)
            .clip(RoundedCornerShape(9.dp))
            .background(if (pressed) KeyBgPressed else bg)
            .pointerInput(label, repeatOnHold, onSwipeRight, onSwipeLeft, onLongPress, longPress) {
                val threshold = swipePx.dp.toPx()
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true

                        var repeatJob: Job? = null
                        if (repeatOnHold && onRepeat != null) {
                            repeatJob = scope.launch {
                                delay(REPEAT_START_MS)
                                while (true) {
                                    Haptics.tap(hapticView)
                                    onRepeat()
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                        }

                        var up: PointerInputChange? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                up = change
                                break
                            }
                        }

                        repeatJob?.cancel()
                        pressed = false
                        if (up == null) continue

                        val dx = up.position.x - down.position.x
                        val dy = up.position.y - down.position.y
                        val heldLongEnough = (up.uptimeMillis - down.uptimeMillis) >= LONG_PRESS_MS
                        val horizontal = abs(dx) > threshold && abs(dx) > abs(dy)

                        when {
                            horizontal && dx > 0 && onSwipeRight != null -> {
                                Haptics.tap(hapticView)
                                onSwipeRight()
                            }
                            horizontal && dx < 0 && onSwipeLeft != null -> {
                                Haptics.tap(hapticView)
                                onSwipeLeft()
                            }
                            heldLongEnough && (longPress != null || onLongPress != null) -> {
                                Haptics.longPress(hapticView)
                                longPress?.invoke() ?: onLongPress?.invoke()
                            }
                            else -> {
                                Haptics.tap(hapticView)
                                onClick()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = fontSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

/**
 * Rounded action button that mirrors a Material [androidx.compose.material3.Button]
 * without pulling the Material ripple/layout overhead into the 42dp key height.
 */
@Composable
private fun ButtonLike(
    label: String,
    modifier: Modifier = Modifier,
    container: Color,
    textColor: Color,
    fontSize: TextUnit,
    bold: Boolean = false,
    letterSpacing: TextUnit = 0.sp,
    hapticView: View? = null,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(KeyH)
            .clip(RoundedCornerShape(9.dp))
            .background(if (pressed) KeyBgPressed else container)
            .clickable {
                Haptics.tap(hapticView)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = fontSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = letterSpacing,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun SmallKey(
    label: String,
    modifier: Modifier = Modifier,
    view: View?,
    onClick: () -> Unit
) {
    Key(
        label = label,
        modifier = modifier,
        bg = KeyBgAlt,
        textColor = Color.White,
        fontSize = 13.sp,
        bold = true,
        hapticView = view,
        onClick = onClick
    )
}

@Composable
private fun TabKey(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    view: View?,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) AccentGreen else KeyBgAlt)
            .clickable {
                Haptics.tap(view)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color.Black else MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}
