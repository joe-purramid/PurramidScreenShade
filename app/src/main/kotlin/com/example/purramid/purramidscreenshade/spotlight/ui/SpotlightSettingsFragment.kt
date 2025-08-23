// SpotlightSettingsFragment.kt
package com.example.purramid.purramidscreenshade.spotlight.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.purramid.purramidscreenshade.R
import com.example.purramid.purramidscreenshade.databinding.FragmentSpotlightSettingsBinding
import com.example.purramid.purramidscreenshade.instance.InstanceManager
import com.example.purramid.purramidscreenshade.spotlight.ACTION_ADD_NEW_SPOTLIGHT_OPENING
import com.example.purramid.purramidscreenshade.spotlight.SpotlightService
import com.example.purramid.purramidscreenshade.spotlight.viewmodel.SpotlightViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SpotlightSettingsFragment : Fragment() {

    private var _binding: FragmentSpotlightSettingsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var instanceManager: InstanceManager

    // Use the Activity's ViewModel instead of creating a separate one
    private val spotlightViewModel: SpotlightViewModel by activityViewModels()

    private var instanceId: Int = -1

    companion object {
        const val TAG = "SpotlightSettingsFragment"
        private const val ARG_INSTANCE_ID = "instance_id"

        fun newInstance(instanceId: Int = -1): SpotlightSettingsFragment {
            return SpotlightSettingsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_INSTANCE_ID, instanceId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instanceId = arguments?.getInt(ARG_INSTANCE_ID, -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpotlightSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            spotlightViewModel.uiState.collectLatest { state ->
                // Update button based on whether we can add more openings
                binding.buttonAddNewSpotlight.isEnabled = state.canAddMore

                // Could also update UI to show current number of openings
                // binding.textOpeningsCount.text = "Openings: ${state.openings.size}/${SpotlightUiState.MAX_OPENINGS}"
            }
        }
    }

    private fun setupListeners() {
        binding.buttonCloseSpotlightSettings.setOnClickListener {
            activity?.finish()
        }

        binding.buttonAddNewSpotlight.setOnClickListener {
            val activeCount = instanceManager.getActiveInstanceCount(InstanceManager.SPOTLIGHT)

            if (activeCount < 4) {  // MAX_SPOTLIGHTS
                Log.d(TAG, "Add new spotlight requested from settings.")
                val serviceIntent = Intent(requireContext(), SpotlightService::class.java).apply {
                    action = ACTION_ADD_NEW_SPOTLIGHT_OPENING
                    if (instanceId > 0) {
                        putExtra(SpotlightService.KEY_INSTANCE_ID, instanceId)
                    }
                }
                ContextCompat.startForegroundService(requireContext(), serviceIntent)
            } else {
                Snackbar.make(
                    binding.root,
                    getString(R.string.max_spotlights_reached_snackbar),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}