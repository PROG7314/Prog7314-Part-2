package com.example.aegis.data

import android.content.Context
import com.example.aegis.data.local.db.AegisDatabase
import com.example.aegis.data.remote.AegisApi
import com.example.aegis.data.remote.ApiClient
import com.example.aegis.data.remote.TokenProvider
import com.example.aegis.data.repository.ApiListingRepository
import com.example.aegis.data.repository.ApiSignupRepository
import com.example.aegis.data.repository.ApiUserRepository
import com.example.aegis.data.repository.OfflineListingRepository
import com.example.aegis.data.repository.OfflineSignupRepository
import com.example.aegis.data.repository.SyncEngine
import com.example.aegis.data.sync.ConnectivityObserver
import com.example.aegis.data.sync.SyncScheduler
import com.example.aegis.data.sync.SyncState
import com.example.aegis.domain.repository.ListingRepository
import com.example.aegis.domain.repository.SignupRepository
import com.example.aegis.domain.repository.UserRepository
import com.example.aegis.data.repository.ApiMessageRepository
import com.example.aegis.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * The place that builds the API client and repositories.
 * Call init() once in Application.onCreate() before any screen touches a repository, then startOffline().
 *
 * Listings and sign-ups now go through the offline-first repositories (local copy + sync queue).
 * The ViewModels don't know the difference - they still get a ListingRepository / SignupRepository.
 */
object ServiceLocator {

    private var apiInstance: AegisApi? = null
    private var appContext: Context? = null

    // Lives as long as the process; used for the "pending changes" counter.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context, baseUrl: String, tokenProvider: TokenProvider, debug: Boolean) {
        appContext = context.applicationContext
        apiInstance = ApiClient.create(baseUrl, tokenProvider, debug)
    }

    fun api(): AegisApi =
        checkNotNull(apiInstance) { "ServiceLocator.init() must be called in Application.onCreate() first" }

    private fun context(): Context =
        checkNotNull(appContext) { "ServiceLocator.init() must be called in Application.onCreate() first" }

    // ----- offline machinery -----
    val database: AegisDatabase by lazy { AegisDatabase.get(context()) }
    val connectivity: ConnectivityObserver by lazy { ConnectivityObserver(context()) }
    val syncState: SyncState by lazy { SyncState(context(), database.pendingDao(), appScope) }
    val syncEngine: SyncEngine by lazy { SyncEngine(context(), api(), database, syncState) }

    /** Start watching the network, sync when it comes back, and keep a 15-minute safety-net job. */
    fun startOffline() {
        syncState // make sure the pending counter is running
        connectivity.start { SyncScheduler.syncNow(context()) }
        SyncScheduler.schedulePeriodic(context())
    }

    /** Call on logout: the next account must not see this one's cached data or inherit its queued changes. */
    suspend fun clearUserData() {
        SyncScheduler.cancelAll(context())
        withContext(Dispatchers.IO) { database.clearAllTables() } // can't run on the main thread
        syncState.reset()
        SyncScheduler.schedulePeriodic(context())
    }

    // ----- repositories -----
    val listingRepository: ListingRepository by lazy {
        OfflineListingRepository(ApiListingRepository(api()), database.listingCacheDao(), connectivity, syncState, context())
    }
    val signupRepository: SignupRepository by lazy {
        OfflineSignupRepository(ApiSignupRepository(api()), database, connectivity, syncState, context())
    }
    val userRepository: UserRepository by lazy { ApiUserRepository(api()) }
    val messageRepository: MessageRepository by lazy { ApiMessageRepository(api()) }
}