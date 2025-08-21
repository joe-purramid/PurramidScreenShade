# PurramidTime - Technical Architecture Decisions

## Overview
This document defines the mandatory architectural patterns and implementation approaches for Purramid applications.

## Core Architecture Pattern

### **MVVM + Repository Pattern**
- **Activities**: Handle UI lifecycle and system interactions only
- **ViewModels**: Manage UI state and business logic
- **Repository**: Single source of truth for data operations
- **Room Database**: Local data persistence
- **Foreground Service**: Background operations and overlay management

## Dependency Injection

### **Hilt Framework (Mandatory)**
```kotlin
// Application-level module
@Module
@InstallIn(SingletonComponent::class)
object AppModule

// Service-level injection
@Module 
@InstallIn(ServiceComponent::class)
object ServiceModule

// Activity-level injection
@AndroidEntryPoint
class SpotlightActivity : AppCompatActivity()
```

**Injection Strategy:**
- Repository instances: `@Singleton`
- ViewModel instances: `@ViewModelScoped`
- Database: `@Singleton`
- Audio manager: `@Singleton`
- Service: System-managed singleton

## State Management

### **UI State Pattern**
```kotlin
sealed class SpotlightUiState {
    object Loading : SpotlightUiState()
    data class Success(val data: SpotlightData) : SpotlightUiState()
    data class Error(val message: String) : SpotlightUiState()
}
```

### **Settings State Pattern**
```kotlin
data class SpotlightSettings(
    val windowSize: WindowSize = WindowSize.DEFAULT,
    val screenPosition: ScreenPosition = ScreenPosition.DEFAULT,
    val activeSpotlightId: Long? = null,
    val musicEnabled: Boolean = false,
    val marathonEnabled: Boolean = false
)
```

## Coroutines and Threading

### **Scope Management**
- **ViewModels**: Use `viewModelScope` for UI-related operations
- **Repository**: Use `viewModelScope` from calling ViewModel
- **Service**: Use `serviceScope` for background operations
- **Database**: All operations on `Dispatchers.IO`

### **Threading Rules**
```kotlin
class PurrPetsRepository @Inject constructor(
    private val dao: SpotlightDao,
    private val audioManager: AudioManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun saveSpotlightData(spotlight: Spotlight) = withContext(ioDispatcher) {
        dao.insertSpotlight(spotlight)
    }
    
    fun observeSpotlight() = dao.getAllSpotlight().flowOn(ioDispatcher)
}
```

## Service Architecture

### **Foreground Service Pattern**
```kotlin
@AndroidEntryPoint
class SpotlightService : Service() {
    @Inject lateinit var repository: SpotlightRepository
    @Inject lateinit var audioManager: AudioManager
    
    // Service manages overlay window lifecycle
    // Repository handles all data operations
    // AudioManager handles microphone operations
}
```

**Service Responsibilities:**
- Overlay window management
- System permission handling
- Alarm scheduling and execution
- Inter-component communication

## Data Flow Architecture

### **Unidirectional Data Flow**
```
User Input → ViewModel → Repository → Database/Service
          ← StateFlow ← StateFlow ← Flow/LiveData
```

### **Communication Patterns**
- **Activity ↔ Service**: Bound service with IBinder interface
- **ViewModel ↔ Repository**: Direct injection, coroutine-based
- **Repository ↔ Database**: Room DAO with Flow/suspend functions
- **Service ↔ Audio**: Direct AudioManager integration

## Window Management

### **Overlay Window Architecture**
- Service creates and manages overlay window
- WindowManager.LayoutParams for positioning
- Touch event handling through OnTouchListener
- State persistence through Repository

### **Window State Management**
```kotlin
data class WindowState(
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
    val isFullScreen: Boolean = false,
    val isMinimized: Boolean = false
)
```

## Animation Architecture

