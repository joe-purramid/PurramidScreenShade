# PurramidTime - Implementation Decisions

## Deviations from Specifications
- **Reviewed**: 14 June 2025
- **Architecture**: Instance management
- **Key Decisions**:
  - Do not use AtomicInteger for instance numbering
  - Use thepurramid.instance.InstanceManager.kt

- **Reviewed**: 15 June 2025
- **Architecture**: Service settings Window Implementation
- **Key Decisions**:
  - Settings should open at the center of the screen
  - Use an explosion animation
  - Future efforts may revise this implemenation to have settings open from the settings button in the app-intent window
 
- **Reviewed**: 20 June 2025
- **Architecture**: HiltViewModelFactory is incompatible with LifecycleService
- **Key Decisions**:
  - Use the standard ViewModelProvider with just the factory
  - Use a unique key for each ViewModel instance
  - Add an "initialize()" method to set the instance ID after creation
  - Remove the HiltViewModelFactory usage

  
  
  
  


- **Reviewed**: [Date]
- **Architecture**: [Relevant file]
- **Key Decisions**:
  - 
	- 
