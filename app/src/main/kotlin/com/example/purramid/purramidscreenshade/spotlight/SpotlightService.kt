// SpotlightService.kt
package com.example.purramid.purramidscreenshade.spotlight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.purramid.purramidscreenshade.MainActivity
import com.example.purramid.purramidscreenshade.R
import com.example.purramid.purramidscreenshade.di.SpotlightPrefs
import com.example.purramid.purramidscreenshade.instance.InstanceManager
import com.example.purramid.purramidscreenshade.spotlight.repository.SpotlightRepository
import com.example.purramid.purramidscreenshade.spotlight.util.SpotlightMigrationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Define Actions
const val ACTION_START_SPOTLIGHT_SERVICE = "com.example.purramid.spotlight.ACTION_START_SERVICE"
const val ACTION_STOP_SPOTLIGHT_SERVICE = "com.example.purramid.spotlight.ACTION_STOP_SERVICE"
const val ACTION_ADD_NEW_SPOTLIGHT_OPENING = "com.example.purramid.spotlight.ACTION_ADD_NEW_OPENING"
const val ACTION_CLOSE_INSTANCE = "com.example.purramid.spotlight.ACTION_CLOSE_INSTANCE"

@AndroidEntryPoint
class SpotlightService : LifecycleService() {

    @Inject lateinit var windowManager: WindowManager
    @Inject lateinit var instanceManager: InstanceManager
    @Inject lateinit var repository: SpotlightRepository
    @Inject @SpotlightPrefs lateinit var servicePrefs: SharedPreferences
    @Inject lateinit var migrationHelper: SpotlightMigrationHelper

    private var instanceId: Int? = null
    private var spotlightOverlayView: SpotlightView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var stateObserverJob: Job? = null
    private var isForeground = false

    // Lock tracking
    private var isLockAllActive = false
    private val individuallyLockedOpenings = mutableSetOf<Int>()

    companion object {
        private const val TAG = "SpotlightService"
        private const val NOTIFICATION_ID = 3
        private const val CHANNEL_ID = "SpotlightServiceChannel"
        const val MAX_OPENINGS_PER_OVERLAY = 4
        const val KEY_INSTANCE_ID = "spotlight_instance_id"
        const val PREFS_NAME_FOR_ACTIVITY = "spotlight_service_prefs"
        const val KEY_ACTIVE_COUNT_FOR_ACTIVITY = "active_spotlight_count"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()

        lifecycleScope.launch(Dispatchers.IO) {
            migrationHelper.migrateIfNeeded()
            loadAndRestoreSpotlightStates()
        }
    }

