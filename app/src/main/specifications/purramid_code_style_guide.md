# The Purramid - Code Style Guide

## Naming Conventions
- Naming: PurramidTime can be abbreviated as PurrTime in file titles for efficiency
- Activities: *Activity (e.g., ClockActivity, TimerActivity, StopwatchActivity)
- Services: *Service (e.g., ClockOverlayService)
- ViewModels: *ViewModel
- Repositories: *Repository
- Use lowercase for packages: com.purramid.purramidtime.*

## Resolution standards
- Pixel density must support 2k and 4k resolutions
- Default resolution is 4k
- 1k is unnecessary
- 8k is unnecessary at this time

## State Management
- Use sealed classes for UI states
- Use data classes for preferences
- Immutable data wherever possible

## Coroutines
- Use viewModelScope in ViewModels
- Use lifecycleScope in Activities
- Structured concurrency for Services

## Resource Organization
- Drawables: ic_* for icons, bg_* for backgrounds
- Strings: Organized by feature in strings.xml

## Localization
- Logs can be in English only
- All other text should be implemented as strings
- "Purramid" is never translated

## Button Activation
- Button icons will be 757575 when inactive (default state)
- Button icons will be 1976D2 when active (active state)
- Button background will be E3F2FD when active
- A ripple effect will occur with the onTouch event that activates the button.
- Use isActivated property for boolean on/off states

## Message Boxes
- Messages should appear in snackbars not toasts
- Snackbars should open near the button that triggered the error message
- If action buttons are included in the snackbars
	- Cancel buttons
		- Appear on the left
		- Have a transparent background
		- Have an outline color of 757575
	- Accept buttons
		- Appear on the right
		- Have a color 2196F3 for the background fill and the outline
		
## Tooltips and Descriptive Text
- Action buttons should include descriptive strings for accessibility readers
- Icons should include a tooltip string
	- Tooltips should appear with a long press-and-hold touch
	- The tooltip box should include a tail that points at the icon in question
	- Tooltips remain visible until the user taps the screen again