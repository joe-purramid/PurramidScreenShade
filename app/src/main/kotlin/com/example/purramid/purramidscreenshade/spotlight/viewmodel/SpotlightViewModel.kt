// SpotlightViewModel.kt
package com.example.purramid.purramidscreenshade.spotlight.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.purramidscreenshade.spotlight.SpotlightUiState
import com.example.purramid.purramidscreenshade.spotlight.repository.SpotlightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpotlightViewModel @Inject constructor(
    private val repository: SpotlightRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val KEY_INSTANCE_ID = "spotlightInstanceId"
    }

    private val instanceId: Int = savedStateHandle.get<Int>(KEY_INSTANCE_ID) ?: 0

    // Use repository's state flow if instance ID is valid
    val uiState: StateFlow<SpotlightUiState> = if (instanceId > 0) {
        repository.getSpotlightStateFlow(instanceId)
    } else {
        MutableStateFlow(SpotlightUiState()).asStateFlow()
    }

    init {
        if (instanceId > 0) {
            viewModelScope.launch {
                repository.loadState(instanceId)
            }
        }
    }

    fun toggleControlsVisibility() {
        if (instanceId <= 0) return
        repository.toggleControlsVisibility(instanceId)
    }

    fun addOpening(screenWidth: Int, screenHeight: Int) {
        if (instanceId <= 0) return
        viewModelScope.launch {
            repository.addOpening(instanceId, screenWidth, screenHeight)
        }
    }
}