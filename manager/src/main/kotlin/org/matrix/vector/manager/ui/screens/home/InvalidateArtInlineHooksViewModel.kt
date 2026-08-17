package org.matrix.vector.manager.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.vector.manager.data.model.AppInfo
import org.matrix.vector.manager.data.repository.AppRepository
import org.matrix.vector.manager.di.ServiceLocator
import org.matrix.vector.manager.ipc.DaemonClient
import org.matrix.vector.manager.logE
import org.matrix.vector.manager.logW

/** The key the daemon stores the system UI's opt-in under; see PreferenceStore. */
const val SYSTEM_UI_VIRTUAL_PACKAGE = "system"

/** The synthetic display label for the Android system UI process. */
const val SYSTEM_UI_LABEL = "system"

/**
 * A package the reader can opt into ART inline-hook invalidation, shown as a switch.
 *
 * Every installed app plus one synthetic row for the system UI, whose process is {@code system:ui}
 * and which the daemon resolves from the reserved package name {@code system}.
 */
data class InlineHookRow(
    val packageName: String,
    val label: String,
    val appName: String,
    val enabled: Boolean,
    val isSystemUi: Boolean = false,
)

data class InlineHookUiState(
    val rows: List<InlineHookRow> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Picks which packages invalidate Vector's native ART inline hooks after injection.
 *
 * This is the manager's view of {@code PreferenceStore.invalidate_art_inline_hooks:*} on the
 * daemon. Nothing here knows or cares about the libart.so mechanics — the daemon resolves each
 * package name against real process topology when an app next starts, and this screen only
 * records the reader's choices.
 */
class InlineHookViewModel(
    private val daemonClient: DaemonClient,
    private val appRepository: AppRepository,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    private val configured = MutableStateFlow<Set<String>>(emptySet())
    private val apps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val loading = MutableStateFlow(true)

    private val _message = MutableStateFlow<InlineHookMessage?>(null)
    val message: StateFlow<InlineHookMessage?> = _message.asStateFlow()

    val uiState: StateFlow<InlineHookUiState> =
        combine(apps, configured, searchQuery, loading) { appList, set, query, isLoading ->
                val rows = buildList {
                    val systemUi = InlineHookRow(
                        packageName = SYSTEM_UI_VIRTUAL_PACKAGE,
                        label = SYSTEM_UI_LABEL,
                        appName = SYSTEM_UI_LABEL,
                        enabled = SYSTEM_UI_VIRTUAL_PACKAGE in set,
                        isSystemUi = true,
                    )
                    // The system UI leads, then everything else alphabetically.
                    if (query.isBlank() || SYSTEM_UI_LABEL.contains(query, ignoreCase = true)) {
                        add(systemUi)
                    }
                    appList
                        .asSequence()
                        .map { app ->
                            InlineHookRow(
                                packageName = app.packageName,
                                label = app.appName,
                                appName = app.packageName,
                                enabled = app.packageName in set,
                            )
                        }
                        .filter { row ->
                            query.isBlank() ||
                                row.label.contains(query, ignoreCase = true) ||
                                row.appName.contains(query, ignoreCase = true)
                        }
                        .sortedBy { it.label.lowercase() }
                        .forEach { add(it) }
                }
                InlineHookUiState(rows = rows, loading = isLoading)
            }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
                InlineHookUiState())

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                apps.value = appRepository.getInstalledApps()
            }
            loadConfigured()
            loading.value = false
        }
    }

    private suspend fun loadConfigured() {
        daemonClient
            .getInvalidateArtInlineHookPackages()
            .onSuccess { set -> configured.value = set.toSet() }
            .onFailure { e -> logW("inline-hooks: reading the configured packages failed", e) }
    }

    fun setEnabled(row: InlineHookRow, enabled: Boolean) {
        viewModelScope.launch {
            daemonClient
                .setInvalidateArtInlineHooks(row.packageName, enabled)
                .onSuccess { stored ->
                    // The daemon's answer, not merely the fact that it answered: a blank name is
                    // refused, and moving the switch on a refusal would show a choice that was
                    // never saved.
                    if (stored) {
                        configured.value =
                            if (enabled) configured.value + row.packageName
                            else configured.value - row.packageName
                    } else {
                        logE("inline-hooks: daemon refused $enabled for ${row.packageName}")
                        _message.value = InlineHookMessage.SaveFailed
                    }
                }
                .onFailure { e ->
                    logE("inline-hooks: setting $enabled for ${row.packageName} failed", e)
                    _message.value = InlineHookMessage.SaveFailed
                }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

sealed interface InlineHookMessage {
    data object SaveFailed : InlineHookMessage
}

class InlineHookViewModelFactory(
    private val daemonClient: DaemonClient,
    private val appRepository: AppRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        InlineHookViewModel(daemonClient, appRepository) as T
}

/** Accessible from the screen to build the [InlineHookViewModelFactory] the same way [ScopeScreen] does. */
object InlineHookLocator {
    fun factory(): InlineHookViewModelFactory =
        InlineHookViewModelFactory(
            daemonClient = ServiceLocator.daemon,
            appRepository = ServiceLocator.apps,
        )
}
