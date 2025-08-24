// ScreenServiceMask.kt
package com.example.purramid.purramidscreenshade.screen_mask

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.animation.ObjectAnimator
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.purramid.purramidscreenshade.R
import com.example.purramid.purramidscreenshade.instance.InstanceManager
import com.example.purramid.purramidscreenshade.screen_mask.repository.ScreenMaskRepository
import com.example.purramid.purramidscreenshade.screen_mask.ui.MaskView
import com.example.purramid.purramidscreenshade.util.cleanup
import com.example.purramid.purramidscreenshade.util.dpToPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.util.concurrent.ConcurrentHashMap

// Actions for ScreenMaskService
const val ACTION_START_SCREEN_MASK = "com.example.purramid.screen_mask.ACTION_START"
const val ACTION_STOP_SCREEN_MASK_SERVICE = "com.example.purramid.screen_mask.ACTION_STOP_SERVICE"
const val ACTION_ADD_NEW_MASK_INSTANCE = "com.example.purramid.screen_mask.ACTION_ADD_NEW_INSTANCE"
const val ACTION_REQUEST_IMAGE_CHOOSER = "com.example.purramid.screen_mask.ACTION_REQUEST_IMAGE_CHOOSER" // Service sends to Activity
const val ACTION_TOGGLE_LOCK = "com.example.purramid.screen_mask.ACTION_TOGGLE_LOCK"
const val ACTION_TOGGLE_LOCK_ALL = "com.example.purramid.screen_mask.ACTION_TOGGLE_LOCK_ALL"
const val ACTION_BILLBOARD_IMAGE_SELECTED = "com.example.purramid.screen_mask.ACTION_BILLBOARD_IMAGE_SELECTED" // Activity sends to Service
const val ACTION_HIGHLIGHT = "com.example.purramid.screen_mask.ACTION_HIGHLIGHT"
const val ACTION_REMOVE_HIGHLIGHT = "com.example.purramid.screen_mask.ACTION_REMOVE_HIGHLIGHT"
const val EXTRA_MASK_INSTANCE_ID = "screenMaskInstanceId"
const val EXTRA_IMAGE_URI = "com.example.purramid.screen_mask.EXTRA_IMAGE_URI"

@AndroidEntryPoint
class ScreenMaskService : LifecycleService() {

    // Job for managing coroutines
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    @Inject lateinit var windowManager: WindowManager
    @Inject lateinit var instanceManager: InstanceManager
    @Inject lateinit var repository: ScreenMaskRepository
    @Inject @ScreenMaskPrefs lateinit var servicePrefs: SharedPreferences

    private val activeMaskViews = ConcurrentHashMap<Int, MaskView>()
    private val maskLayoutParams = ConcurrentHashMap<Int, WindowManager.LayoutParams>()
    private val stateObserverJobs = ConcurrentHashMap<Int, Job>()

    private var isLockAllActive = false
    private var isForeground = false
    private var imageChooserTargetInstanceId: Int? = null

    companion object {
        private const val TAG = "ScreenMaskService"
        private const val NOTIFICATION_ID = 6
        private const val CHANNEL_ID = "ScreenMaskServiceChannel"
        const val MAX_MASKS = 4 // Shared constant for max masks
        const val PREFS_NAME_FOR_ACTIVITY = "com.example.purramid.purramidscreenshade.screen_mask.APP_PREFERENCES"
        const val KEY_ACTIVE_COUNT = "SCREEN_MASK_ACTIVE_INSTANCE_COUNT"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
        loadAndRestoreMaskStates() // Attempt to restore any previously active masks
    }

    private fun updateActiveInstanceCountInPrefs() {
        servicePrefs.edit { putInt(KEY_ACTIVE_COUNT, activeMaskViews.size) }
        Log.d(TAG, "Updated active ScreenMask count: ${activeMaskViews.size}")
    }

    private fun loadAndRestoreMaskStates() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Initialize repository first
            repository.initializeRepository()

            // Get all persisted states
            val persistedStates = repository.getAllStates()
            if (persistedStates.isNotEmpty()) {
                Log.d(TAG, "Found ${persistedStates.size} persisted screen mask states. Restoring...")
                persistedStates.forEach { entity ->
                    // Register the existing instance ID
                    instanceManager.registerExistingInstance(InstanceManager.SCREEN_MASK, entity.instanceId)

                    withContext(Dispatchers.Main) {
                        initializeMaskInstance(entity.instanceId)
                    }
                }
            }

