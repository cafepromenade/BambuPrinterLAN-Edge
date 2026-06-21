package com.bambuprinterlan.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The first imported model, shared with the 3D editor viewport. */
object WorkspaceStore {
    private val _firstModel = MutableStateFlow<Pair<String, String>?>(null)  // uri, name
    val firstModel: StateFlow<Pair<String, String>?> = _firstModel.asStateFlow()

    fun setFirst(uri: String?, name: String?) {
        _firstModel.value = if (uri != null && name != null) uri to name else null
    }
}
