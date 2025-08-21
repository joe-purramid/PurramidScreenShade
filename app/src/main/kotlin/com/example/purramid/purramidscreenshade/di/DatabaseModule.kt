// DatabaseModule.kt
package com.example.purramid.purramidscreenshade.di

import android.content.Context
import com.example.purramid.purramidscreenshade.di.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers


@Module
@InstallIn(SingletonComponent::class) // Provides dependencies for the entire app lifecycle
object DatabaseModule {

    @Provides
    @Singleton
    fun provideScreenMaskDao(database: PurrShadeDatabase): ScreenMaskDao { // Added
        return database.screenMaskDao()
    }

    @Provides
    @Singleton
    fun provideSpotlightDao(database: PurrShadeDatabase): SpotlightDao {
        return database.spotlightDao()
    }

    @Provides
    @Singleton // Ensures only one instance of the Database is created
    fun providePurramidDatabase(@ApplicationContext appContext: Context): PurrShadeDatabase {
        return PurramidDatabase.getDatabase(appContext)
    }

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}