            if (activeMaskViews.isNotEmpty()) {
                startForegroundServiceIfNeeded()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        var action = intent?.action
        Log.d(TAG, "onStartCommand: Action: $action")

        when (action) {
            ACTION_START_SCREEN_MASK -> {
                startForegroundServiceIfNeeded()
                if (activeMaskViews.isEmpty() && servicePrefs.getInt(KEY_ACTIVE_COUNT, 0) == 0) {
                    Log.d(TAG, "No active masks (from map and prefs), adding a new default one.")
                    handleAddNewMaskInstance()
                }
            }
            ACTION_ADD_NEW_MASK_INSTANCE -> {
                startForegroundServiceIfNeeded()
                val sourceId = intent.getIntExtra(EXTRA_MASK_INSTANCE_ID, -1)
                handleAddNewMaskInstance(if (sourceId != -1) sourceId else null)
            }
            ACTION_STOP_SCREEN_MASK_SERVICE -> {
                stopAllInstancesAndService()
            }
            ACTION_TOGGLE_LOCK -> {
                val instanceId = intent?.getIntExtra(EXTRA_MASK_INSTANCE_ID, -1)
                if (instanceId != -1) {
                    lifecycleScope.launch {
                        repository.toggleLock(instanceId)
                    }
                }
            }
            ACTION_REQUEST_IMAGE_CHOOSER -> {
                val instanceId = intent?.getIntExtra(EXTRA_MASK_INSTANCE_ID, -1)
                if (instanceId != -1) {
                    imageChooserTargetInstanceId = instanceId
                    val activityIntent = Intent(this, ScreenMaskActivity::class.java).apply {
                        action = ScreenMaskActivity.ACTION_LAUNCH_IMAGE_CHOOSER_FROM_SERVICE
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(activityIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not start ScreenMaskActivity for image chooser", e)
                        activeMaskViews[instanceId]?.showMessage(getString(R.string.cannot_open_image_picker))
                        imageChooserTargetInstanceId = null
                    }
                }
            }
            ACTION_TOGGLE_LOCK_ALL -> {
                // Trigger the Lock All functionality
                handleLockAllToggle()
            }
            ACTION_BILLBOARD_IMAGE_SELECTED -> {
                val uriString = intent?.getStringExtra(EXTRA_IMAGE_URI)
                val targetId = imageChooserTargetInstanceId ?: intent?.getIntExtra(EXTRA_MASK_INSTANCE_ID, -1) ?: -1

                if (targetId != -1) {
                    lifecycleScope.launch {
                        repository.setBillboardImageUri(targetId, uriString)
                    }
                }
                imageChooserTargetInstanceId = null
            }
            ACTION_HIGHLIGHT -> {
                val instanceId = intent?.getIntExtra(EXTRA_MASK_INSTANCE_ID, -1)
                if (instanceId != -1) {
                    highlightMask(instanceId, true)
                }
            }
            ACTION_REMOVE_HIGHLIGHT -> {
                val instanceId = intent?.getIntExtra(EXTRA_MASK_INSTANCE_ID, -1)
                if (instanceId != -1) {
                    highlightMask(instanceId, false)
                }
            }
        }
        return START_STICKY
    }

    private fun handleLockAllToggle() {
        lifecycleScope.launch {
            if (!isLockAllActive) {
                // Lock all masks
                repository.getActiveInstanceIds().forEach { instanceId ->
                    repository.setLocked(instanceId, true, isFromLockAll = true)
                }
                isLockAllActive = true
            } else {
                // Unlock all masks that were locked by Lock All
                repository.getActiveInstanceIds().forEach { instanceId ->
                    val state = repository.getMaskStateFlow(instanceId).value
                    if (state.isLockedByLockAll) {
                        repository.setLocked(instanceId, false)
                    }
                }
                isLockAllActive = false
            }

            // Update all mask views to reflect new button states
            withContext(Dispatchers.Main) {
                activeMaskViews.values.forEach { maskView ->
                    maskView.updateLockButtonState()
                }
            }
        }
    }

    fun onSettingsRequested(id: Int) {
        // Highlight the mask that requested settings
        highlightMask(id, true)

        // Store which instance requested settings
        val intent = Intent(this@ScreenMaskService, ScreenMaskActivity::class.java).apply {
            putExtra(EXTRA_MASK_INSTANCE_ID, id)
            putExtra("EXTRA_SETTINGS_BUTTON_SCREEN_LOCATION", getSettingsButtonLocation(id))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Add animation flags for smooth transition
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // Create activity options for custom animation
        val options = ActivityOptions.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )

        startActivity(intent, options.toBundle())
    }

    private fun highlightMaskAnimated(instanceId: Int?, highlight: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            activeMaskViews[instanceId]?.let { maskView ->
                if (highlight) {
                    // Animate highlight appearance
                    val fadeIn = ObjectAnimator.ofFloat(maskView, "alpha", 0.7f, 1.0f).apply {
                        duration = 200
                        interpolator = AccelerateDecelerateInterpolator()
                    }
                    fadeIn.start()
                }
                maskView.setHighlighted(highlight)
            }
        }
    }

