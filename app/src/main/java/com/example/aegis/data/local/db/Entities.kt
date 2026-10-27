package com.example.aegis.data.local.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/*
 * The three RoomDB tables from the Part 1 data model:
 *   1. cached_listings     - the listings feed as last fetched, so Home still shows something offline
 *   2. cached_commitments  - the volunteer's sign-ups as last fetched (what the server says)
 *   3. pending_changes     - the queue: sign-ups and cancels made offline, each unsynced / synced / rejected
 *
 * Listings are stored as JSON (ListingSnapshot) rather than one column per field - the cache is only
 * ever read back whole, never queried by field, and it keeps the schema stable if Listing grows.
 */

@Entity(
    tableName = "cached_listings",
    indices = [Index("queryKey"), Index("listingId")]
)
data class CachedListingEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    /** Identifies which filters produced this list (see listingQueryKey), so each feed is cached on its own. */
    val queryKey: String,
    /** Keeps the server's order. */
    val position: Int,
    val listingId: String,
    val json: String
)

@Entity(tableName = "cached_commitments")
data class CachedCommitmentEntity(
    /** The server's sign-up id - or the app's own UUID for a sign-up that was made offline and then synced. */
    @PrimaryKey val id: String,
    val listingId: String,
    val volunteerId: String?,
    /** SignupStatus.name */
    val status: String,
    val hoursCompleted: Double?,
    val position: Int,
    val listingJson: String?
)

object PendingAction {
    const val SIGNUP = "signup"
    const val CANCEL = "cancel"
}

/** The "synced / unsynced" status from the data model, plus REJECTED for changes the server refused. */
object SyncStatus {
    const val UNSYNCED = "unsynced"
    const val SYNCED = "synced"
    const val REJECTED = "rejected"
}

@Entity(
    tableName = "pending_changes",
    indices = [Index("state"), Index("signupId")]
)
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    /** The app's UUID for the sign-up. Sent to the server as the sign-up's id, which makes retries safe. */
    val signupId: String,
    /** PendingAction.SIGNUP or PendingAction.CANCEL */
    val action: String,
    val listingId: String?,
    /** Snapshot of the listing at the time, so the Commitments screen can show it while offline. */
    val listingJson: String?,
    /** When the user actually tapped (epoch millis). Sent as `updatedAt`; the server uses it for last-write-wins. */
    val changedAt: Long,
    /** SyncStatus.* - the DAO queries compare against these literals, keep them in step. */
    val state: String = SyncStatus.UNSYNCED,
    /** Why the server rejected it (only when state = REJECTED). */
    val message: String? = null
)