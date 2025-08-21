// ScreenMaskSettingsFragment.kt
package com.example.purramid.purramidscreenshade.screen_mask.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.purramid.purramidscreenshade.R
import com.example.purramid.purramidscreenshade.databinding.FragmentScreenMaskSettingsBinding
import com.example.purramid.purramidscreenshade.screen_mask.ACTION_ADD_NEW_MASK_INSTANCE
import com.example.purramid.purramidscreenshade.screen_mask.ACTION_REMOVE_HIGHLIGHT
import com.example.purramid.purramidscreenshade.screen_mask.ACTION_REQUEST_IMAGE_CHOOSER
import com.example.purramid.purramidscreenshade.screen_mask.ACTION_TOGGLE_LOCK
import com.example.purramid.purramidscreenshade.screen_mask.ACTION_TOGGLE_LOCK_ALL
import com.example.purramid.purramidscreenshade.screen_mask.EXTRA_MASK_INSTANCE_ID
import com.example.purramid.purramidscreenshade.screen_mask.ScreenMaskActivity
import com.example.purramid.purramidscreenshade.screen_mask.ScreenMaskService
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.graphics.toColorInt

@AndroidEntryPoint
class ScreenMaskSettingsFragment : Fragment() {

    private var _binding: FragmentScreenMaskSettingsBinding? = null
    private val binding get() = _binding!!

    // No ViewModel needed for this simplified settings fragment if it only adds masks
    // and doesn't manage default color/opacity preferences.

    companion object {
        const val TAG = "ScreenMaskSettingsFragment"
        private const val ARG_INSTANCE_ID = "instance_id"

        fun newInstance(instanceId: Int): ScreenMaskSettingsFragment {
            return ScreenMaskSettingsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_INSTANCE_ID, instanceId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScreenMaskSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun sendHighlightCommand(instanceId: Int, highlight: Boolean) {
        val intent = Intent(requireContext(), ScreenMaskService::class.java).apply {
            action = if (highlight) {
                // We need to define a new action for highlighting
                "com.example.purramid.screen_mask.ACTION_HIGHLIGHT"
            } else {
                ACTION_REMOVE_HIGHLIGHT
            }
            putExtra(EXTRA_MASK_INSTANCE_ID, instanceId)
        }
        requireContext().startService(intent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val instanceId = arguments?.getInt(ARG_INSTANCE_ID) ?: -1
        sendHighlightCommand(instanceId, true)

        setupListeners()
        updateAddAnotherButtonState() // Check initial state
    }

    private fun setupListeners() {
        binding.buttonCloseScreenMaskSettings.setOnClickListener {
            activity?.finish() // Close the hosting ScreenMaskActivity
        }

        // Lock button for individual mask
        binding.lockButton.setOnClickListener {
            val instanceId = arguments?.getInt(ARG_INSTANCE_ID) ?: return@setOnClickListener
            sendLockCommand(instanceId)
        }

        // Lock All button
        binding.lockAllButton.setOnClickListener {
            sendLockAllCommand()
        }

        // Billboard button
        binding.billboardButton.setOnClickListener {
            val instanceId = arguments?.getInt(ARG_INSTANCE_ID) ?: return@setOnClickListener

            // Apply active state color
            applyActiveColorToButton(binding.billboardButton as MaterialButton)

            // Send command to service
            sendBillboardCommand(instanceId)
        }

        // Add New Mask
        binding.buttonAddNewMask.setOnClickListener {
            val prefs = requireActivity().getSharedPreferences(
                ScreenMaskActivity.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val activeCount = prefs.getInt(ScreenMaskActivity.KEY_ACTIVE_COUNT, 0)
            val maxMasks = ScreenMaskService.MAX_MASKS

            if (activeCount < maxMasks) {
                // Apply active state color
                applyActiveColorToButton(binding.buttonAddNewMask as MaterialButton)

                val serviceIntent = Intent(requireContext(), ScreenMaskService::class.java).apply {
                    action = ACTION_ADD_NEW_MASK_INSTANCE
                    putExtra(EXTRA_MASK_INSTANCE_ID, arguments?.getInt(ARG_INSTANCE_ID) ?: -1)
                }
                ContextCompat.startForegroundService(requireContext(), serviceIntent)

                // Reset color after a short delay
                binding.buttonAddNewMask.postDelayed({
                    clearButtonColorFilter(binding.buttonAddNewMask as MaterialButton)
                    updateAddAnotherButtonState() // Check if we need to disable it
                }, 300)
            } else {
                Snackbar.make(
                    binding.root,
                    getString(R.string.max_masks_reached_snackbar),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun sendLockCommand(instanceId: Int) {
        val intent = Intent(requireContext(), ScreenMaskService::class.java).apply {
            action = ACTION_TOGGLE_LOCK
            putExtra(EXTRA_MASK_INSTANCE_ID, instanceId)
        }
        requireContext().startService(intent)
    }

    private fun sendLockAllCommand() {
        val intent = Intent(requireContext(), ScreenMaskService::class.java).apply {
            action = ACTION_TOGGLE_LOCK_ALL
        }
        requireContext().startService(intent)
    }

    private fun sendBillboardCommand(instanceId: Int) {
        // This triggers the image picker via the service
        val intent = Intent(requireContext(), ScreenMaskService::class.java).apply {
            action = ACTION_REQUEST_IMAGE_CHOOSER
            putExtra(EXTRA_MASK_INSTANCE_ID, instanceId)
        }
        requireContext().startService(intent)
    }

    override fun onResume() {
        super.onResume()
        // Reset button colors when returning to settings
        clearButtonColorFilter(binding.billboardButton as MaterialButton)
        clearButtonColorFilter(binding.buttonAddNewMask as MaterialButton)
    }

    // Helper method to apply active color to Material Button
    private fun applyActiveColorToButton(button: MaterialButton) {
        button.iconTint = ColorStateList.valueOf("#808080".toColorInt())
    }

    // Helper method to clear color filter from Material Button
    private fun clearButtonColorFilter(button: MaterialButton) {
        button.iconTint = null
    }

    private fun updateAddAnotherButtonState() {
        val prefs = requireActivity().getSharedPreferences(
            ScreenMaskActivity.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val activeCount = prefs.getInt(ScreenMaskActivity.KEY_ACTIVE_COUNT, 0)

        // Cast binding button to MaterialButton
        val addButton = binding.buttonAddNewMask as MaterialButton

        if (activeCount >= ScreenMaskService.MAX_MASKS) {
            // Disable and apply inactive appearance
            addButton.isEnabled = false
            addButton.alpha = 0.5f
            // Apply gray tint for disabled state
            addButton.iconTint = ColorStateList.valueOf("#CCCCCC".toColorInt())
        } else {
            // Enable and restore normal appearance
            addButton.isEnabled = true
            addButton.alpha = 1.0f
            // Clear any color filter using the helper method
            clearButtonColorFilter(addButton)
        }
    }

    override fun onDestroyView() {
        // Remove highlight when closing
        val instanceId = arguments?.getInt(ARG_INSTANCE_ID) ?: -1
        if (instanceId != -1) {
            // Send intent to service to remove highlight
            val intent = Intent(requireContext(), ScreenMaskService::class.java).apply {
                action = ACTION_REMOVE_HIGHLIGHT
                putExtra(EXTRA_MASK_INSTANCE_ID, instanceId)
            }
            requireContext().startService(intent)
        }
        super.onDestroyView()
    }
}