    private fun getSettingsButtonLocation(instanceId: Int): IntArray {
        val maskView = activeMaskViews[instanceId] ?: return intArrayOf(0, 0)
        val location = IntArray(2)
        maskView.settingsButton.getLocationOnScreen(location)
        return location
    }

    private fun handleAddNewMaskInstance(sourceInstanceId: Int? = null) {
        val activeCount = instanceManager.getActiveInstanceCount(InstanceManager.SCREEN_MASK)
        if (activeCount >= MAX_MASKS) {
            Log.w(TAG, "Maximum number of masks ($MAX_MASKS) reached.")
            return
        }

        val newInstanceId = instanceManager.getNextInstanceId(InstanceManager.SCREEN_MASK)
        if (newInstanceId == null) {
            Log.w(TAG, "No available instance slots for Screen Mask")
            return
        }

        Log.d(TAG, "Adding new mask instance with ID: $newInstanceId")

        lifecycleScope.launch {
            // Clone state if source provided
            if (sourceInstanceId != null) {
                repository.cloneState(sourceInstanceId, newInstanceId)
            } else {
                // Create default state
                repository.loadState(newInstanceId)
            }

            withContext(Dispatchers.Main) {
                initializeMaskInstance(newInstanceId)
                updateActiveInstanceCountInPrefs()
                startForegroundServiceIfNeeded()
            }
        }
    }

