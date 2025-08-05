// ScreenMaskDao.kt
package com.example.purramid.thepurramid.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScreenMaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: ScreenMaskStateEntity)

    @Query("SELECT * FROM screen_mask_states WHERE instanceId = :id")
    suspend fun getById(id: Int): ScreenMaskStateEntity?

    @Query("SELECT * FROM screen_mask_states")
    suspend fun getAllStates(): List<ScreenMaskStateEntity>

    @Query("DELETE FROM screen_mask_states WHERE instanceId = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM screen_mask_states")
    suspend fun clearAll()
}