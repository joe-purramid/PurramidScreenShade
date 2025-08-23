package com.example.purramid.purramidscreenshade.screen_mask.repository

import android.util.Log
import com.example.purramid.purramidscreenshade.data.db.ScreenMaskDao
import com.example.purramid.purramidscreenshade.data.db.ScreenMaskStateEntity
import com.example.purramid.purramidscreenshade.screen_mask.ScreenMaskState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenMaskRepository @Inject constructor(
    private val screenMaskDao: ScreenMaskDao
) {
    companion object {
        private const val TAG = "ScreenMaskRepository"
    }

    // In-memory state management for active masks
    private val activeMaskStates = ConcurrentHashMap<Int, MutableStateFlow<ScreenMaskState>>()

    // Get or create a state flow for a specific instance
    fun getMaskStateFlow(instanceId: Int): StateFlow<ScreenMaskState> {
        return activeMaskStates.computeIfAbsent(instanceId) {
            MutableStateFlow(ScreenMaskState(instanceId = instanceId))
        }.asStateFlow()
    }

    // Load state from database
    suspend fun loadState(instanceId: Int): ScreenMaskState = withContext(Dispatchers.IO) {
        val entity = screenMaskDao.getById(instanceId)
        val state = if (entity != null) {
            mapEntityToState(entity)
        } else {
            // Create default state for new instance
            val defaultState = ScreenMaskState(instanceId = instanceId)
            saveState(defaultState) // Save initial state
            defaultState
        }
        
        // Update in-memory state
        activeMaskStates.computeIfAbsent(instanceId) {
            MutableStateFlow(state)
        }.value = state
        
        Log.d(TAG, "Loaded state for instance $instanceId: $state")
        state
    }

    // Save state to database
    suspend fun saveState(state: ScreenMaskState) = withContext(Dispatchers.IO) {
        if (state.instanceId <= 0) {
            Log.w(TAG, "Attempted to save state with invalid instanceId: ${state.instanceId}")
            return@withContext
        }
        
        try {
            screenMaskDao.insertOrUpdate(mapStateToEntity(state))
            Log.d(TAG, "Saved state for instance ${state.instanceId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving state for instance ${state.instanceId}", e)
        }
    }

    // Update position
    suspend fun updatePosition(instanceId: Int, x: Int, y: Int) {
        val stateFlow = activeMaskStates[instanceId] ?: return
        if (stateFlow.value.x == x && stateFlow.value.y == y) return
        
        val newState = stateFlow.value.copy(x = x, y = y)
        stateFlow.value = newState
        saveState(newState)
    }

    // Update size
    suspend fun updateSize(instanceId: Int, width: Int, height: Int) {
        val stateFlow = activeMaskStates[instanceId] ?: return
        if (stateFlow.value.width == width && stateFlow.value.height == height) return
        
        val newState = stateFlow.value.copy(width = width, height = height)
        stateFlow.value = newState
        saveState(newState)
    }

    // Toggle lock
    suspend fun toggleLock(instanceId: Int) {
        val stateFlow = activeMaskStates[instanceId] ?: return
        val newState = stateFlow.value.copy(isLocked = !stateFlow.value.isLocked)
        stateFlow.value = newState
        saveState(newState)
    }

    // Set locked state
    suspend fun setLocked(instanceId: Int, locked: Boolean, isFromLockAll: Boolean = false) {
        val stateFlow = activeMaskStates[instanceId] ?: return
        val newState = stateFlow.value.copy(
            isLocked = locked,
            isLockedByLockAll = if (locked && isFromLockAll) true 
                               else if (!locked) false 
                               else stateFlow.value.isLockedByLockAll
        )
        stateFlow.value = newState
        saveState(newState)
    }

    // Set billboard image URI
    suspend fun setBillboardImageUri(instanceId: Int, uriString: String?) {
        val stateFlow = activeMaskStates[instanceId] ?: return
        if (stateFlow.value.billboardImageUri == uriString) return
        
        val newState = stateFlow.value.copy(
            billboardImageUri = uriString, 
            isBillboardVisible = uriString != null
        )
        stateFlow.value = newState
        saveState(newState)
    }

    // Toggle controls visibility
    suspend fun toggleControlsVisibility(instanceId: Int) {
        val stateFlow = activeMaskStates[instanceId] ?: return
        val newState = stateFlow.value.copy(isControlsVisible = !stateFlow.value.isControlsVisible)
        stateFlow.value = newState
        saveState(newState)
    }

    // Delete state
    suspend fun deleteState(instanceId: Int) = withContext(Dispatchers.IO) {
        if (instanceId <= 0) {
            Log.w(TAG, "deleteState called with invalid instanceId: $instanceId")
            return@withContext
        }
        
        try {
            screenMaskDao.deleteById(instanceId)
            activeMaskStates.remove(instanceId)
            Log.d(TAG, "Deleted state for instance $instanceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting state for instance $instanceId", e)
        }
    }

    // Get all active states
    suspend fun getAllStates(): List<ScreenMaskStateEntity> = withContext(Dispatchers.IO) {
        screenMaskDao.getAllStates()
    }

    // Clone state from source instance
    suspend fun cloneState(sourceInstanceId: Int, newInstanceId: Int): ScreenMaskState? = 
        withContext(Dispatchers.IO) {
            val sourceEntity = screenMaskDao.getById(sourceInstanceId) ?: return@withContext null
            val clonedEntity = sourceEntity.copy(
                instanceId = newInstanceId,
                uuid = UUID.randomUUID().toString(),
                x = sourceEntity.x + 25,
                y = sourceEntity.y + 25
            )
            screenMaskDao.insertOrUpdate(clonedEntity)
            mapEntityToState(clonedEntity)
        }

    // Get all active instance IDs
    fun getActiveInstanceIds(): Set<Int> = activeMaskStates.keys

    // Clear instance from memory (but not database)
    fun clearFromMemory(instanceId: Int) {
        activeMaskStates.remove(instanceId)
    }

    // Helper functions
    private fun mapEntityToState(entity: ScreenMaskStateEntity): ScreenMaskState {
        return ScreenMaskState(
            instanceId = entity.instanceId,
            x = entity.x,
            y = entity.y,
            width = entity.width,
            height = entity.height,
            isLocked = entity.isLocked,
            isLockedByLockAll = entity.isLockedByLockAll,
            billboardImageUri = entity.billboardImageUri,
            isBillboardVisible = entity.isBillboardVisible,
            isControlsVisible = entity.isControlsVisible
        )
    }

    private fun mapStateToEntity(state: ScreenMaskState): ScreenMaskStateEntity {
        return ScreenMaskStateEntity(
            instanceId = state.instanceId,
            x = state.x,
            y = state.y,
            width = state.width,
            height = state.height,
            isLocked = state.isLocked,
            isLockedByLockAll = state.isLockedByLockAll,
            billboardImageUri = state.billboardImageUri,
            isBillboardVisible = state.isBillboardVisible,
            isControlsVisible = state.isControlsVisible
        )
    }
}