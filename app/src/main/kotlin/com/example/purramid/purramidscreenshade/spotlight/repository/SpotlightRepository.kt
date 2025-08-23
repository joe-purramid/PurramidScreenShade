// SpotlightRepository.kt
package com.example.purramid.purramidscreenshade.spotlight.repository

import android.util.Log
import com.example.purramid.purramidscreenshade.data.db.SpotlightDao
import com.example.purramid.purramidscreenshade.data.db.SpotlightInstanceEntity
import com.example.purramid.purramidscreenshade.data.db.SpotlightOpeningEntity
import com.example.purramid.purramidscreenshade.spotlight.SpotlightOpening
import com.example.purramid.purramidscreenshade.spotlight.SpotlightUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.maxOf

@Singleton
class SpotlightRepository @Inject constructor(
    private val spotlightDao: SpotlightDao
) {
    companion object {
        private const val TAG = "SpotlightRepository"
        private const val DEFAULT_RADIUS = 125f
    }

    // State management per instance (like ScreenMaskRepository)
    private val instanceStates = ConcurrentHashMap<Int, MutableStateFlow<SpotlightUiState>>()

    /**
     * Get or create StateFlow for an instance
     */
    fun getSpotlightStateFlow(instanceId: Int): StateFlow<SpotlightUiState> {
        return instanceStates.getOrPut(instanceId) {
            MutableStateFlow(SpotlightUiState(instanceId = instanceId))
        }.asStateFlow()
    }

    /**
     * Load state for an instance (creates if doesn't exist)
     */
    suspend fun loadState(instanceId: Int, screenWidth: Int = 1920, screenHeight: Int = 1080) = withContext(Dispatchers.IO) {
        try {
            // Ensure instance exists
            var instance = spotlightDao.getInstanceById(instanceId)
            if (instance == null) {
                instance = SpotlightInstanceEntity(
                    instanceId = instanceId,
                    isActive = true
                )
                spotlightDao.insertOrUpdateInstance(instance)
                Log.d(TAG, "Created new Spotlight instance: $instanceId")
            }

            // Load openings
            val openings = spotlightDao.getOpeningsForInstance(instanceId)
            if (openings.isEmpty()) {
                // Create default opening
                createDefaultOpening(instanceId, screenWidth, screenHeight)
            } else {
                // Update state with existing openings
                updateStateFromOpenings(instanceId, openings)
            }

            // Start observing database changes
            observeOpenings(instanceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading state for instance $instanceId", e)
            updateState(instanceId) { it.copy(error = "Failed to load state") }
        }
    }

    /**
     * Clone state from one instance to another
     */
    suspend fun cloneState(sourceId: Int, targetId: Int) = withContext(Dispatchers.IO) {
        try {
            val sourceOpenings = spotlightDao.getOpeningsForInstance(sourceId)

            // Create target instance
            val targetInstance = SpotlightInstanceEntity(
                instanceId = targetId,
                isActive = true
            )
            spotlightDao.insertOrUpdateInstance(targetInstance)

            // Clone openings
            sourceOpenings.forEach { opening ->
                val clonedOpening = opening.copy(
                    openingId = 0, // Auto-generate new ID
                    instanceId = targetId
                )
                spotlightDao.insertOpening(clonedOpening)
            }

            Log.d(TAG, "Cloned state from instance $sourceId to $targetId")
        } catch (e: Exception) {
            Log.e(TAG, "Error cloning state", e)
        }
    }

    /**
     * Observe database changes for an instance
     */
    private suspend fun observeOpenings(instanceId: Int) {
        spotlightDao.getOpeningsForInstanceFlow(instanceId).collect { openingEntities ->
            updateStateFromOpenings(instanceId, openingEntities)
        }
    }

    private fun updateStateFromOpenings(instanceId: Int, openingEntities: List<SpotlightOpeningEntity>) {
        val openings = openingEntities.map { mapEntityToOpening(it) }
        updateState(instanceId) { currentState ->
            currentState.copy(
                openings = openings,
                isAnyLocked = openings.any { it.isLocked },
                areAllLocked = openings.isNotEmpty() && openings.all { it.isLocked },
                canAddMore = openings.size < SpotlightUiState.MAX_OPENINGS,
                isLoading = false,
                error = null
            )
        }
    }

    /**
     * Update state using a transformation function
     */
    private fun updateState(instanceId: Int, transform: (SpotlightUiState) -> SpotlightUiState) {
        val stateFlow = instanceStates[instanceId] ?: return
        stateFlow.value = transform(stateFlow.value)
    }

    /**
     * Get all active instance IDs
     */
    suspend fun getActiveInstanceIds(): List<Int> = withContext(Dispatchers.IO) {
        spotlightDao.getActiveInstances().map { it.instanceId }
    }

    /**
     * Get all active instances
     */
    suspend fun getActiveInstances(): List<SpotlightInstanceEntity> = withContext(Dispatchers.IO) {
        spotlightDao.getActiveInstances()
    }

    /**
     * Get all states (for restoration)
     */
    suspend fun getAllStates(): List<SpotlightInstanceEntity> = withContext(Dispatchers.IO) {
        spotlightDao.getActiveInstances()
    }

    /**
     * Create default opening for an instance
     */
    private suspend fun createDefaultOpening(instanceId: Int, screenWidth: Int, screenHeight: Int) {
        val defaultOpening = SpotlightOpeningEntity(
            instanceId = instanceId,
            centerX = screenWidth / 2f,
            centerY = screenHeight / 2f,
            radius = DEFAULT_RADIUS,
            width = DEFAULT_RADIUS * 2,
            height = DEFAULT_RADIUS * 2,
            size = DEFAULT_RADIUS * 2,
            shape = SpotlightOpening.Shape.CIRCLE.name,
            isLocked = false,
            displayOrder = 0
        )
        spotlightDao.insertOpening(defaultOpening)
    }

    /**
     * Add a new opening to an instance
     */
    suspend fun addOpening(instanceId: Int, screenWidth: Int, screenHeight: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val existingCount = spotlightDao.getOpeningCountForInstance(instanceId)
            if (existingCount >= SpotlightUiState.MAX_OPENINGS) {
                return@withContext false
            }

            val offsetX = 50f * existingCount
            val offsetY = 50f * existingCount
            val centerX = (screenWidth / 2f + offsetX).coerceIn(DEFAULT_RADIUS, screenWidth - DEFAULT_RADIUS)
            val centerY = (screenHeight / 2f + offsetY).coerceIn(DEFAULT_RADIUS, screenHeight - DEFAULT_RADIUS)

            val newOpening = SpotlightOpeningEntity(
                instanceId = instanceId,
                centerX = centerX,
                centerY = centerY,
                radius = DEFAULT_RADIUS,
                width = DEFAULT_RADIUS * 2,
                height = DEFAULT_RADIUS * 2,
                size = DEFAULT_RADIUS * 2,
                shape = SpotlightOpening.Shape.CIRCLE.name,
                isLocked = false,
                displayOrder = existingCount
            )

            spotlightDao.insertOpening(newOpening)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding opening", e)
            false
        }
    }

    /**
     * Update opening position
     */
    suspend fun updateOpeningPosition(openingId: Int, newX: Float, newY: Float) = withContext(Dispatchers.IO) {
        try {
            val opening = spotlightDao.getOpeningById(openingId)
            if (opening != null && !opening.isLocked) {
                val updated = opening.copy(centerX = newX, centerY = newY)
                spotlightDao.updateOpening(updated)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating opening position", e)
        }
    }

    /**
     * Update opening size
     */
    suspend fun updateOpeningSize(opening: SpotlightOpening, instanceId: Int) = withContext(Dispatchers.IO) {
        try {
            val entity = mapOpeningToEntity(opening, instanceId)
            spotlightDao.updateOpening(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating opening size", e)
        }
    }

    /**
     * Toggle opening shape
     */
    suspend fun toggleOpeningShape(openingId: Int) = withContext(Dispatchers.IO) {
        try {
            val opening = spotlightDao.getOpeningById(openingId)
            if (opening != null && !opening.isLocked) {
                val currentShape = SpotlightOpening.Shape.valueOf(opening.shape)
                val newShape = when (currentShape) {
                    SpotlightOpening.Shape.CIRCLE -> SpotlightOpening.Shape.SQUARE
                    SpotlightOpening.Shape.SQUARE -> SpotlightOpening.Shape.CIRCLE
                    SpotlightOpening.Shape.OVAL -> SpotlightOpening.Shape.RECTANGLE
                    SpotlightOpening.Shape.RECTANGLE -> SpotlightOpening.Shape.OVAL
                }

                val updated = when (newShape) {
                    SpotlightOpening.Shape.CIRCLE, SpotlightOpening.Shape.SQUARE -> {
                        val avgDim = (opening.width + opening.height) / 2f
                        opening.copy(
                            shape = newShape.name,
                            radius = avgDim / 2f,
                            width = avgDim,
                            height = avgDim,
                            size = avgDim
                        )
                    }
                    SpotlightOpening.Shape.OVAL, SpotlightOpening.Shape.RECTANGLE -> {
                        opening.copy(
                            shape = newShape.name,
                            radius = maxOf(opening.width, opening.height) / 2f,
                            size = maxOf(opening.width, opening.height)
                        )
                    }
                }

                spotlightDao.updateOpening(updated)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling shape", e)
        }
    }

    /**
     * Toggle lock state for an opening
     */
    suspend fun toggleLock(openingId: Int) = withContext(Dispatchers.IO) {
        try {
            val opening = spotlightDao.getOpeningById(openingId)
            if (opening != null) {
                spotlightDao.updateOpeningLockState(openingId, !opening.isLocked)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling lock", e)
        }
    }

    /**
     * Set lock state for all openings in an instance
     */
    suspend fun setAllLocked(instanceId: Int, isLocked: Boolean) = withContext(Dispatchers.IO) {
        try {
            spotlightDao.updateAllOpeningsLockState(instanceId, isLocked)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting all locks", e)
        }
    }

    /**
     * Toggle controls visibility
     */
    fun toggleControlsVisibility(instanceId: Int) {
        updateState(instanceId) { it.copy(showControls = !it.showControls) }
    }

    /**
     * Delete an opening
     */
    suspend fun deleteOpening(openingId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val opening = spotlightDao.getOpeningById(openingId) ?: return@withContext false
            val count = spotlightDao.getOpeningCountForInstance(opening.instanceId)

            if (count > 1) {
                spotlightDao.deleteOpening(openingId)
                true
            } else {
                false // Can't delete last opening
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting opening", e)
            false
        }
    }

    /**
     * Delete state for an instance
     */
    suspend fun deleteState(instanceId: Int) = withContext(Dispatchers.IO) {
        try {
            spotlightDao.deleteInstanceAndOpenings(instanceId)
            instanceStates.remove(instanceId)
            Log.d(TAG, "Deleted state for instance $instanceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting state", e)
        }
    }

    /**
     * Deactivate an instance
     */
    suspend fun deactivateInstance(instanceId: Int) = withContext(Dispatchers.IO) {
        try {
            spotlightDao.deactivateInstance(instanceId)
            Log.d(TAG, "Deactivated instance $instanceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating instance", e)
        }
    }

    // ===== Mapping Functions =====

    private fun mapEntityToOpening(entity: SpotlightOpeningEntity): SpotlightOpening {
        return SpotlightOpening(
            openingId = entity.openingId,
            centerX = entity.centerX,
            centerY = entity.centerY,
            radius = entity.radius,
            width = entity.width,
            height = entity.height,
            size = entity.size,
            shape = try {
                SpotlightOpening.Shape.valueOf(entity.shape)
            } catch (e: Exception) {
                SpotlightOpening.Shape.CIRCLE
            },
            isLocked = entity.isLocked,
            displayOrder = entity.displayOrder
        )
    }

    private fun mapOpeningToEntity(opening: SpotlightOpening, instanceId: Int): SpotlightOpeningEntity {
        return SpotlightOpeningEntity(
            openingId = opening.openingId,
            instanceId = instanceId,
            centerX = opening.centerX,
            centerY = opening.centerY,
            radius = opening.radius,
            width = opening.width,
            height = opening.height,
            size = opening.size,
            shape = opening.shape.name,
            isLocked = opening.isLocked,
            displayOrder = opening.displayOrder
        )
    }
}