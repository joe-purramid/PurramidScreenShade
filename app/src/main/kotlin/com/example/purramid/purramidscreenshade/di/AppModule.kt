// SharedPreferencesModule.kt
package com.example.purramid.purramidscreenshade.di

import android.content.Context
import android.content.SharedPreferences
import com.example.purramid.purramidscreenshade.screen_mask.ScreenMaskService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ScreenMaskPrefs
    fun provideScreenMaskPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(ScreenMaskService.PREFS_NAME_FOR_ACTIVITY, Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    @SpotlightPrefs // Use the qualifier
    fun provideSpotlightPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(com.example.purramid.purramidscreenshade.spotlight.SpotlightService.PREFS_NAME_FOR_ACTIVITY, Context.MODE_PRIVATE)
    }
}