package app.forgetit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forgetit.AppContainer
import app.forgetit.data.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Shared state for the tab screens: every tracker's records plus settings and cover photos. */
class MainViewModel(val c: AppContainer) : ViewModel() {
    val subs = c.subscriptions.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<Settings> = c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** First photo of each record, keyed by "OWNERTYPE:id". */
    val covers: StateFlow<Map<String, File>> = c.photos.observeAll()
        .map { list ->
            list.groupBy { "${it.ownerType}:${it.ownerId}" }
                .mapValues { c.photos.file(it.value.first().fileName) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun deleteSubscription(id: Long) = viewModelScope.launch { c.subscriptions.delete(id) }
}
