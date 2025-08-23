// SpotlightRepository.kt
package com.example.purramid.purramidscreenshade.spotlight

import android.util.Log
import com.example.purramid.purramidscreenshade.data.db.SpotlightDao
import com.example.purramid.purramidscreenshade.data.db.SpotlightInstanceEntity
import com.example.purramid.purramidscreenshade.data.db.SpotlightOpeningEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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

    // ===== Instance Management =====
    
    suspend fun createOrActivateInstance(instanceId: Int): SpotlightInstanceEntity = withContext(Dispatchers.IO) {
        var instance = spotlightDao.getInstanceById(instanceId)
        if (instance == null) {
            instance = SpotlightInstanceEntity(
                instanceId = instanceId,
                isActive = true
            )
            spotlightDao.insertOrUpdateInstance(instance)
            Log.d(TAG, "Created new Spotlight instance: $instanceId")
        } else if (!instance.isActive) {
            instance = instance.copy(isActive = true)
            spotlightDao.insertOrUpdateInstance(instance)
            Log.d(TAG, "Reactivated Spotlight instance: $instanceId")
        }
        instance
    }

    suspend fun deactivateInstance(instanceId: Int) = withContext(Dispatchers.IO) {
        spotlightDao.deactivateInstance(instanceId)
    }

    suspend fun getActiveInstances(): List<SpotlightInstanceEntity> = withContext(Dispatchers.IO) {
        spotlightDao.getActiveInstances()
    }

    suspend fun getActiveInstanceCount(): Int = withContext(Dispatchers.IO) {
        spotlightDao.getActiveInstanceCount()
    }

    // ===== Opening Management =====

    fun getOpeningsFlow(instanceId: Int): Flow<List<SpotlightOpening>> {
        return spotlightDao.getOpeningsForInstanceFlow(instanceId)
            .map { entities -> entities.map { mapEntityToOpening(it) } }
    }

    suspend fun getOpenings(instanceId: Int): List<SpotlightOpening> = withContext(Dispatchers.IO) {
        spotlightDao.getOpeningsForInstance(instanceId).map { mapEntityToOpening(it) }
    }

    suspend fun createDefaultOpening(instanceId: Int, screenWidth: Int, screenHeight: Int) = withContext(Dispatchers.IO) {
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

    suspend fun updateOpeningSize(opening: SpotlightOpening) = withContext(Dispatchers.IO) {
        try {
            val entity = mapOpeningToEntity(opening, opening.openingId) // We'll need the instanceId
            spotlightDao.updateOpening(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating opening size", e)
        }
    }

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

    suspend fun toggleOpeningLock(openingId: Int) = withContext(Dispatchers.IO) {
        try {
            val opening = spotlightDao.getOpeningById(openingId)
            if (opening != null) {
                spotlightDao.updateOpeningLockState(openingId, !opening.isLocked)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling lock", e)
        }
    }

    suspend fun setAllOpeningsLockState(instanceId: Int, isLocked: Boolean) = withContext(Dispatchers.IO) {
        try {
            spotlightDao.updateAllOpeningsLockState(instanceId, isLocked)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting all locks", e)
        }
    }

    suspend fun deleteOpening(openingId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get the opening to find its instance
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

    suspend fun deleteInstanceAndOpenings(instanceId: Int) = withContext(Dispatchers.IO) {
        spotlightDao.deleteInstanceAndOpenings(instanceId)
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