    fun highlightMask(instanceId: Int?, highlight: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            activeMaskViews[instanceId]?.setHighlighted(highlight)
        }
    }

    private fun initializeMaskInstance(instanceId: Int) {
        // Load initial state
        lifecycleScope.launch {
            repository.loadState(instanceId)
            observeInstanceState(instanceId)
        }
    }

    private fun observeInstanceState(instanceId: Int) {
        stateObserverJobs[instanceId]?.cancel()
        stateObserverJobs[instanceId] = lifecycleScope.launch {
            repository.getMaskStateFlow(instanceId).collectLatest { state ->
                Log.d(TAG, "State update for Mask ID $instanceId")
                withContext(Dispatchers.Main) {
                    addOrUpdateMaskView(instanceId, state)
                }
            }
        }
        Log.d(TAG, "Started observing state for Mask ID $instanceId")
    }

    private fun addOrUpdateMaskView(instanceId: Int, state: ScreenMaskState) {
        var maskView = activeMaskViews[instanceId]
        var params = maskLayoutParams[instanceId]

        if (maskView == null) {
            Log.d(TAG, "Creating new MaskView UI for ID: $instanceId")
            params = createDefaultLayoutParams(state)
            maskView = MaskView(this@ScreenMaskService, instanceId = instanceId).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                interactionListener = createMaskInteractionListener(instanceId)
            }
            activeMaskViews[instanceId] = maskView
            maskLayoutParams[instanceId] = params

            try {
                windowManager.addView(maskView, params)
                Log.d(TAG, "Added MaskView ID $instanceId to WindowManager.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding MaskView ID $instanceId to WindowManager", e)
                activeMaskViews.remove(instanceId)
                maskLayoutParams.remove(instanceId)
                stateObserverJobs[instanceId]?.cancel()
                lifecycleScope.launch {
                    repository.deleteState(instanceId)
                }
                updateActiveInstanceCountInPrefs()
                return
            }
        }

        maskView.updateState(state)

        // Update WindowManager.LayoutParams if position/size changed in state
        var layoutNeedsUpdate = false
        if (params!!.x != state.x || params.y != state.y) {
            params.x = state.x
            params.y = state.y
            layoutNeedsUpdate = true
        }
        val newWidth = if (state.width <= 0) WindowManager.LayoutParams.MATCH_PARENT else state.width
        val newHeight = if (state.height <= 0) WindowManager.LayoutParams.MATCH_PARENT else state.height
        if (params.width != newWidth || params.height != newHeight) {
            params.width = newWidth
            params.height = newHeight
            layoutNeedsUpdate = true
        }

        if (layoutNeedsUpdate && maskView.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(maskView, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating WindowManager layout for Mask ID $instanceId", e)
            }
        }
    }

    private fun removeMaskInstance(instanceId: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Removing Mask instance ID: $instanceId")

            instanceManager.releaseInstanceId(InstanceManager.SCREEN_MASK, instanceId)

            stateObserverJobs[instanceId]?.cancel()
            stateObserverJobs.remove(instanceId)

            removeViewSafely(instanceId)

            lifecycleScope.launch(Dispatchers.IO) {
                repository.deleteState(instanceId)
            }

            updateActiveInstanceCountInPrefs()

            if (activeMaskViews.isEmpty()) {
                Log.d(TAG, "No active masks left, stopping service.")
                stopService()
            }
        }
    }

    private fun createMaskInteractionListener(instanceId: Int): MaskView.InteractionListener {
        return object : MaskView.InteractionListener {
            override fun onMaskMoved(instanceId: Int, x: Int, y: Int) {
                lifecycleScope.launch {
                    repository.updatePosition(instanceId, x, y)
                }
            }

            override fun onMaskResized(instanceId: Int, width: Int, height: Int) {
                lifecycleScope.launch {
                    repository.updateSize(instanceId, width, height)
                }
            }

            override fun onSettingsRequested(instanceId: Int) {
                this@ScreenMaskService.onSettingsRequested(instanceId)
            }

            override fun onLockToggled(instanceId: Int) {
                lifecycleScope.launch {
                    repository.toggleLock(instanceId)
                }
            }

            override fun onLockAllToggled(instanceId: Int) {
                handleLockAllToggle()
            }

            override fun onCloseRequested(instanceId: Int) {
                removeMaskInstance(instanceId)
            }

            override fun onBillboardTapped(instanceId: Int) {
                imageChooserTargetInstanceId = instanceId
                val activityIntent = Intent(this@ScreenMaskService, ScreenMaskActivity::class.java).apply {
                    action = ScreenMaskActivity.ACTION_LAUNCH_IMAGE_CHOOSER_FROM_SERVICE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(activityIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not start ScreenMaskActivity for image chooser", e)
                    activeMaskViews[instanceId]?.showMessage(getString(R.string.cannot_open_image_picker))
                    imageChooserTargetInstanceId = null
                }
            }

            override fun onColorChangeRequested(instanceId: Int) {
                Log.d(TAG, "Color change requested for $instanceId (currently no-op for opaque masks)")
            }

            override fun onControlsToggled(instanceId: Int) {
                lifecycleScope.launch {
                    repository.toggleControlsVisibility(instanceId)
                }
            }
        }
    }

    private fun createDefaultLayoutParams(initialState: ScreenMaskState): WindowManager.LayoutParams {
        val screenWidth: Int
        val screenHeight: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            // Fallback for older versions
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
        }

        // Default to full screen if width/height are invalid or 0
        val width = if (initialState.width <= 0) screenWidth else initialState.width
        val height = if (initialState.height <= 0) screenHeight else initialState.height

        // Ensure x and y are within bounds if dimensions are smaller than screen
        // If it's full screen, x/y should be 0.
        val x = if (width >= screenWidth) 0 else initialState.x.coerceIn(0, screenWidth - width)
        val y = if (height >= screenHeight) 0 else initialState.y.coerceIn(0, screenHeight - height)


        return WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun stopAllInstancesAndService() {
        Log.d(TAG, "Stopping all instances and Screen Mask service.")
        activeMaskViews.keys.toList().forEach { id ->
            removeMaskInstance(id)
        }
        if (activeMaskViews.isEmpty()) {
            stopService()
        }
    }

    private fun stopService() {
        Log.d(TAG, "stopService called for Screen Mask")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isForeground = false
    }

    private fun startForegroundServiceIfNeeded() {
        if (isForeground) return
        val notification = createNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
            Log.d(TAG, "ScreenMaskService started in foreground.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service for ScreenMask", e)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, ScreenMaskActivity::class.java).apply {
            action = ACTION_START_SCREEN_MASK // Action to re-evaluate if service should show UI
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Mask Active") // More specific title
            .setContentText("Tap to manage screen masks.") // More specific text
            .setSmallIcon(R.drawable.ic_mask_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Mask Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")

        // Cancel service scope first
        serviceScope.cancel()
        serviceJob.cancel()

        // Cancel all coroutines
        stateObserverJobs.values.forEach { it.cancel() }
        stateObserverJobs.clear()

        // Cancel all state observer jobs
        stateObserverJobs.values.forEach { it.cancel() }
        stateObserverJobs.clear()

        // Remove all views safely
        activeMaskViews.keys.toList().forEach { id ->
            removeViewSafely(id)
        }

        // Clear collections
        activeMaskViews.clear()
        maskLayoutParams.clear()

        super.onDestroy()
    }

    private fun removeViewSafely(id: Int) {
        try {
            val view = activeMaskViews.remove(id)
            maskLayoutParams.remove(id)

            view?.let {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
                // Trigger cleanup in the view
                it.cleanup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing view safely for $id", e)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}