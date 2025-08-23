// SpotlightSettingsViewModel.kt
package com.example.purramid.purramidscreenshade.spotlight.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.purramidscreenshade.instance.InstanceManager
import com.example.purramid.purramidscreenshade.spotlight.SpotlightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpotlightSettingsViewModel @Inject constructor(
    private val repository: SpotlightRepository,
    private val instanceManager: InstanceManager
) : ViewModel() {

    data class SettingsUiState(
        val activeInstanceCount: Int = 0,
        val canAddMore: Boolean = true,
        val isLoading: Boolean = false
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadActiveInstances()
    }

    private fun loadActiveInstances() {
        viewModelScope.launch {
            val count = repository.getActiveInstanceCount()
            _uiState.value = SettingsUiState(
                activeInstanceCount = count,
                canAddMore = count < 4,
                isLoading = false
            )
        }
    }

    fun canAddNewSpotlight(): Boolean {
        return instanceManager.getActiveInstanceCount(InstanceManager.SPOTLIGHT) < 4
    }

    fun refresh() {
        loadActiveInstances()
    }
}