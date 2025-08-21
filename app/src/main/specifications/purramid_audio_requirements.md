# Purramid Pets - Audio Requirements

## Overview
This document defines the audio system requirements for microphone monitoring, music playback, and alarm sound functionality. Not all these features are used in every Purramid app.

## Audio Permissions

### **Required Permissions**
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-feature 
    android:name="android.hardware.microphone" 
    android:required="false" />
```

### **Permission Handling Strategy**
- Request `RECORD_AUDIO` permission when user first selects Responsive Mode
- Graceful degradation if permission denied (disable Responsive Mode option)
- Show clear rationale: "Microphone access needed to monitor classroom noise levels"
- Re-check permission status on app launch and mode switching

## Microphone Management

### **Microphone Detection**
```kotlin
class AudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasActiveMicrophone(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC || 
                           it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                           it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
    }
}
```

### **Microphone Monitoring Requirements**
- **Polling Interval**: Every 2 seconds during Responsive Mode
- **Detection Frequency**: Check for microphone availability every 0.2 seconds when microphone lost
- **Audio Format**: 16-bit PCM, 44.1 kHz sample rate
- **Buffer Size**: Minimum buffer size for device
- **Permission**: Must handle runtime permission requests gracefully

### **Decibel Measurement Implementation**
```kotlin
class DecibelMeter {
    private var audioRecord: AudioRecord? = null
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    
    fun startMeasuring(): Flow<Double> = flow {
        // Implementation returns decibel readings every 2 seconds
        // Range: 0-120 dB typical
        // Accuracy: ±3 dB acceptable for classroom use
    }
    
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val MEASUREMENT_INTERVAL_MS = 2000L
    }
}
```

## Responsive Mode Audio Behavior

### **Decibel Thresholds and Actions**
| Threshold | Default Value | Pet Animation | Trigger Condition |
|-----------|---------------|---------------|-------------------|
| Green | ≤59 dB | Sleep | Quiet classroom |
| Yellow | 60-79 dB | Ear plugs | Normal classroom noise |
| Red | ≥80 dB | Crying | Loud classroom |

### **Audio Processing Requirements**
- **Real-time Processing**: Maximum 100ms latency from audio input to pet reaction
- **Background Processing**: Continue monitoring when app is minimized
- **Battery Optimization**: Use power-efficient audio APIs
- **Memory Usage**: Maximum 10MB for audio processing buffers

### **Error Handling**
```kotlin
sealed class AudioError {
    object PermissionDenied : AudioError()
    object MicrophoneUnavailable : AudioError()
    object AudioRecordingFailed : AudioError()
    data class UnknownError(val message: String) : AudioError()
}
```

## Music Playback System

### **Supported Audio Formats**
- **Primary**: MP3, AAC, OGG Vorbis
- **Secondary**: WAV, FLAC (if device supports)
- **Streaming**: HTTP/HTTPS URLs only
- **Validation**: Must validate URL before playback attempt

### **Music URL Requirements**
```kotlin
class MusicUrlValidator {
    suspend fun validateMusicUrl(url: String): MusicUrlResult {
        // Validate URL format
        // Test HTTP connectivity
        // Verify audio content type
        // Return success/failure with error details
    }
}

sealed class MusicUrlResult {
    data class Valid(val title: String?) : MusicUrlResult()
    data class Invalid(val reason: String) : MusicUrlResult()
}
```

### **Playback Implementation**
```kotlin
class MusicPlayer @Inject constructor() {
    private var mediaPlayer: MediaPlayer? = null
    
    fun playFromUrl(url: String): Flow<PlaybackState> = flow {
        // Handle streaming audio from URL
        // Emit playback states: Loading, Playing, Stopped, Error
        // Support play/pause/stop controls
    }
    
    fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

sealed class PlaybackState {
    object Loading : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    object Stopped : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}
```

### **Music Navigation Bar Requirements**
- **Equalizer Animation**: 3 vertical bars with randomized up/down motion during playback
- **Song Title Display**: Extract from metadata or show musical notes if unavailable
- **Controls**: Play, Pause, Reset (stop and return to beginning)
- **Visual Feedback**: Equalizer bars static when paused, animated when playing

## Alarm Audio System

### **Audio Source Options**
```kotlin
sealed class AlarmAudioSource {
    data class SystemAlarm(val uri: Uri) : AlarmAudioSource()
    data class MusicUrl(val url: String, val title: String?) : AlarmAudioSource()
    object DefaultAlarm : AlarmAudioSource()
}
```

### **System Integration**
- **Available System Alarms**: Query `RingtoneManager.TYPE_ALARM`
- **Custom Music**: Use URLs from Music Manager
- **Default Fallback**: Use system default alarm if custom source fails
- **Volume**: Use system alarm volume level
- **Duration**: Play until user taps to dismiss

### **Alarm Playback Requirements**
```kotlin
class AlarmPlayer @Inject constructor(
    private val context: Context,
    private val musicPlayer: MusicPlayer
) {
    suspend fun playAlarm(source: AlarmAudioSource) {
        when (source) {
            is AlarmAudioSource.SystemAlarm -> playSystemAlarm(source.uri)
            is AlarmAudioSource.MusicUrl -> playMusicAlarm(source.url)
            is AlarmAudioSource.DefaultAlarm -> playDefaultAlarm()
        }
    }
    
    private fun playSystemAlarm(uri: Uri) {
        // Use MediaPlayer with system alarm URI
        // Respect system alarm volume
        // Loop until dismissed
    }
}
```

## Audio Focus Management

### **Audio Focus Strategy**
```kotlin
class AudioFocusManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    fun requestAudioFocus(usage: AudioUsage): Boolean {
        val focusRequest = AudioFocusRequest.Builder(AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage.toAudioAttribute())
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()
            
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
}

enum class AudioUsage {
    MUSIC_PLAYBACK,     // For background music
    ALARM_NOTIFICATION, // For alarm sounds
    MICROPHONE_MONITORING // For responsive mode (no actual audio output)
}
```

### **Background Audio Behavior**
- **Music Playback**: Continue when app minimized
- **Alarm Audio**: Continue when app minimized until dismissed
- **Microphone Monitoring**: Continue when app minimized (Responsive Mode)
- **Audio Focus**: Handle phone calls, other apps requesting audio focus

## Performance Requirements

### **Latency Requirements**
- **Microphone Response**: < 100ms from audio input to pet animation trigger
- **Music Playback Start**: < 500ms from URL to first audio output
- **Alarm Trigger**: < 50ms from scheduled time to audio start

### **Memory Usage**
- **Audio Buffers**: Maximum 10MB total
- **Music Streaming**: 2MB buffer for network audio
- **Microphone Processing**: 1MB buffer for real-time analysis

### **Battery Optimization**
- **Microphone Monitoring**: Use lowest acceptable sample rate
- **Background Processing**: Minimize CPU usage during monitoring
- **Doze Mode**: Handle Android Doze mode for alarm functionality

## Error Recovery

### **Network Audio Errors**
```kotlin
class AudioErrorRecovery {
    fun handleMusicUrlError(error: Exception): UserAction {
        return when (error) {
            is UnknownHostException -> UserAction.ShowSnackbar("No internet connection for music")
            is FileNotFoundException -> UserAction.ShowSnackbar("Music URL not found")
            is SecurityException -> UserAction.ShowSnackbar("Cannot access music URL")
            else -> UserAction.ShowSnackbar("Unable to play music from URL")
        }
    }
}
```

### **Microphone Recovery**
- **Permission Lost**: Prompt user to re-grant permission
- **Hardware Disconnected**: Switch to Rewards Mode with user notification
- **Audio Recording Failed**: Retry with different audio parameters
- **System Audio Conflict**: Wait and retry when audio becomes available

## Testing Requirements

### **Audio Testing Strategy**
```kotlin
// Unit Tests
class AudioManagerTest {
    @Test fun `microphone detection works correctly`
    @Test fun `decibel measurement returns valid range`
    @Test fun `music URL validation catches invalid URLs`
    @Test fun `alarm audio source selection works`
}

// Integration Tests  
class AudioIntegrationTest {
    @Test fun `responsive mode triggers correct animations`
    @Test fun `music playback integrates with navigation bar`
    @Test fun `alarm audio plays at scheduled time`
}
```

### **Manual Testing Checklist**
- [ ] Microphone permission request and denial handling
- [ ] Responsive mode with actual classroom noise levels
- [ ] Music URL playback with various formats and sources
- [ ] Alarm functionality with system sounds and custom music
- [ ] Audio focus handling during phone calls
- [ ] Background audio behavior when app minimized
- [ ] Battery usage during extended microphone monitoring

## Platform Compatibility

### **Android Version Support**
- **Minimum SDK**: API 26 (Android 8.0) for AudioRecord improvements
- **Target SDK**: API 34 (Android 14) for latest audio APIs
- **Audio Features**: Graceful degradation on older devices

### **Hardware Requirements**
- **Microphone**: Required for Responsive Mode, optional for Rewards Mode
- **Speakers/Headphones**: Required for music and alarm audio
- **Network**: Required for streaming music URLs
- **Storage**: 50MB minimum for audio processing libraries