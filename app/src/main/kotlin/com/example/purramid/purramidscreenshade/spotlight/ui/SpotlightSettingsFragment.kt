package com.example.purramid.purramidscreenshade.spotlight.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.purramid.purramidscreenshade.R
import com.example.purramid.purramidscreenshade.databinding.FragmentSpotlightSettingsBinding
import com.example.purramid.purramidscreenshade.spotlight.ACTION_ADD_NEW_SPOTLIGHT_OPENING
import com.example.purramid.purramidscreenshade.spotlight.SpotlightService
import com.example.purramid.purramidscreenshade.spotlight.viewmodel.SpotlightSettingsViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SpotlightSettingsFragment : Fragment() {

    private var _binding: FragmentSpotlightSettingsBinding? = null
    private val binding get() = _binding!!

    // Use the settings-specific ViewModel
    private val viewModel: SpotlightSettingsViewModel by viewModels()

    companion object {
        const val TAG = "SpotlightSettingsFragment"
        fun newInstance(): SpotlightSettingsFragment {
            return SpotlightSettingsFragment()
        }
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
            viewModel.uiState.collectLatest { state ->
                // Update UI based on state if needed
                binding.buttonAddNewSpotlight.isEnabled = state.canAddMore
            }
        }
    }

    private fun setupListeners() {
        binding.buttonCloseSpotlightSettings.setOnClickListener {
            activity?.finish()
        }

        binding.buttonAddNewSpotlight.setOnClickListener {
            if (viewModel.canAddNewSpotlight()) {
                Log.d(TAG, "Add new spotlight requested from settings.")
                val serviceIntent = Intent(requireContext(), SpotlightService::class.java).apply {
                    action = ACTION_ADD_NEW_SPOTLIGHT_OPENING
                }
                ContextCompat.startForegroundService(requireContext(), serviceIntent)

                // Refresh the state after adding
                viewModel.refresh()
            } else {
                Snackbar.make(
                    binding.root,
                    getString(R.string.max_spotlights_reached_snackbar),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh state when fragment resumes
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}