    private suspend fun loadAndRestoreSpotlightStates() = withContext(Dispatchers.IO) {
        val persistedStates = repository.getAllStates()
        if (persistedStates.isNotEmpty()) {
            Log.d(TAG, "Found ${persistedStates.size} persisted spotlight states. Restoring...")
            persistedStates.forEach { instance ->
                // Register the existing instance ID
                instanceManager.registerExistingInstance(InstanceManager.SPOTLIGHT, instance.instanceId)

                withContext(Dispatchers.Main) {
                    // This will create the overlay for this instance
                    initializeInstance(instance.instanceId)
                }
            }
        }

        if (instanceId != null) {
            withContext(Dispatchers.Main) {
                startForegroundServiceIfNeeded()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action
        Log.d(TAG, "onStartCommand: Action: $action")

        when (action) {
            ACTION_START_SPOTLIGHT_SERVICE -> {
                if (instanceId == null) {
                    val existingInstanceId = intent?.getIntExtra(KEY_INSTANCE_ID, -1)?.takeIf { it > 0 }
                    if (existingInstanceId != null) {
                        restoreExistingInstance(existingInstanceId)
                    } else {
                        initializeService()
                    }
                }
            }
            ACTION_ADD_NEW_SPOTLIGHT_OPENING -> {
                if (instanceId == null) {
                    initializeService()
                } else {
                    handleAddNewSpotlightOpening()
                }
            }
            ACTION_CLOSE_INSTANCE -> {
                val targetInstanceId = intent?.getIntExtra(KEY_INSTANCE_ID, -1)
                if (targetInstanceId == instanceId) {
                    stopService()
                }
            }
            ACTION_STOP_SPOTLIGHT_SERVICE -> {
                stopService()
            }
        }
        return START_STICKY
    }

    private fun initializeService() {
        instanceId = instanceManager.getNextInstanceId(InstanceManager.SPOTLIGHT)

        if (instanceId == null) {
            Log.e(TAG, "No available instance slots")
            stopSelf()
            return
        }

        Log.d(TAG, "Initializing service with instance ID: $instanceId")
        initializeInstance(instanceId!!)
    }

    private fun restoreExistingInstance(existingInstanceId: Int) {
        val isTracked = instanceManager.getActiveInstanceIds(InstanceManager.SPOTLIGHT)
            .contains(existingInstanceId)

        if (!isTracked) {
            val registered = instanceManager.registerExistingInstance(
                InstanceManager.SPOTLIGHT,
                existingInstanceId
            )
            if (!registered) {
                Log.e(TAG, "Failed to register existing instance $existingInstanceId")
                stopSelf()
                return
            }
        }

        instanceId = existingInstanceId
        Log.d(TAG, "Restoring existing instance ID: $instanceId")
        initializeInstance(instanceId!!)
    }

    private fun initializeInstance(id: Int) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Load state from repository
        lifecycleScope.launch {
            repository.loadState(id, screenWidth, screenHeight)
            withContext(Dispatchers.Main) {
                observeInstanceState(id)
            }
        }

        createOverlayView()
        startForegroundServiceIfNeeded()
        updateActiveInstanceCount()
    }

    private fun observeInstanceState(instanceId: Int) {
        stateObserverJob?.cancel()
        stateObserverJob = lifecycleScope.launch {
            repository.getSpotlightStateFlow(instanceId).collectLatest { state ->
                Log.d(TAG, "State update for instance $instanceId: ${state.openings.size} openings")
                spotlightOverlayView?.updateState(state)
            }
        }
    }

    private fun createOverlayView() {
        if (spotlightOverlayView != null) {
            Log.w(TAG, "Overlay view already exists")
            return
        }

        overlayLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        spotlightOverlayView = SpotlightView(this, null).apply {
            interactionListener = createInteractionListener()
        }

        try {
            windowManager.addView(spotlightOverlayView, overlayLayoutParams)
            Log.d(TAG, "Added overlay view to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view", e)
            stopService()
        }
    }

    private fun createInteractionListener(): SpotlightView.SpotlightInteractionListener {
        return object : SpotlightView.SpotlightInteractionListener {
            override fun onOpeningMoved(openingId: Int, newX: Float, newY: Float) {
                lifecycleScope.launch {
                    repository.updateOpeningPosition(openingId, newX, newY)
                }
            }

            override fun onOpeningResized(opening: SpotlightOpening) {
                lifecycleScope.launch {
                    instanceId?.let { id ->
                        repository.updateOpeningSize(opening, id)
                    }
                }
            }

            override fun onOpeningShapeToggled(openingId: Int) {
                lifecycleScope.launch {
                    repository.toggleOpeningShape(openingId)
                }
            }

            override fun onOpeningLockToggled(openingId: Int) {
                lifecycleScope.launch {
                    val state = repository.getSpotlightStateFlow(instanceId ?: return@launch).value
                    val opening = state.openings.find { it.openingId == openingId }
                    if (opening != null) {
                        if (!opening.isLocked && !isLockAllActive) {
                            individuallyLockedOpenings.add(openingId)
                        } else if (opening.isLocked && !isLockAllActive) {
                            individuallyLockedOpenings.remove(openingId)
                        }
                    }
                    repository.toggleLock(openingId)
                }
            }

            override fun onAllLocksToggled() {
                lifecycleScope.launch {
                    instanceId?.let { id ->
                        isLockAllActive = !isLockAllActive

                        if (isLockAllActive) {
                            repository.setAllLocked(id, true)
                        } else {
                            // Unlock only openings that weren't individually locked
                            val state = repository.getSpotlightStateFlow(id).value
                            state.openings.forEach { opening ->
                                if (!individuallyLockedOpenings.contains(opening.openingId)) {
                                    repository.toggleLock(opening.openingId)
                                }
                            }
                        }
                    }
                }
            }

            override fun onOpeningDeleted(openingId: Int) {
                lifecycleScope.launch {
                    val state = repository.getSpotlightStateFlow(instanceId ?: return@launch).value

                    if (state.openings.size <= 1) {
                        Log.d(TAG, "Last opening deleted, stopping service")
                        stopService()
                    } else {
                        val deleted = repository.deleteOpening(openingId)
                        if (!deleted) {
                            Log.w(TAG, "Could not delete opening")
                        }
                    }
                }
            }

            override fun onAddNewOpeningRequested() {
                handleAddNewSpotlightOpening()
            }

            override fun onControlsToggled() {
                instanceId?.let { id ->
                    repository.toggleControlsVisibility(id)
                }
            }

            override fun onSettingsRequested() {
                openSettingsActivity()
            }
        }
    }

    private fun handleAddNewSpotlightOpening() {
        instanceId?.let { id ->
            val state = repository.getSpotlightStateFlow(id).value
            if (state.openings.size >= MAX_OPENINGS_PER_OVERLAY) {
                Log.w(TAG, "Maximum openings reached")
                return
            }

            val displayMetrics = resources.displayMetrics
            lifecycleScope.launch {
                repository.addOpening(id, displayMetrics.widthPixels, displayMetrics.heightPixels)
            }
        }
    }

    private fun openSettingsActivity() {
        val intent = Intent(this, SpotlightActivity::class.java).apply {
            action = SpotlightActivity.ACTION_SHOW_SPOTLIGHT_SETTINGS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(KEY_INSTANCE_ID, instanceId)
        }
        startActivity(intent)
    }

    private fun updateActiveInstanceCount() {
        val activeCount = instanceManager.getActiveInstanceCount(InstanceManager.SPOTLIGHT)
        servicePrefs.edit().putInt(KEY_ACTIVE_COUNT_FOR_ACTIVITY, activeCount).apply()
    }

    private fun startForegroundServiceIfNeeded() {
        if (isForeground) return

        val notification = createNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
            Log.d(TAG, "Started foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.spotlight_title))
            .setContentText("Spotlight overlay is active")
            .setSmallIcon(R.drawable.ic_spotlight)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spotlight Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun stopService() {
        Log.d(TAG, "Stopping service")

        stateObserverJob?.cancel()

        spotlightOverlayView?.let { view ->
            if (view.isAttachedToWindow) {
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing overlay view", e)
                }
            }
        }
        spotlightOverlayView = null

        instanceId?.let { id ->
            instanceManager.releaseInstanceId(InstanceManager.SPOTLIGHT, id)
            lifecycleScope.launch {
                repository.deactivateInstance(id)
            }
        }

        updateActiveInstanceCount()

        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }

        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved - instance will be preserved")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stateObserverJob?.cancel()

        spotlightOverlayView?.let { view ->
            if (view.isAttachedToWindow) {
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing view on destroy", e)
                }
            }
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}