package com.example.purramid.purramidscreenshade.screen_mask.repository

import android.util.Log
import com.example.purramid.purramidscreenshade.data.db.ScreenMaskDao
import com.example.purramid.purramidscreenshade.data.db.ScreenMaskStateEntity
import com.example.purramid.purramidscreenshade.screen_mask.ScreenMaskState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    // Track which instances have been initialized from database
    private val initializedInstances = mutableSetOf<Int>()

    // Track if repository has been restored from database
    private var isRestored = false

    // Initialize repository by loading all persisted states
    suspend fun initializeRepository() = withContext(Dispatchers.IO) {
        if (isRestored) {
            Log.d(TAG, "Repository already restored, skipping initialization")
            return@withContext
        }

        Log.d(TAG, "Initializing repository from database")
        val persistedStates = screenMaskDao.getAllStates()

        persistedStates.forEach { entity ->
            val state = mapEntityToState(entity)
            activeMaskStates[entity.instanceId] = MutableStateFlow(state)
            initializedInstances.add(entity.instanceId)
            Log.d(TAG, "Restored instance ${entity.instanceId} from database")
        }

        isRestored = true
        Log.d(TAG, "Repository initialization complete. Restored ${persistedStates.size} instances")
    }

    fun getMaskStateFlow(instanceId: Int): StateFlow<ScreenMaskState> {
        return activeMaskStates.computeIfAbsent(instanceId) {
            MutableStateFlow(ScreenMaskState(instanceId = instanceId))
        }.asStateFlow()
    }

    // Load state from database
    suspend fun loadState(instanceId: Int): ScreenMaskState = withContext(Dispatchers.IO) {
        // First check if already in memory
        val existingFlow = activeMaskStates[instanceId]
        if (existingFlow != null && instanceId in initializedInstances) {
            Log.d(TAG, "Instance $instanceId already loaded, returning cached state")
            return@withContext existingFlow.value
        }

        val entity = screenMaskDao.getById(instanceId)
        val state = if (entity != null) {
            mapEntityToState(entity)
        } else {
            // Create default state for new instance
            Log.d(TAG, "Creating new default state for instance $instanceId")
            val defaultState = ScreenMaskState(instanceId = instanceId)
            saveState(defaultState) // Save initial state
            defaultState
        }
        
        // Update in-memory state
        activeMaskStates[instanceId] = MutableStateFlow(state)
        initializedInstances.add(instanceId)
        
        Log.d(TAG, "Loaded state for instance $instanceId: $state")
        state
    }

    // Create default state with proper initialization
    private fun createDefaultState(instanceId: Int): ScreenMaskState {
        return ScreenMaskState(
            instanceId = instanceId,
            x = 0,
            y = 0,
            width = -1, // -1 indicates full screen
            height = -1, // -1 indicates full screen
            isLocked = false,
            isLockedByLockAll = false,
            billboardImageUri = null,
            isBillboardVisible = false,
            isControlsVisible = true
        )
    }

    // Save state to database
    suspend fun saveState(state: ScreenMaskState) = withContext(Dispatchers.IO) {
        if (state.instanceId <= 0) {
            Log.w(TAG, "Attempted to save state with invalid instanceId: ${state.instanceId}")
            return@withContext
        }
        
        try {
            val entity = mapStateToEntity(state)
            screenMaskDao.insertOrUpdate(entity)
            Log.d(TAG, "Saved state for instance ${state.instanceId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving state for instance ${state.instanceId}", e)
        }
    }

    // Update position
    suspend fun updatePosition(instanceId: Int, x: Int, y: Int) {
        val stateFlow = activeMaskStates[instanceId]
        if (stateFlow == null) {
            Log.w(TAG, "Attempted to update position for non-existent instance $instanceId")
            return
        }

        if (stateFlow.value.x == x && stateFlow.value.y == y) return

        val newState = stateFlow.value.copy(x = x, y = y)
        stateFlow.value = newState
        saveState(newState)
        Log.d(TAG, "Updated position for instance $instanceId: x=$x, y=$y")
    }

    // Update size
    suspend fun updateSize(instanceId: Int, width: Int, height: Int) {
        val stateFlow = activeMaskStates[instanceId]
        if (stateFlow == null) {
            Log.w(TAG, "Attempted to update size for non-existent instance $instanceId")
            return
        }

        if (stateFlow.value.width == width && stateFlow.value.height == height) return
        
        val newState = stateFlow.value.copy(width = width, height = height)
        stateFlow.value = newState
        saveState(newState)
        Log.d(TAG, "Updated size for instance $instanceId: width=$width, height=$height")
    }

    // Toggle lock
    suspend fun toggleLock(instanceId: Int) {
        val stateFlow = activeMaskStates[instanceId]
        if (stateFlow == null) {
            Log.w(TAG, "Attempted to toggle lock for non-existent instance $instanceId")
            return
        }

        val newState = stateFlow.value.copy(
            isLocked = !stateFlow.value.isLocked,
            isLockedByLockAll = false // Clear lock all flag when individually toggled
            )
        stateFlow.value = newState
        saveState(newState)
        Log.d(TAG, "Toggled lock for instance $instanceId: isLocked=${newState.isLocked}")
    }

    // Set locked state
    suspend fun setLocked(instanceId: Int, locked: Boolean, isFromLockAll: Boolean = false) {
        val stateFlow = activeMaskStates[instanceId]
        if (stateFlow == null) {
            Log.w(TAG, "Attempted to set lock for non-existent instance $instanceId")
            return
        }

        val newState = stateFlow.value.copy(
            isLocked = locked,
            isLockedByLockAll = if (locked && isFromLockAll) true 
                               else if (!locked) false 
                               else stateFlow.value.isLockedByLockAll
        )
        stateFlow.value = newState
        saveState(newState)
        Log.d(TAG, "Set lock for instance $instanceId: isLocked=$locked, isFromLockAll=$isFromLockAll")
    }

    // Set billboard image URI
    suspend fun setBillboardImageUri(instanceId: Int, uriString: String?) {
        val stateFlow = activeMaskStates[instanceId]
        if (stateFlow == null) {
            Log.w(TAG, "Attempted to set billboard for non-existent instance $instanceId")
            return
        }

        if (stateFlow.value.billboardImageUri == uriString) return
        
        val newState = stateFlow.value.copy(
            billboardImageUri = uriString, 
            isBillboardVisible = uriString != null
        )
        stateFlow.value = newState
        saveState(newState)
        Log.d(TAG, "Set billboard URI for instance $instanceId: $uriString")
    }

    // Toggle controls visibility
    suspend fun toggleControlsVisibility(instanceId: Int) {
        val stateFlow = activeMaskStates[instanceId]
        if (stateFlow == null) {
            Log.w(TAG, "Attempted to toggle controls for non-existent instance $instanceId")
            return
        }

        val newState = stateFlow.value.copy(isControlsVisible = !stateFlow.value.isControlsVisible)
        stateFlow.value = newState
        saveState(newState)
        Log.d(TAG, "Toggled controls visibility for instance $instanceId: ${newState.isControlsVisible}")
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
            initializedInstances.remove(instanceId)
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
            // First try from memory
            val sourceState = activeMaskStates[sourceInstanceId]?.value

            if (sourceState != null) {
                // Clone from in-memory state
                val clonedState = sourceState.copy(
                    instanceId = newInstanceId,
                    x = sourceState.x + 25,
                    y = sourceState.y + 25
                )

                // Save to database and memory
                activeMaskStates[newInstanceId] = MutableStateFlow(clonedState)
                initializedInstances.add(newInstanceId)
                saveState(clonedState)

                Log.d(TAG, "Cloned instance $sourceInstanceId to $newInstanceId from memory")
                return@withContext clonedState
            }

            // Fallback to database
            val sourceEntity = screenMaskDao.getById(sourceInstanceId)
            if (sourceEntity != null) {
                val clonedEntity = sourceEntity.copy(
                    instanceId = newInstanceId,
                    uuid = UUID.randomUUID().toString(),
                    x = sourceEntity.x + 25,
                    y = sourceEntity.y + 25
                )
                screenMaskDao.insertOrUpdate(clonedEntity)

                val clonedState = mapEntityToState(clonedEntity)
                activeMaskStates[newInstanceId] = MutableStateFlow(clonedState)
                initializedInstances.add(newInstanceId)

                Log.d(TAG, "Cloned instance $sourceInstanceId to $newInstanceId from database")
                return@withContext clonedState
            }

            Log.w(TAG, "Failed to clone: source instance $sourceInstanceId not found")
            null
        }

    // Get all active instance IDs
    fun getActiveInstanceIds(): Set<Int> = activeMaskStates.keys

    // Clear instance from memory (but not database)
    fun clearFromMemory(instanceId: Int) {
        activeMaskStates.remove(instanceId)
    }

    // Get all active instance IDs
    fun getActiveInstanceIds(): Set<Int> = activeMaskStates.keys.toSet()

    // Check if an instance exists in repository
    fun hasInstance(instanceId: Int): Boolean = instanceId in activeMaskStates

    // Clear instance from memory (but not database)
    fun clearFromMemory(instanceId: Int) {
        activeMaskStates.remove(instanceId)
        initializedInstances.remove(instanceId)
        Log.d(TAG, "Cleared instance $instanceId from memory")
    }

    // Clear all instances from memory (for testing or reset)
    fun clearAllFromMemory() {
        activeMaskStates.clear()
        initializedInstances.clear()
        isRestored = false
        Log.d(TAG, "Cleared all instances from memory")
    }

    // Get current state snapshot for an instance
    fun getCurrentState(instanceId: Int): ScreenMaskState? {
        return activeMaskStates[instanceId]?.value
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
        // Try to preserve existing UUID if entity exists
        val existingUuid = runCatching {
            screenMaskDao.getById(state.instanceId)?.uuid
        }.getOrNull()

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