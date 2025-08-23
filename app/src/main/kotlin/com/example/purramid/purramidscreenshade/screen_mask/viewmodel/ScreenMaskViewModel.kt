// ScreenMaskViewModel.kt
package com.example.purramid.purramidscreenshade.screen_mask.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.purramidscreenshade.screen_mask.repository.ScreenMaskRepository
import com.example.purramid.purramidscreenshade.screen_mask.ScreenMaskState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ScreenMaskViewModel @Inject constructor(
    private val repository: ScreenMaskRepository,
    savedStateHandle: SavedStateHandle // Hilt injects this
) : ViewModel() {

    companion object {
        const val KEY_INSTANCE_ID = "screenMaskInstanceId"
    }

    private var instanceId: Int = savedStateHandle.get<Int>(KEY_INSTANCE_ID) ?: 0

    // Use repository's state flow if instance ID is valid, otherwise create empty state
    val uiState: StateFlow<ScreenMaskState> = if (instanceId > 0) {
        repository.getMaskStateFlow(instanceId)
    } else {
        MutableStateFlow(ScreenMaskState()).asStateFlow()
    }

    init {
        if (instanceId > 0) {
            viewModelScope.launch {
                repository.loadState(instanceId)
            }
        }
    }

    fun updatePosition(x: Int, y: Int) {
        if (instanceId == 0) return
        viewModelScope.launch {
            repository.updatePosition(instanceId, x, y)
        }
    }

    fun updateSize(width: Int, height: Int) {
        if (instanceId <= 0) return
        viewModelScope.launch {
            repository.updateSize(instanceId, width, height)
        }
    }

    fun toggleLock() {
        if (instanceId <= 0) return
        viewModelScope.launch {
            repository.toggleLock(instanceId)
        }
    }

    fun setBillboardImageUri(uriString: String?) {
        if (instanceId <= 0) return
        viewModelScope.launch {
            repository.setBillboardImageUri(instanceId, uriString)
        }
    }

    fun toggleControlsVisibility() {
        if (instanceId <= 0) return
        viewModelScope.launch {
            repository.toggleControlsVisibility(instanceId)
        }
    }
}