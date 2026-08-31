package com.gastos.quincenal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gastos.quincenal.data.local.entity.MonthEntity
import com.gastos.quincenal.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM user_settings WHERE id = :id LIMIT 1")
    fun observeSettings(id: Int = UserSettingsEntity.SETTINGS_ID): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = :id LIMIT 1")
    suspend fun getSettings(id: Int = UserSettingsEntity.SETTINGS_ID): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettingsEntity)

    @Query("SELECT * FROM months")
    suspend fun getMonths(): List<MonthEntity>

    @Query("SELECT * FROM months WHERE yearMonth = :yearMonth LIMIT 1")
    fun observeMonth(yearMonth: String): Flow<MonthEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonth(month: MonthEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonths(months: List<MonthEntity>)

    @Query("DELETE FROM months")
    suspend fun deleteAllMonths()
}
