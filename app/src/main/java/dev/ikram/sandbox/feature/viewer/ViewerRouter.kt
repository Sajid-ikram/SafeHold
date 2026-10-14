package dev.ikram.sandbox.feature.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.SandboxTopBar
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.feature.viewer.components.UnsafeFileMessage
import dev.ikram.sandbox.feature.viewer.components.ViewerMessage
import dev.ikram.sandbox.feature.viewer.image.ImageViewerRoute
import dev.ikram.sandbox.feature.viewer.pdf.PdfViewerRoute

/**
 * Picks the built in viewer for a vault file from its category, and refuses blocked,
 * unknown and archive files outright, whatever route led here.
 */
@Composable
fun ViewerRouter(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ViewerRouterViewModel = hiltViewModel(),
) {
    val target by viewModel.target.collectAsStateWithLifecycle()
    when (val current = target) {
        ViewerTarget.Loading -> Unit

        ViewerTarget.Missing -> Refusal(onBack, modifier, missing = true)

        is ViewerTarget.Found -> {
            val file = current.file
            when {
                !TypePolicy.rulesFor(file.category).canOpen -> Refusal(onBack, modifier, missing = false)

                file.openFailed -> Plain(onBack, modifier) { UnsafeFileMessage() }

                file.category == FileCategory.IMAGE -> ImageViewerRoute(onBack = onBack, modifier = modifier)

                file.category == FileCategory.PDF -> PdfViewerRoute(onBack = onBack, modifier = modifier)

                else -> Plain(onBack, modifier) {
                    ViewerMessage(
                        icon = SandboxIcons.Clock,
                        title = file.displayName,
                        message = stringResource(R.string.viewer_placeholder),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun Refusal(onBack: () -> Unit, modifier: Modifier, missing: Boolean) {
    Plain(onBack, modifier) {
        val semantic = SandboxTheme.semanticColors
        ViewerMessage(
            icon = if (missing) SandboxIcons.Unknown else SandboxIcons.Lock,
            title = stringResource(if (missing) R.string.viewer_missing_title else R.string.viewer_refused_title),
            message = stringResource(if (missing) R.string.viewer_missing_message else R.string.viewer_refused_message),
            container = semantic.blockedContainer,
            content = semantic.onBlockedContainer,
        )
    }
}

@Composable
private fun Plain(onBack: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SandboxTopBar(title = "", onBack = onBack, backContentDescription = stringResource(R.string.action_back))
        content()
    }
}
