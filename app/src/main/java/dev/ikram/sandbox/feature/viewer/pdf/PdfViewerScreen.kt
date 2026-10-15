package dev.ikram.sandbox.feature.viewer.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.feature.viewer.components.UnsafeFileMessage
import dev.ikram.sandbox.feature.viewer.components.ViewerMessage
import dev.ikram.sandbox.feature.viewer.components.ViewerOverlayActions
import dev.ikram.sandbox.feature.viewer.components.ViewerOverlayState
import dev.ikram.sandbox.feature.viewer.components.ViewerOverlays
import dev.ikram.sandbox.feature.viewer.components.ViewerTopBar
import dev.ikram.sandbox.feature.viewer.image.PageState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val PaperShape = RoundedCornerShape(4.dp)
private const val A4_RATIO = 1f / 1.414f

@Composable
fun PdfViewerRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: PdfViewerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    PdfViewerScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
fun PdfViewerScreen(
    state: PdfViewerUiState,
    onEvent: (PdfViewerEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ViewerTopBar(
            title = state.file?.displayName.orEmpty(),
            onBack = onBack,
            onShare = { onEvent(PdfViewerEvent.ShareTapped) },
            onDetails = { onEvent(PdfViewerEvent.DetailsTapped) },
        )
        when (val document = state.document) {
            PdfDocumentState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            PdfDocumentState.Unsafe -> UnsafeFileMessage()

            PdfDocumentState.Unreadable -> ViewerMessage(
                icon = SandboxIcons.Pdf,
                title = stringResource(R.string.viewer_pdf_unreadable_title),
                message = stringResource(R.string.viewer_pdf_unreadable_message),
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is PdfDocumentState.Ready -> Document(document.pageCount, state, onEvent)
        }
    }
    Overlays(state, onEvent)
}

@Composable
private fun ColumnScope.Document(pageCount: Int, state: PdfViewerUiState, onEvent: (PdfViewerEvent) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentPage by remember { derivedStateOf { mostVisiblePage(listState) } }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collect { visible ->
                if (visible.isNotEmpty()) {
                    onEvent(PdfViewerEvent.PagesVisible(visible.first()..visible.last()))
                }
            }
    }
    val density = LocalDensity.current
    val thumbnailWidthPx = with(density) { THUMBNAIL_WIDTH.roundToPx() * 2 }
    // Pages are sized from the first one drawn until their own size is known.
    val defaultRatio = state.pages.values.firstNotNullOfOrNull { (it as? PageState.Ready)?.image?.ratio() } ?: A4_RATIO

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val pageWidthPx = with(density) { (maxWidth - PAGE_PADDING * 2).roundToPx() }
        LaunchedEffect(pageWidthPx) { onEvent(PdfViewerEvent.Viewport(pageWidthPx, thumbnailWidthPx)) }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = PAGE_PADDING, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(pageCount, key = { it }) { index -> Page(state.pages[index], defaultRatio) }
        }
        PagePill(currentPage + 1, pageCount, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    ThumbnailStrip(
        pageCount = pageCount,
        current = currentPage,
        thumbnails = state.thumbnails,
        onVisible = { onEvent(PdfViewerEvent.ThumbnailsVisible(it)) },
        onSelect = { index -> scope.launch { listState.animateScrollToItem(index) } },
    )
}

/** The page taking up most of the screen, not merely the first one peeking in at the top. */
private fun mostVisiblePage(listState: LazyListState): Int {
    val info = listState.layoutInfo
    val viewportEnd = info.viewportEndOffset
    return info.visibleItemsInfo.maxByOrNull { item ->
        minOf(item.offset + item.size, viewportEnd) - maxOf(item.offset, 0)
    }?.index ?: 0
}

@Composable
private fun Page(page: PageState?, defaultRatio: Float) {
    val image = (page as? PageState.Ready)?.image
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(image?.ratio() ?: defaultRatio)
            .shadow(3.dp, PaperShape)
            .background(Color.White, PaperShape),
        contentAlignment = Alignment.Center,
    ) {
        when (page) {
            is PageState.Ready -> Image(page.image, contentDescription = null, modifier = Modifier.fillMaxSize())

            PageState.NotDecodable, PageState.Unsafe -> Text(
                stringResource(R.string.viewer_pdf_unreadable_title),
                color = Color.DarkGray,
                style = MaterialTheme.typography.bodySmall,
            )

            null, PageState.Loading -> CircularProgressIndicator(color = Color.LightGray)
        }
    }
}

@Composable
private fun PagePill(page: Int, pageCount: Int, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(32.dp)
            .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.viewer_page_of, page, pageCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}

@Composable
private fun ThumbnailStrip(
    pageCount: Int,
    current: Int,
    thumbnails: Map<Int, ImageBitmap>,
    onVisible: (IntRange) -> Unit,
    onSelect: (Int) -> Unit,
) {
    val stripState = rememberLazyListState()
    LaunchedEffect(current) { stripState.animateScrollToItem(maxOf(0, current - 2)) }
    LaunchedEffect(stripState) {
        snapshotFlow { stripState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collect { visible -> if (visible.isNotEmpty()) onVisible(visible.first()..visible.last()) }
    }
    LazyRow(
        state = stripState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        items(pageCount, key = { it }) { index ->
            Thumbnail(index, thumbnails[index], selected = index == current, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun Thumbnail(index: Int, image: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (selected) {
                        Modifier.border(2.dp, colors.primary, RoundedCornerShape(6.dp)).padding(3.dp)
                    } else {
                        Modifier.padding(3.dp)
                    },
                )
                .size(width = THUMBNAIL_WIDTH, height = 68.dp)
                .clip(PaperShape)
                .background(Color.White)
                .border(1.dp, colors.outlineVariant, PaperShape),
        ) {
            if (image != null) {
                Image(
                    image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.width(THUMBNAIL_WIDTH),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Overlays(state: PdfViewerUiState, onEvent: (PdfViewerEvent) -> Unit) {
    ViewerOverlays(
        state = ViewerOverlayState(
            file = state.file,
            showDetails = state.showDetails,
            confirmDelete = state.confirmDelete,
            sharing = state.sharing,
        ),
        actions = ViewerOverlayActions(
            onShare = { onEvent(PdfViewerEvent.ShareTapped) },
            onShareDismissed = { onEvent(PdfViewerEvent.ShareDismissed) },
            onDelete = { onEvent(PdfViewerEvent.DeleteTapped) },
            onDeleteConfirmed = { onEvent(PdfViewerEvent.DeleteConfirmed) },
            onDeleteDismissed = { onEvent(PdfViewerEvent.DeleteDismissed) },
            onDetailsDismissed = { onEvent(PdfViewerEvent.DetailsDismissed) },
        ),
    )
}

private fun ImageBitmap.ratio(): Float = width.toFloat() / height

private val PAGE_PADDING = 16.dp
private val THUMBNAIL_WIDTH = 52.dp
