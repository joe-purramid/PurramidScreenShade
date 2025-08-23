// SpotlightActivity.kt
package com.example.purramid.purramidscreenshade.spotlight

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.purramid.purramidscreenshade.R
import com.example.purramid.purramidscreenshade.databinding.ActivitySpotlightBinding
import com.example.purramid.purramidscreenshade.instance.InstanceManager
import com.example.purramid.purramidscreenshade.spotlight.repository.SpotlightRepository
import com.example.purramid.purramidscreenshade.spotlight.ui.SpotlightSettingsFragment
import com.example.purramid.purramidscreenshade.spotlight.viewmodel.SpotlightViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SpotlightActivity : AppCompatActivity() {

    @Inject lateinit var instanceManager: InstanceManager
    @Inject lateinit var repository: SpotlightRepository

    private lateinit var binding: ActivitySpotlightBinding
    private val viewModel: SpotlightViewModel by viewModels()
    private val activityScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "SpotlightActivity"
        const val ACTION_SHOW_SPOTLIGHT_SETTINGS = "com.example.purramid.spotlight.ACTION_SHOW_SETTINGS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set instance ID in intent extras BEFORE super.onCreate() for SavedStateHandle
        val requestingInstanceId = intent.getIntExtra(SpotlightService.KEY_INSTANCE_ID, -1)
        if (requestingInstanceId != -1) {
            intent.putExtra(SpotlightViewModel.KEY_INSTANCE_ID, requestingInstanceId)
        }

        super.onCreate(savedInstanceState)
        binding = ActivitySpotlightBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate - Intent Action: ${intent.action}")

        handleIntent(intent)
    }

    private fun handleIntent(currentIntent: Intent?) {
        Log.d(TAG, "handleIntent - Action: ${currentIntent?.action}")
        if (currentIntent?.action == ACTION_SHOW_SPOTLIGHT_SETTINGS) {
            showSettingsFragment()
        } else {
            activityScope.launch {
                handleDefaultLaunch()
            }
        }
    }

    private suspend fun handleDefaultLaunch() = withContext(Dispatchers.IO) {
        try {
            val activeInstances = repository.getActiveInstances()

            withContext(Dispatchers.Main) {
                when {
                    activeInstances.isNotEmpty() -> {
                        Log.d(TAG, "Found ${activeInstances.size} active Spotlight instances")
                        showSettingsFragment()
                    }
                    else -> {
                        val nextInstanceId = instanceManager.getNextInstanceId(InstanceManager.SPOTLIGHT)
                        if (nextInstanceId != null) {
                            instanceManager.releaseInstanceId(InstanceManager.SPOTLIGHT, nextInstanceId)

                            Log.d(TAG, "No active Spotlights, starting new service")
                            val serviceIntent = Intent(this@SpotlightActivity, SpotlightService::class.java).apply {
                                action = ACTION_START_SPOTLIGHT_SERVICE
                            }
                            ContextCompat.startForegroundService(this@SpotlightActivity, serviceIntent)
                            finish()
                        } else {
                            Log.w(TAG, "Maximum Spotlight instances reached")
                            finish()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleDefaultLaunch", e)
            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }

    private fun showSettingsFragment() {
        if (supportFragmentManager.findFragmentByTag(SpotlightSettingsFragment.TAG) == null) {
            Log.d(TAG, "Showing Spotlight settings fragment.")
            val instanceId = intent.getIntExtra(SpotlightService.KEY_INSTANCE_ID, -1)
            val fragment = SpotlightSettingsFragment.newInstance(instanceId)

            supportFragmentManager.beginTransaction()
                .replace(R.id.spotlight_fragment_container, fragment, SpotlightSettingsFragment.TAG)
                .commit()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(TAG, "onNewIntent - Action: ${intent.action}")
        if (intent.action == ACTION_SHOW_SPOTLIGHT_SETTINGS) {
            showSettingsFragment()
        }
    }
}