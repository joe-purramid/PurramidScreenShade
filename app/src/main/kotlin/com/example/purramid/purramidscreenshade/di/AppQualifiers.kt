package com.example.purramid.purramidscreenshade.di

import javax.inject.Qualifier

// Define the qualifier annotation
@Qualifier
@Retention(AnnotationRetention.BINARY) // Standard retention for qualifiers
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScreenMaskPrefs

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SpotlightPrefs // Assuming you'll need one for Spotlight too
