package dev.ikram.sandbox.feature.viewer.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.vault.FileDetailsSheet
import dev.ikram.sandbox.feature.viewer.components.SandboxIndicator
import dev.ikram.sandbox.feature.viewer.components.UnsafeFileMessage
import dev.ikram.sandbox.feature.viewer.components.ViewerMessage
import dev.ikram.sandbox.util.LightSystemBarIcons
import kotlinx.coroutines.launch

/** The viewer is always dark, as in the design, whatever the system theme. */
private val ViewerBackground = Color(0xFF050807)
private val ViewerContent = Color(0xFFE6ECEA)
private val ViewerMuted = Color(0xFF9AA7A3)
private val ViewerOverlay = Color.White.copy(alpha = 0.12f)

@Composable
fun ImageViewerRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImageViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    ImageViewerScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
fun ImageViewerScreen(
    state: ImageViewerUiState,
    onEvent: (ImageViewerEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LightSystemBarIcons()
    SandboxTheme(darkTheme = true) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ViewerBackground),
        ) {
            if (state.images.isNotEmpty()) {
                ViewerContent(state, onEvent, onBack)
            }
        }
        Overlays(state, onEvent)
    }
}

@Composable
private fun ViewerContent(state: ImageViewerUiState, onEvent: (ImageViewerEvent) -> Unit, onBack: () -> Unit) {
    val images = state.images
    val pagerState = rememberPagerState(initialPage = state.startIndex.coerceIn(0, images.lastIndex)) { images.size }
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    val current = images.getOrNull(pagerState.currentPage) ?: images.last()
    LaunchedEffect(current.id) {
        zoom = 1f
        onEvent(ImageViewerEvent.PageShown(current.id))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(
            file = current,
            onBack = onBack,
            onShare = { onEvent(ImageViewerEvent.ShareTapped) },
            onDetails = { onEvent(ImageViewerEvent.DetailsTapped(current.id)) },
        )
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val longestSide = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }
            LaunchedEffect(longestSide) { onEvent(ImageViewerEvent.Viewport(longestSide)) }
            HorizontalPager(
                state = pagerState,
                key = { images[it].id },
                userScrollEnabled = zoom <= 1.01f,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val file = images[page]
                Page(file, state.pages[file.id], onZoomChange = { if (page == pagerState.currentPage) zoom = it })
            }
            ZoomChip(zoom, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
            if (pagerState.currentPage > 0) {
                RoundButton(
                    SandboxIcons.Back,
                    R.string.viewer_previous,
                    Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                ) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                }
            }
            if (pagerState.currentPage < images.lastIndex) {
                RoundButton(
                    SandboxIcons.Forward,
                    R.string.viewer_next,
                    Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                ) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            }
        }
        Footer(
            images = images,
            current = pagerState.currentPage,
            thumbnails = state.thumbnails,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        )
    }
}

@Composable
private fun Page(file: VaultFile, page: PageState?, onZoomChange: (Float) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (page) {
            null, PageState.Loading -> CircularProgressIndicator(color = ViewerMuted)

            is PageState.Ready -> ZoomableImage(
                page.image,
                contentDescription = file.displayName,
                onZoomChange = onZoomChange,
            )

            PageState.Unsafe -> UnsafeFileMessage()

            PageState.NotDecodable -> ViewerMessage(
                icon = SandboxIcons.Image,
                title = stringResource(R.string.viewer_undecodable_title),
                message = stringResource(R.string.viewer_undecodable_message),
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopBar(file: VaultFile, onBack: () -> Unit, onShare: () -> Unit, onDetails: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(SandboxIcons.Back, stringResource(R.string.action_back), tint = ViewerContent)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = ViewerContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SandboxIndicator()
        }
        IconButton(onClick = onShare) {
            Icon(SandboxIcons.Share, stringResource(R.string.viewer_share), tint = ViewerContent)
        }
        IconButton(onClick = onDetails) {
            Icon(SandboxIcons.Info, stringResource(R.string.viewer_details), tint = ViewerContent)
        }
    }
}

@Composable
private fun ZoomChip(zoom: Float, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(28.dp)
            .background(ViewerOverlay, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.viewer_zoom, zoom),
            color = ViewerContent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: Int, modifier: Modifier, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .background(ViewerOverlay, CircleShape),
    ) {
        Icon(icon, stringResource(label), tint = ViewerContent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Footer(
    images: List<VaultFile>,
    current: Int,
    thumbnails: Map<String, ImageBitmap>,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(current) { listState.animateScrollToItem(maxOf(0, current - 2)) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = pluralStringResource(R.plurals.viewer_image_count, images.size, current + 1, images.size),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFC5CFCC),
            fontWeight = FontWeight.SemiBold,
        )
        if (images.size > 1) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(images, key = { _, file -> file.id }) { index, file ->
                    Thumbnail(thumbnails[file.id], selected = index == current, onClick = { onSelect(index) })
                }
            }
        }
        Text(stringResource(R.string.viewer_hint), style = MaterialTheme.typography.bodySmall, color = ViewerMuted)
    }
}

@Composable
private fun Thumbnail(image: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
            .then(
                if (selected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(10.dp),
                    )
                } else {
                    Modifier
                },
            )
            .padding(2.dp)
            .clip(shape)
            .alpha(if (selected) 1f else UNSELECTED_ALPHA)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Overlays(state: ImageViewerUiState, onEvent: (ImageViewerEvent) -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = when (val message = state.message) {
        null -> null
        ImageViewerMessage.ShareNotReady -> stringResource(R.string.vault_message_share_not_ready)
        is ImageViewerMessage.Deleted -> stringResource(R.string.vault_message_deleted, message.name)
    }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onEvent(ImageViewerEvent.MessageShown)
        }
    }
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbarHostState)
    }
    state.details?.let { file ->
        FileDetailsSheet(
            file = file,
            onOpen = {},
            onShare = { onEvent(ImageViewerEvent.ShareTapped) },
            onDelete = { onEvent(ImageViewerEvent.DeleteTapped(file.id)) },
            onHashCopied = {},
            onDismiss = { onEvent(ImageViewerEvent.DetailsDismissed) },
            showOpen = false,
        )
    }
    state.confirmDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { onEvent(ImageViewerEvent.DeleteDismissed) },
            title = { Text(stringResource(R.string.delete_title, file.displayName)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(ImageViewerEvent.DeleteConfirmed) }) {
                    Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onEvent(ImageViewerEvent.DeleteDismissed)
                }) { Text(stringResource(R.string.delete_cancel)) }
            },
        )
    }
}

private const val UNSELECTED_ALPHA = 0.7f
