package com.example.aegis.data.local.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [CachedListingEntity::class, CachedCommitmentEntity::class, PendingChangeEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AegisDatabase : RoomDatabase() {

    abstract fun listingCacheDao(): ListingCacheDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun pendingDao(): PendingChangeDao

    companion object {
        @Volatile
        private var instance: AegisDatabase? = null

        fun get(context: Context): AegisDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder<AegisDatabase>(context.applicationContext, "aegis.db")
                    // Room 3 always needs a SQLite driver; this one uses the phone's built-in SQLite.
                    .setDriver(AndroidSQLiteDriver())
                    .build()
                    .also { instance = it }
            }
    }
}