### **Animation Management**
1. Core Animation Types
 1.1 Time Display Animations
  - Second Hand Movement (Analog Mode)
   - Smooth sweep option: Continuous rotation at 6°/second
   - Tick option: Discrete 6° jumps every second
   - Requirement: User-configurable per instance
  - Digit Transitions (Digital Mode)
   - Fade in/out for changing digits
   - Optional flip animation for retro-style display
   - Requirement: Smooth transitions without text jumping

 1.2 Mode Transition Animations
    - Digital ↔ Analog Switching
   - Duration: 300-500ms
   - Type: Crossfade with optional rotation
   - Requirement: No jarring visual artifacts during transition

 1.3 Window State Animations
  - Window Resize Animations
   - Smooth scaling during pinch gestures
   - Requirement: Real-time response without lag

 1.4 Interactive Animations
  - Button Press Feedback
   - Scale/opacity change on touch
   - Ripple effect for Material Design compliance

2. Performance Requirements
 - 2.1 Frame Rate Targets
  - Minimum 30 FPS for all animations
  - Target 60 FPS for time-critical animations (second hand, digit changes)
  - Degradation: Graceful fallback for multiple active instances

 - 2.2 Resource Management
  - Memory Budget: Max 10MB per screen mask instance for animation resources
  - CPU Usage: Animation thread should not exceed 5% CPU per instance
  - Battery Impact: Implement frame skipping when device is in power-saving mode

3. Technical Implementation Requirements
 - 3.1 Animation Framework
  - Primary: Android Animator API for property animations
  - Constraint: Must work with WindowManager overlay system

 - 3.2 Synchronization
  - Time Sync: All animations must sync with the shared time ticker
  - Frame Alignment: Coordinate updates to prevent screen tearing
  - State Consistency: Animation state must persist through configuration changes

 - 3.3 Lifecycle Management
```kotlin
interface AnimationLifecycle {
    fun onAnimationStart()
    fun onAnimationPause()
    fun onAnimationResume()
    fun onAnimationStop()
    fun onAnimationDestroy()
}
```

## Error Handling

### **Repository Error Handling**
```kotlin
sealed class SpotlightResult<T> {
    data class Success<T>(val data: T) : SpotlightResult<T>()
    data class Error<T>(val exception: Exception) : SpotlightResult<T>()
}
```

### **Error Propagation Strategy**
- Repository catches and wraps exceptions
- ViewModel converts to UI-appropriate error states
- Service logs critical errors and attempts recovery
- User-facing errors show in Snackbars (per code style guide)

## Testing Architecture

### **Testing Layers**
- **Unit Tests**: Repository, ViewModel logic
- **Integration Tests**: Database operations, Service functionality
- **UI Tests**: Activity interactions, overlay behavior
- **Accessibility Tests**: Keyboard navigation, screen reader support

### **Dependency Injection for Testing**
```kotlin
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object TestAppModule
```

## Configuration Management

### **Build Variants**
- `debug`: Development with extensive logging
- `release`: Production with minimal logging
- Shared configuration through `BuildConfig`

### **Manifest Requirements**
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<application android:supportsRtl="true">
    <service android:name=".service.SpotlightService" 
             android:foregroundServiceType="specialUse" />
</application>
```

## Performance Requirements

### **Memory Management**
- Maximum 100MB RAM usage
- Efficient bitmap handling for graphics
- Proper lifecycle cleanup for animations
- Service memory monitoring

### **Responsiveness**
- UI interactions: < 16ms response time
- Database operations: < 100ms for simple queries
- Audio detection: 2-second polling interval
- Animation frame rate: 60 FPS target, 30 FPS minimum

## Security Considerations

### **Data Protection**
- No sensitive user data collection
- Local storage only (no cloud sync)
- Audio data processed locally, never stored
- Overlay permission usage limited to app functionality

### **Permission Handling**
- Request permissions on first use
- Graceful degradation when permissions denied
- Clear permission rationale to users
- Regular permission state validation