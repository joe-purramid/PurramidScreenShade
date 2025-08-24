// ScreenMaskActivity.kt
package com.example.purramid.purramidscreenshade.screen_mask

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.transition.Explode
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.purramid.purramidscreenshade.R
import com.example.purramid.purramidscreenshade.databinding.ActivityScreenMaskBinding
import com.example.purramid.purramidscreenshade.screen_mask.ui.ScreenMaskSettingsFragment
import com.example.purramid.purramidscreenshade.screen_mask.viewmodel.ScreenMaskViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ScreenMaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScreenMaskBinding
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    // Use Hilt's viewModels delegate with default factory
    private val viewModel: ScreenMaskViewModel by viewModels()

    companion object {
        private const val TAG = "ScreenMaskActivity"
        const val ACTION_LAUNCH_IMAGE_CHOOSER_FROM_SERVICE = "com.example.purramid.screen_mask.ACTION_LAUNCH_IMAGE_CHOOSER_FROM_SERVICE"
        // Using the constants defined in ScreenMaskService for SharedPreferences
        const val PREFS_NAME = ScreenMaskService.PREFS_NAME_FOR_ACTIVITY
        const val KEY_ACTIVE_COUNT = ScreenMaskService.KEY_ACTIVE_COUNT
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // IMPORTANT: Set the instance ID in the intent extras BEFORE super.onCreate()
        // This ensures SavedStateHandle receives the value
        val requestingInstanceId = intent.getIntExtra(EXTRA_MASK_INSTANCE_ID, -1)
        if (requestingInstanceId != -1) {
            intent.putExtra(ScreenMaskViewModel.KEY_INSTANCE_ID, requestingInstanceId)
        }

        super.onCreate(savedInstanceState)
        binding = ActivityScreenMaskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate - Intent Action: ${intent.action}")

        // Explosion transition (appears in center)
        window.enterTransition = Explode().apply {
            duration = 300
        }
        window.exitTransition = Explode().apply {
            duration = 300
        }

        // Initialize image picker launcher (keep existing functionality)
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                Log.d(TAG, "Image selected: $uri")

                // Check image size before forwarding
                val fileSize = getFileSize(uri)
                if (fileSize > 3 * 1024 * 1024) { // 3MB in bytes
                    showImageSizeDialog(uri)
                } else {
                    sendImageUriToService(uri)
                    finish()
                }
            } else {
                Log.d(TAG, "No image selected from picker.")
                sendImageUriToService(null)
                finish()
            }
        }

        // Check the action that started this activity
        when (intent.action) {
            ACTION_LAUNCH_IMAGE_CHOOSER_FROM_SERVICE -> {
                Log.d(TAG, "Launched by service to pick image.")
                openImageChooser()
            }
            else -> {
                // Default: Show settings
                val requestingInstanceId = intent.getIntExtra(EXTRA_MASK_INSTANCE_ID, -1)
                showSettingsFragment(requestingInstanceId)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                Log.d(TAG, "Mask state updated: locked=${state.isLocked}, controls=${state.isControlsVisible}")
                // Update UI if needed
                // For example, you could update the title or other UI elements based on state
            }
        }
    }

    private fun openImageChooser() {
        try {
            imagePickerLauncher.launch("image/*")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open image picker", e)
            Snackbar.make(binding.root, getString(R.string.cannot_open_image_picker), Snackbar.LENGTH_LONG).show()
            finish()
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize
            } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size", e)
            0L
        }
    }

    private fun showImageSizeDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.image_too_large_title))
            .setMessage(getString(R.string.image_too_large_message))
            .setPositiveButton(getString(R.string.optimize)) { _, _ ->
                compressAndSendImage(uri)
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                // Return to picker
                openImageChooser()
            }
            .setCancelable(false)
            .show()
    }

    private fun compressAndSendImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val compressedUri = compressImage(uri)
                withContext(Dispatchers.Main) {
                    sendImageUriToService(compressedUri)
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error compressing image", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScreenMaskActivity, getString(R.string.compression_failed), Toast.LENGTH_LONG).show()
                    openImageChooser()
                }
            }
        }
    }

    private suspend fun compressImage(uri: Uri): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }

                // Calculate optimal dimensions while maintaining aspect ratio
                val maxDimension = 1920 // Max width or height
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()

                val (newWidth, newHeight) = if (bitmap.width > bitmap.height) {
                    if (bitmap.width > maxDimension) {
                        maxDimension to (maxDimension / ratio).toInt()
                    } else {
                        bitmap.width to bitmap.height
                    }
                } else {
                    if (bitmap.height > maxDimension) {
                        (maxDimension * ratio).toInt() to maxDimension
                    } else {
                        bitmap.width to bitmap.height
                    }
                }

                // Resize bitmap if needed
                val resizedBitmap = if (newWidth != bitmap.width || newHeight != bitmap.height) {
                    Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                        if (it != bitmap) bitmap.recycle()
                    }
                } else {
                    bitmap
                }

                // Progressive compression with format selection
                var outputStream: ByteArrayOutputStream
                var compressed: ByteArray
                var format = Bitmap.CompressFormat.JPEG
                var quality = 95
                val targetSize = 3 * 1024 * 1024 // 3MB

                do {
                    outputStream = ByteArrayOutputStream()
                    resizedBitmap.compress(format, quality, outputStream)
                    compressed = outputStream.toByteArray()

                    if (compressed.size > targetSize && quality > 10) {
                        quality -= 5
                    } else if (compressed.size > targetSize && format == Bitmap.CompressFormat.JPEG) {
                        // Try WebP if JPEG isn't sufficient
                        format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                        quality = 90
                    }
                } while (compressed.size > targetSize && quality > 10)

                resizedBitmap.recycle()

                // Save compressed image to cache
                val extension = when(format) {
                    Bitmap.CompressFormat.WEBP, Bitmap.CompressFormat.WEBP_LOSSY, Bitmap.CompressFormat.WEBP_LOSSLESS -> "webp"
                    else -> "jpg"
                }
                val fileName = "compressed_${System.currentTimeMillis()}.$extension"
                val file = File(cacheDir, fileName)
                file.writeBytes(compressed)

                // Clear old cached images to prevent storage buildup
                clearOldCachedImages()

                // Return URI of compressed file
                FileProvider.getUriForFile(this@ScreenMaskActivity, "${packageName}.fileprovider", file)

            } catch (e: Exception) {
                Log.e(TAG, "Compression error", e)
                null
            }
        }
    }

    private fun clearOldCachedImages() {
        try {
            val cacheDir = cacheDir
            val maxAge = 24 * 60 * 60 * 1000L // 24 hours
            val now = System.currentTimeMillis()

            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("compressed_") && (now - file.lastModified() > maxAge)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear old cached images", e)
        }
    }

    private fun sendImageUriToService(uri: Uri?) {
        val serviceIntent = Intent(this, ScreenMaskService::class.java).apply {
            action = ACTION_BILLBOARD_IMAGE_SELECTED
            putExtra(EXTRA_IMAGE_URI, uri?.toString()) // Send URI as String
            // The service knows which instance requested it via imageChooserTargetInstanceId
        }
        // Use startService for simple data passing intents that don't require foreground lifecycle
        startService(serviceIntent)
    }

    private fun showSettingsFragment(instanceId: Int) {
        val fragment = ScreenMaskSettingsFragment.newInstance(instanceId)

        supportFragmentManager.beginTransaction()
            .replace(R.id.screen_mask_fragment_container, fragment)
            .commit()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent with the new one
        handleIntent(intent)
    }

    // Centralized intent handling logic
    private fun handleIntent(intent: Intent) {
        Log.d(TAG, "handleIntent - Action: ${intent.action}")
        if (intent.action == ACTION_LAUNCH_IMAGE_CHOOSER_FROM_SERVICE) {
            Log.d(TAG, "Launched by service to pick image.")
            openImageChooser()
            // Activity will finish after imagePickerLauncher returns if openImageChooser calls finish()
        } else {
            // Default launch path or if reordered to front without specific known action
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val activeCount = prefs.getInt(KEY_ACTIVE_COUNT, 0)

            if (activeCount > 0) {
                Log.d(TAG, "Screen Masks active ($activeCount), launching settings fragment.")
                val instanceId = intent.getIntExtra(EXTRA_MASK_INSTANCE_ID, 1)
                showSettingsFragment(instanceId)
                // Activity remains open to host the fragment
            } else {
                Log.d(TAG, "No active Screen Masks, requesting service to add a new one.")
                val serviceIntent = Intent(this, ScreenMaskService::class.java).apply {
                    action = ACTION_ADD_NEW_MASK_INSTANCE
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                finish() // Finish after telling service to add the first instance
            }
        }
    }
}