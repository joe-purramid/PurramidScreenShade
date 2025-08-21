# Android Icon Active/Inactive State Implementation Guide

## Overview

This document provides step-by-step instructions for implementing consistent active/inactive states for action button icons in our Android Kotlin application targeting Android 13.

## Benefits of Background Tinting for Touch Interfaces

Adding background tinting addresses several critical touch interface challenges:

- **Finger Occlusion**: When users touch the screen, their finger covers the icon, making color-only feedback invisible
- **Immediate Feedback**: Background changes provide instant visual confirmation that the touch was registered
- **Peripheral Vision**: Users can see activation state even when not looking directly at the button
- **Accessibility**: Provides multiple visual cues (icon color + background) for better usability

## Design Standards

- **Active State Color**: `#1976D2` (Material Blue 700)
    - ic_info.xml is an exception to this rule
- **Inactive State Color**: `#757575` (Medium Gray)  
- **Active Background**: `#E3F2FD` (Light Blue - provides subtle contrast without overwhelming the icon)
- **Touch Feedback**: Ripple effect on all interactive buttons
- **State Management**: Use `isActivated` property for boolean on/off states

---

## Implementation Steps

### Step 1: Define Color Resources

Create or update `res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Icon state colors -->
    <color name="icon_active">#1976D2</color>
    <color name="icon_inactive">#757575</color>
    
    <!-- Background state colors -->
    <color name="button_background_active">#E3F2FD</color> <!-- Light blue background -->
    
    <!-- Ripple effect colors -->
    <color name="ripple_color">#1F1976D2</color> <!-- 12% opacity of active color -->
</resources>
```

### Step 2: Create Color State List

Create `res/color/icon_tint_stateful.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_activated="true" android:color="@color/icon_active" />
    <item android:color="@color/icon_inactive" />
</selector>
```

### Step 3: Create Ripple Background with Activated State

Create `res/drawable/action_button_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
        android:color="@color/ripple_color">
    <item android:id="@android:id/mask">
        <shape android:shape="oval">
            <solid android:color="@android:color/white"/>
        </shape>
    </item>
    <item>
        <selector>
            <item android:state_activated="true">
                <shape android:shape="oval">
                    <solid android:color="@color/button_background_active"/>
                </shape>
            </item>
            <item>
                <shape android:shape="oval">
                    <solid android:color="@android:color/transparent"/>
                </shape>
            </item>
        </selector>
    </item>
</ripple>
```

### Step 4: Update Existing Icon XML Files

For each existing icon XML file, update the `android:tint` attribute:

**Before:**
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:tint="#000000"
    ... >
```

**After:**
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:tint="@color/icon_inactive"
    ... >
```

**Apply this change to:**
- `ic_delete.xml`
- `ic_settings.xml`
- `ic_reset.xml`
- `ic_resize_left_handle.xml`
- `ic_resize_right_handle.xml`
- Any other action button icons

### Step 5: Update Layout Files

For each ImageButton in your fragment/activity layouts:

**Before:**
```xml
<ImageButton
    android:id="@+id/settingsButton"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:src="@drawable/ic_settings" />
```

**After:**
```xml
<ImageButton
    android:id="@+id/settingsButton"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:src="@drawable/ic_settings"
    android:imageTintList="@color/icon_tint_stateful"
    android:background="@drawable/action_button_background"
    android:contentDescription="@string/settings_button" />
```

**Required attributes for all action buttons:**
- `android:imageTintList="@color/icon_tint_stateful"`
- `android:background="@drawable/action_button_background"`
- `android:contentDescription="..."` (for accessibility)

### Step 6: Fragment/Activity Code Implementation

In your Fragment or Activity, manage button states using the `isActivated` property:

```kotlin
class YourFragment : Fragment() {
    private var isSettingsOpen = false
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val settingsButton = view.findViewById<ImageButton>(R.id.settingsButton)
        
        // Set initial state
        settingsButton.isActivated = isSettingsOpen
        
        settingsButton.setOnClickListener {
            // Toggle state
            isSettingsOpen = !isSettingsOpen
            settingsButton.isActivated = isSettingsOpen
            
            // Perform action based on state
            if (isSettingsOpen) {
                openSettingsPanel()
            } else {
                closeSettingsPanel()
            }
        }
    }
    
    private fun openSettingsPanel() {
        // Implementation for opening settings
    }
    
    private fun closeSettingsPanel() {
        // Implementation for closing settings
    }
}
```

### Step 7: State Persistence (Optional)

To maintain button states across fragment lifecycle events:

```kotlin
override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean("settings_open", isSettingsOpen)
}

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    // Restore state
    isSettingsOpen = savedInstanceState?.getBoolean("settings_open", false) ?: false
    
    val settingsButton = view.findViewById<ImageButton>(R.id.settingsButton)
    settingsButton.isActivated = isSettingsOpen
}
```

---

## File Checklist

Ensure these files are created/updated:

### New Files
- [ ] `res/color/icon_tint_stateful.xml`
- [ ] `res/drawable/action_button_background.xml`

### Updated Files
- [ ] `res/values/colors.xml` (add icon and background colors)
- [ ] `ic_delete.xml` (update tint)
- [ ] `ic_settings.xml` (update tint)
- [ ] `ic_reset.xml` (update tint)
- [ ] `ic_resize_left_handle.xml` (update tint)
- [ ] `ic_resize_right_handle.xml` (update tint)
- [ ] `ic_lock.xml` (update tint if needed)
- [ ] `ic_lock_all.xml` (update tint if needed)
- [ ] All fragment/activity layout files with ImageButtons
- [ ] All Fragment/Activity Kotlin files with button logic

---

## Testing Checklist

- [ ] Icons display in inactive state (#757575) with transparent background by default
- [ ] Icons change to active state (#1976D2) with light blue background when `isActivated = true`
- [ ] Ripple effect appears on touch for all buttons
- [ ] Button states persist during device rotation
- [ ] All buttons have proper content descriptions
- [ ] Color changes are smooth and immediate

---

## Common Issues

### Icons not changing color
**Solution**: Ensure `android:imageTintList` is set, not just `android:tint` in the vector

### Ripple effect not showing
**Solution**: Verify the button has `android:background="@drawable/action_button_background"`

### State not persisting
**Solution**: Implement `onSaveInstanceState` and restore logic

---

## Future Enhancements

When ready to add animations or additional visual feedback:

1. Create animator XML files in `res/animator/`
2. Add `android:stateListAnimator` to buttons
3. Consider elevation changes for active states
4. Add transition animations between states