package org.matrix.vector.manager.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.matrix.vector.manager.R
import org.matrix.vector.ui.PackageRow
import org.matrix.vector.ui.SearchField
import org.matrix.vector.manager.ui.components.SnackbarTone
import org.matrix.vector.manager.ui.components.VectorSnackbarHost
import org.matrix.vector.manager.ui.components.show

/**
 * The per-app picker behind the "ART inline hook compatibility mode" row.
 *
 * Every installed app with a switch, plus the Android system UI at the top. Each switch records the
 * package in the daemon's preference store; nothing takes effect until that app's process next
 * starts, which is said at the top of the list rather than left to be discovered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvalidateArtInlineHooksScreen(
    onNavigateBack: () -> Unit,
    viewModel: InlineHookViewModel = viewModel(factory = InlineHookLocator.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.invalidate_art_inline_hooks_save_failed)

    LaunchedEffect(message) {
        if (message != null) {
            scope.launch { snackbars.show(saveFailed, SnackbarTone.Failure) }
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { VectorSnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invalidate_art_inline_hooks)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.invalidate_art_inline_hooks_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                SearchField(
                    query = query,
                    onQueryChange = { viewModel.searchQuery.value = it },
                    placeholder = stringResource(R.string.invalidate_art_inline_hooks_search_hint),
                )
                Spacer(Modifier.height(4.dp))
            }
            if (state.loading) {
                item { CircularProgressIndicator() }
            } else {
                items(state.rows, key = { it.packageName }) { row ->
                    PackageRow(
                        icon = {
                            Icon(
                                imageVector = rowIcon(row.isSystemUi),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        label =
                            if (row.isSystemUi) {
                                stringResource(R.string.invalidate_art_inline_hooks_system_ui)
                            } else {
                                row.label
                            },
                        packageName = row.appName,
                        trailing = {
                            Switch(
                                checked = row.enabled,
                                onCheckedChange = { enabled -> viewModel.setEnabled(row, enabled) },
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun rowIcon(isSystemUi: Boolean): ImageVector = Icons.Rounded.Android
