// InstanceManager.kt

package com.example.purramid.purramidtime.instance

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class InstanceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences("instance_manager_prefs", Context.MODE_PRIVATE)
    private val activeInstances = ConcurrentHashMap<String, MutableSet<Int>>()

    companion object {
        const val SCREEN_MASK = "screen_mask"
        const val SPOTLIGHT = "spotlight"
    }

    init {
        // Load persisted instance states
        loadPersistedStates()
    }

    private fun loadPersistedStates() {
        for (appIntent in listOf(SCREEN_MASK, SPOTLIGHT)) {
            val activeIds = prefs.getStringSet("${appIntent}_active_ids", emptySet())
                ?.mapNotNull { it.toIntOrNull() }
                ?.toMutableSet() ?: mutableSetOf()
            activeInstances[appIntent] = activeIds
        }
    }

    private fun saveState(appIntent: String) {
        val ids = activeInstances[appIntent]?.map { it.toString() }?.toSet() ?: emptySet()
        prefs.edit { putStringSet("${appIntent}_active_ids", ids) }
    }

    fun getNextInstanceId(appIntent: String): Int? {
        val maxInstances = 4

        // Synchronize access to the MutableSet
        synchronized(activeInstances) {
            val active = activeInstances.getOrPut(appIntent) {
                Collections.synchronizedSet(mutableSetOf())
            }

            // Find the lowest available ID
            for (id in 1..maxInstances) {
                if (!active.contains(id)) {
                    active.add(id)
                    saveState(appIntent) // This should also be thread-safe
                    return id
                }
            }
        }

        return null // No available slots
    }

    fun releaseInstanceId(appIntent: String, instanceId: Int) {
        activeInstances[appIntent]?.remove(instanceId)
        saveState(appIntent) // Persist the change
    }

    fun getActiveInstanceCount(appIntent: String): Int {
        return activeInstances[appIntent]?.size ?: 0
    }

    fun getActiveInstanceIds(appIntent: String): Set<Int> {
        return activeInstances[appIntent]?.toSet() ?: emptySet()
    }

    // Helper method to register existing instances during restoration
    fun registerExistingInstance(appIntent: String, instanceId: Int): Boolean {
        val maxInstances = 4

        if (instanceId in 1..maxInstances) {
            val active = activeInstances.getOrPut(appIntent) { mutableSetOf() }
            val added = active.add(instanceId)
            if (added) {
                saveState(appIntent)
            }
            return added
        }
        return false
    }
}