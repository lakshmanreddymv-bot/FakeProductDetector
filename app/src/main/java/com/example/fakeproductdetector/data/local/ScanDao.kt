package com.example.fakeproductdetector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// I: Interface Segregation — exposes only the scan-history CRUD operations needed by the repository
/**
 * Room Data Access Object (DAO) for the `scan_history` table.
 *
 * Provides reactive [Flow]-based queries for live UI updates and suspend functions
 * for one-shot operations.
 */
@Dao
interface ScanDao {

    /**
     * Inserts a new scan record, replacing any existing record with the same primary key.
     *
     * @param entity The [ScanEntity] to persist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(entity: ScanEntity)

    /**
     * Returns a live [Flow] of all scan records ordered by scan time, newest first.
     *
     * The Flow re-emits automatically whenever the table changes.
     *
     * @return A [Flow] emitting the full scan history list on every table change.
     */
    @Query("SELECT * FROM scan_history ORDER BY scannedAt DESC")
    fun getAllScans(): Flow<List<ScanEntity>>

    /**
     * Returns a live [Flow] for the scan record with the given [id].
     *
     * Emits `null` if no record with that [id] exists.
     *
     * @param id The unique identifier of the scan to observe.
     * @return A [Flow] emitting the matching [ScanEntity], or null if not found.
     */
    @Query("SELECT * FROM scan_history WHERE id = :id")
    fun getScanById(id: String): Flow<ScanEntity?>

    /**
     * Returns the scan record with the given [id] as a one-shot suspend call.
     *
     * Used internally before deletion to retrieve the image URI for cleanup.
     *
     * @param id The unique identifier of the scan to retrieve.
     * @return The matching [ScanEntity], or null if not found.
     */
    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun getScanByIdOnce(id: String): ScanEntity?

    /**
     * Permanently deletes the scan record with the given [id].
     *
     * @param id The unique identifier of the scan to delete.
     */
    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteScan(id: String)
}