package dev.ikram.sandbox.feature.viewer.text

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.MonoFamily
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.feature.viewer.components.ViewerMessage
import dev.ikram.sandbox.feature.viewer.components.ViewerOverlayActions
import dev.ikram.sandbox.feature.viewer.components.ViewerOverlayState
import dev.ikram.sandbox.feature.viewer.components.ViewerOverlays
import dev.ikram.sandbox.feature.viewer.components.ViewerTopBar
import dev.ikram.sandbox.util.Formatters

private const val LINE_HEIGHT = 22f / 13f
private const val CONTEXT_ROWS = 3

@Composable
fun TextViewerRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TextViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    TextViewerScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
fun TextViewerScreen(
    state: TextViewerUiState,
    onEvent: (TextViewerEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.find.open) { onEvent(TextViewerEvent.FindClosed) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        if (state.find.open) {
            FindBar(state.find, onEvent)
        } else {
            ViewerTopBar(
                title = state.file?.displayName.orEmpty(),
                onBack = onBack,
                onShare = null,
                onDetails = { onEvent(TextViewerEvent.DetailsTapped) },
                onFind = { onEvent(TextViewerEvent.FindOpened) }.takeIf { state.content is TextContent.Ready },
            )
        }
        when (val content = state.content) {
            TextContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            TextContent.Unreadable -> ViewerMessage(
                icon = SandboxIcons.Text,
                title = stringResource(R.string.text_unreadable_title),
                message = stringResource(R.string.text_unreadable_message),
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is TextContent.Ready -> {
                PlainTextNote(content.document, state.file?.sizeBytes ?: 0)
                TextBody(content.document, state, Modifier.weight(1f))
                BottomBar(state, onEvent)
            }
        }
    }
    ViewerOverlays(
        state = ViewerOverlayState(
            file = state.file,
            showDetails = state.showDetails,
            confirmDelete = state.confirmDelete,
            sharing = state.sharing,
        ),
        actions = ViewerOverlayActions(
            onShare = { onEvent(TextViewerEvent.ShareTapped) },
            onShareDismissed = { onEvent(TextViewerEvent.ShareDismissed) },
            onDelete = { onEvent(TextViewerEvent.DeleteTapped) },
            onDeleteConfirmed = { onEvent(TextViewerEvent.DeleteConfirmed) },
            onDeleteDismissed = { onEvent(TextViewerEvent.DeleteDismissed) },
            onDetailsDismissed = { onEvent(TextViewerEvent.DetailsDismissed) },
        ),
    )
}

@Composable
private fun PlainTextNote(document: TextDocument, sizeBytes: Long) {
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            SandboxIcons.Text,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(stringResource(R.string.text_plain_note), style = MaterialTheme.typography.bodySmall)
            if (document.truncated) {
                Text(
                    text = stringResource(
                        R.string.text_truncated,
                        Formatters.size(TextDecoder.MAX_BYTES.toLong()),
                        Formatters.size(sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TextBody(document: TextDocument, state: TextViewerUiState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = TextStyle(
        fontFamily = MonoFamily,
        fontSize = state.fontSizeSp.sp,
        lineHeight = (state.fontSizeSp * LINE_HEIGHT).sp,
        color = colors.onSurface,
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charWidth = remember(style) { measurer.measure("0", style).size.width }
    // Room for the largest line number, never narrower than the design's 44 dp column.
    val gutter = remember(style, document.lineCount) {
        val digits = with(density) { measurer.measure(document.lineCount.toString(), style).size.width.toDp() }
        maxOf(digits + 24.dp, 44.dp)
    }
    val listState = rememberLazyListState()
    val horizontal = rememberScrollState()
    val find = state.find
    LaunchedEffect(find.current, find.matches) {
        val match = find.matches.getOrNull(find.current) ?: return@LaunchedEffect
        val row = document.rowAt(match.start)
        listState.animateScrollToItem((row - CONTEXT_ROWS).coerceAtLeast(0))
        if (!state.wrap) {
            val x = (match.start - document.rowStart(row)) * charWidth
            horizontal.animateScrollTo((x - horizontal.viewportSize / 3).coerceAtLeast(0))
        }
    }
    val highlight = Highlights(
        match = colors.primary.copy(alpha = 0.22f),
        current = SandboxTheme.semanticColors.caution.copy(alpha = 0.45f),
    )

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow),
    ) {
        val textWidth = with(density) { (document.widestRow * charWidth).toDp() } + 14.dp
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(document.rowCount) { row ->
                TextRow(
                    document = document,
                    row = row,
                    state = state,
                    style = style,
                    gutter = gutter.takeIf { state.lineNumbers },
                    textWidth = textWidth.takeUnless { state.wrap },
                    horizontal = horizontal,
                    highlight = highlight,
                )
            }
        }
    }
}

private data class Highlights(val match: Color, val current: Color)

@Composable
private fun TextRow(
    document: TextDocument,
    row: Int,
    state: TextViewerUiState,
    style: TextStyle,
    gutter: Dp?,
    textWidth: Dp?,
    horizontal: ScrollState,
    highlight: Highlights,
) {
    val find = state.find
    val text = remember(document, row, find.matches, find.current, highlight) {
        highlighted(document.row(row), TextSearch.highlights(document, row, find.matches, find.current), highlight)
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        if (gutter != null) {
            Text(
                text = document.lineNumber(row)?.toString().orEmpty(),
                style = style.copy(color = MaterialTheme.colorScheme.outline),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .width(gutter)
                    .padding(end = 12.dp),
            )
        } else {
            Spacer(Modifier.width(14.dp))
        }
        if (textWidth == null) {
            Text(text, style = style, modifier = Modifier.weight(1f).padding(end = 14.dp))
        } else {
            // Every row scrolls with one shared state and has the same width, so they move together.
            Box(modifier = Modifier.weight(1f).horizontalScroll(horizontal)) {
                Text(
                    text,
                    style = style,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.width(textWidth),
                )
            }
        }
    }
}

private fun highlighted(text: String, ranges: List<TextSearch.Highlight>, colors: Highlights): AnnotatedString =
    if (ranges.isEmpty()) {
        AnnotatedString(text)
    } else {
        buildAnnotatedString {
            append(text)
            ranges.forEach { range ->
                addStyle(
                    SpanStyle(background = if (range.isCurrent) colors.current else colors.match),
                    range.start,
                    range.end,
                )
            }
        }
    }

@Composable
private fun FindBar(find: FindState, onEvent: (TextViewerEvent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onEvent(TextViewerEvent.FindClosed) }) {
            Icon(SandboxIcons.Back, stringResource(R.string.text_find_close))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(colors.surfaceContainerHigh, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (find.query.isEmpty()) {
                Text(
                    stringResource(R.string.text_find_hint),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = find.query,
                onValueChange = { onEvent(TextViewerEvent.QueryChanged(it)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onEvent(TextViewerEvent.NextMatch) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        if (find.query.isNotEmpty()) {
            Text(
                text = when {
                    find.matches.isEmpty() -> stringResource(R.string.text_find_none)

                    find.capped -> stringResource(
                        R.string.text_find_position_capped,
                        find.current + 1,
                        find.matches.size,
                    )

                    else -> stringResource(R.string.text_find_position, find.current + 1, find.matches.size)
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        IconButton(onClick = { onEvent(TextViewerEvent.PreviousMatch) }, enabled = find.matches.isNotEmpty()) {
            Icon(SandboxIcons.ChevronUp, stringResource(R.string.text_find_previous))
        }
        IconButton(onClick = { onEvent(TextViewerEvent.NextMatch) }, enabled = find.matches.isNotEmpty()) {
            Icon(SandboxIcons.ChevronDown, stringResource(R.string.text_find_next))
        }
    }
}

@Composable
private fun BottomBar(state: TextViewerUiState, onEvent: (TextViewerEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleChip(state.wrap, R.string.text_wrap_lines) { onEvent(TextViewerEvent.ToggleWrap) }
        ToggleChip(state.lineNumbers, R.string.text_line_numbers) { onEvent(TextViewerEvent.ToggleLineNumbers) }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = { onEvent(TextViewerEvent.SmallerText) },
            enabled = state.fontSizeSp > TextViewerViewModel.MIN_FONT_SP,
        ) {
            SizeGlyph(13, R.string.text_smaller)
        }
        IconButton(
            onClick = { onEvent(TextViewerEvent.LargerText) },
            enabled = state.fontSizeSp < TextViewerViewModel.MAX_FONT_SP,
        ) {
            SizeGlyph(18, R.string.text_larger)
        }
    }
}

@Composable
private fun ToggleChip(selected: Boolean, label: Int, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = if (selected) {
            { Icon(SandboxIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

/** The design's small and large "A". Read out as "Smaller text" and "Larger text". */
@Composable
private fun SizeGlyph(sizeSp: Int, label: Int) {
    val description = stringResource(label)
    Text(
        text = "A",
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.semantics { contentDescription = description },
    )
}
