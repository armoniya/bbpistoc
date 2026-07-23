package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboard_cache WHERE id = 1")
    fun getCachedDashboard(): Flow<DashboardEntity?>

    @Query("SELECT * FROM dashboard_cache WHERE id = 1")
    suspend fun getCachedDashboardOnce(): DashboardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDashboard(entity: DashboardEntity)
}
