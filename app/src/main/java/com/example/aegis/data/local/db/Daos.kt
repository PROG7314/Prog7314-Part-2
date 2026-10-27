package com.example.aegis.data.local.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ListingCacheDao {

    @Query("SELECT json FROM cached_listings WHERE queryKey = :key ORDER BY position")
    abstract suspend fun jsonForQuery(key: String): List<String>

    /** Any cached copy of this listing, from whichever feed it appeared in. */
    @Query("SELECT json FROM cached_listings WHERE listingId = :id LIMIT 1")
    abstract suspend fun jsonForListing(id: String): String?

    @Query("DELETE FROM cached_listings WHERE queryKey = :key")
    abstract suspend fun deleteQuery(key: String)

    @Insert
    abstract suspend fun insertAll(rows: List<CachedListingEntity>)

    /** Swap one feed's cached copy for the fresh one in a single transaction. */
    @Transaction
    open suspend fun replaceQuery(key: String, rows: List<CachedListingEntity>) {
        deleteQuery(key)
        insertAll(rows)
    }
}

@Dao
abstract class CommitmentDao {

    @Query("SELECT * FROM cached_commitments ORDER BY position")
    abstract suspend fun all(): List<CachedCommitmentEntity>

    @Query("SELECT * FROM cached_commitments WHERE id = :id")
    abstract suspend fun find(id: String): CachedCommitmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(row: CachedCommitmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(rows: List<CachedCommitmentEntity>)

    @Query("DELETE FROM cached_commitments WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM cached_commitments")
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun replaceAll(rows: List<CachedCommitmentEntity>) {
        deleteAll()
        insertAll(rows)
    }
}

@Dao
abstract class PendingChangeDao {

    @Insert
    abstract suspend fun insert(row: PendingChangeEntity): Long

    /** Oldest first - the order the user made the changes in. */
    @Query("SELECT * FROM pending_changes WHERE state = 'unsynced' ORDER BY changedAt, seq LIMIT :limit")
    abstract suspend fun unsynced(limit: Int): List<PendingChangeEntity>

    @Query("SELECT * FROM pending_changes WHERE state = 'unsynced' ORDER BY changedAt, seq")
    abstract suspend fun allUnsynced(): List<PendingChangeEntity>

    @Query("SELECT * FROM pending_changes WHERE state = 'unsynced' AND signupId = :signupId AND action = :action LIMIT 1")
    abstract suspend fun findUnsynced(signupId: String, action: String): PendingChangeEntity?

    @Query("UPDATE pending_changes SET state = :state, message = :message WHERE seq = :seq")
    abstract suspend fun setState(seq: Long, state: String, message: String?)

    @Query("DELETE FROM pending_changes WHERE seq = :seq")
    abstract suspend fun delete(seq: Long)

    /** Drives the "N changes waiting" banner and the pending icons. */
    @Query("SELECT COUNT(*) FROM pending_changes WHERE state = 'unsynced'")
    abstract fun observeUnsyncedCount(): Flow<Int>

    /** Housekeeping: synced / rejected rows only matter for a short while. */
    @Query("DELETE FROM pending_changes WHERE state != 'unsynced' AND changedAt < :before")
    abstract suspend fun pruneFinished(before: Long)
}