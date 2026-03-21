package com.example.fakeproductdetector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(entity: ScanEntity)

    @Query("SELECT * FROM scan_history ORDER BY scannedAt DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    fun getScanById(id: String): Flow<ScanEntity?>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun getScanByIdOnce(id: String): ScanEntity?

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteScan(id: String)
}