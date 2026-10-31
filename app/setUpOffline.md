# Offline mode: setup

Not built in Android Studio yet (no Android SDK here), so expect small fixes on the first Gradle sync.
The backend half (`POST /sync/signups`) is done and tested: backend README, section 6c.

## What it does

- **Cached listings feed.** Home shows the last feed it loaded when there's no connection.
- **Cached commitments.** Commitments shows the last list it loaded.
- **Sign up offline.** Tapping "I'd like to help" with no connection saves the sign-up on the phone. It appears in Commitments straight away with a "waiting to sync" icon.
- **Dismiss offline.** Same: it's saved, then sent later. Dismissing a sign-up that never reached the server just deletes it locally.
- **Auto sync.** When the connection returns (and every 15 min as a safety net) WorkManager sends the queue to `POST /sync/signups`.
- **Conflicts.** Last write wins (backend README 6c). A refused change (listing filled up, event over...) is marked rejected and the user is told why.
- **Not offline:** organisation screens, creating/editing listings, the Hours screen. They need the server and show the normal "can't reach the server" error.

Files, all under `app/src/main/java/com/example/aegis/`:

| Package | Files |
|---|---|
| `data/local/db` | `Entities.kt` (3 tables), `Daos.kt`, `AegisDatabase.kt`, `ListingSnapshot.kt` |
| `data/sync` | `ConnectivityObserver.kt`, `SyncState.kt`, `SyncScheduler.kt`, `SyncWorker.kt` |
| `data/repository` | `OfflineRepositories.kt`, `SyncEngine.kt` (new files; leave `ApiRepositories.kt` and `Mappers.kt` as they are) |
| `data/remote/dto` | `SyncDtos.kt` |
| `data` | `ServiceLocator.kt` (replaces yours) |
| (root) | `AegisApp.kt` (replaces yours; it already includes the notification channel line) |
| `ui/offline` | `OfflineBanner.kt` |

Resources: `view_offline_banner.xml`, `ic_sync_pending.xml`, `strings_offline_to_add.xml`.
Test: `app/src/test/.../data/local/OfflineCacheTest.kt`.

## 1. Gradle

`gradle/libs.versions.toml`:

```toml
[versions]
room3 = "3.0.3"
work = "2.12.0"
ksp = "2.3.4"

[libraries]
androidx-room3-runtime = { module = "androidx.room3:room3-runtime", version.ref = "room3" }
androidx-room3-compiler = { module = "androidx.room3:room3-compiler", version.ref = "room3" }
androidx-work-runtime = { module = "androidx.work:work-runtime", version.ref = "work" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

Top-level `build.gradle.kts`, in `plugins { }`:

```kotlin
alias(libs.plugins.ksp) apply false
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)            // add
    id("com.google.gms.google-services")
}

dependencies {
    // ...existing...
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)
    implementation(libs.androidx.work.runtime)
}
```

Versions are the latest stable on the Android and Kotlin docs pages (Room 3.0.3, WorkManager 2.12.0, KSP 2.3.4).

**Room 3:** the code uses Room 3 (`androidx.room3`), not Room 2. Room 3 needs a SQLite driver, and `AegisDatabase.kt` uses `AndroidSQLiteDriver` (phone's built-in SQLite). If `androidx.sqlite.driver.AndroidSQLiteDriver` doesn't resolve, add `implementation("androidx.sqlite:sqlite-framework:2.6.0")`, or check the version Android Studio suggests.

**The risky bit:** the project uses AGP 9.2.1, which has Kotlin built in. KSP's support for that is still being worked on (see google/ksp issue 2615), and I couldn't test it. If Gradle sync or the build fails on the `ksp` plugin, paste me the exact error. The fallback is plain SQLite (`SQLiteOpenHelper`): the brief allows "RoomDB or SQLite", and only the three `data/local/db` files would change.

## 2. Manifest

Add next to the existing `INTERNET` permission:

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## 3. Edits to files you already have

**`domain/model/Models.kt`**: add one field at the end of `Signup` (it has a default, so nothing else breaks):

```kotlin
data class Signup(
    val id: String,
    val listingId: String,
    val volunteerId: String?,
    val volunteerName: String?,
    val status: SignupStatus,
    val hoursCompleted: Double?,
    val listing: Listing?,
    val pendingSync: Boolean = false   // true while an offline change hasn't reached the server yet
)
```

**`data/remote/AegisApi.kt`**: add the import and the method.

```kotlin
import com.example.aegis.data.remote.dto.SyncRequest
import com.example.aegis.data.remote.dto.SyncResponse

    // ----- Offline sync -----
    @POST("sync/signups")
    suspend fun syncSignups(@Body body: SyncRequest): SyncResponse
```

**`res/values/strings.xml`**: paste in the contents of `strings_offline_to_add.xml`.

**`res/layout/activity_main.xml`**: put the banner above the nav host fragment:

```xml
<include layout="@layout/view_offline_banner" />
```

If the root is a `ConstraintLayout`, constrain the banner to the top of the parent and the nav host's top to the banner's bottom. If you're not sure how to do that, paste me `activity_main.xml` and I'll wire it.

**`MainActivity.kt`**: at the end of `onCreate`:

```kotlin
OfflineBanner.bind(this, findViewById(R.id.offlineBanner))
```

with `import com.example.aegis.ui.offline.OfflineBanner`.

**`CommitmentsViewModel.kt`**: reload when a sync finishes and show rejection messages:

```kotlin
init {
    loadCommitments()
    viewModelScope.launch { ServiceLocator.syncState.syncFinished.collect { loadCommitments() } }
    viewModelScope.launch { ServiceLocator.syncState.events.collect { _messages.emit(it) } }
}
```

**`VolunteerHomeViewModel.kt`**: the "signed up" message when it was only queued, and reload the feed when the connection returns:

```kotlin
// in signUp(), the Success branch:
is ApiResult.Success -> {
    _messages.emit(
        if (result.data.pendingSync) "You're offline. We'll sign you up as soon as you reconnect."
        else "You're signed up!"
    )
    loadFeed()
}

// in init { }, after loadFeed():
viewModelScope.launch {
    ServiceLocator.connectivity.online.drop(1).filter { it }.collect { loadFeed() }
}
```

with `import kotlinx.coroutines.flow.drop` and `import kotlinx.coroutines.flow.filter`.

**Commitments card (adapter + `item_commitment` layout):** add an `ImageView` with `@drawable/ic_sync_pending` (id e.g. `pendingIcon`, `visibility="gone"`) and in the adapter's bind: `binding.pendingIcon.visibility = if (signup.pendingSync) View.VISIBLE else View.GONE`. Paste me the adapter and layout if you'd like me to do it.

**Settings screen:** `ServiceLocator.syncState.lastSyncLabel(requireContext())` returns "Last synced: 27 Oct 2026, 15:20" or "Not synced yet". Put it in a TextView.

**Logout:** wherever the app logs out and clears the token, also call:

```kotlin
lifecycleScope.launch { ServiceLocator.clearUserData() }
```

(or `viewModelScope`). Without it, the next account to log in on the same phone would see the previous account's cached sign-ups and queued changes.

## 4. How to test (this is the demo the rubric asks for)

Rubric: "Simulating network disconnection, executing an action in offline mode, and demonstrating successful data synchronisation upon reconnection."

1. Log in as a volunteer **online**. Open Home so the feed loads, then open Commitments.
2. Turn on **airplane mode** (emulator: swipe down, or Extended controls > Cellular). The yellow banner appears: "You're offline. Showing saved data."
3. Home still shows the listings. Tap **I'd like to help** on one. You get the offline message, and the listing appears in Commitments with the pending icon. The banner now says "1 change waiting".
4. Turn airplane mode **off**. The banner changes to "Back online. Syncing...", then disappears. The pending icon goes away.
5. Check the proof: `GET /users/me/signups` in PowerShell, or Firestore console, `signups` collection. The new document's id is the UUID the app made. Logcat filter `SyncEngine` shows the call.
6. Retry test: repeat step 3, then force-stop the app before reconnecting. Reconnect and open the app. It still syncs (WorkManager survives the app being closed).
7. Conflict test: sign up offline for a listing whose last space someone else then takes online. After reconnecting you get "Couldn't sign you up for ...: it filled up while you were offline." and nothing is added to Commitments.

Run the unit tests with `./gradlew testDebugUnitTest` (`OfflineCacheTest`).

## 5. Known gaps

- Hours, organisation screens, and listing create/edit are online-only.
- A rejection message is only shown if the app is open when the sync finishes. The rejected row stays in the database for 24 hours either way.
- Listing spaces shown on Home don't count your queued sign-up until it syncs.
- Afrikaans, isiZulu and isiXhosa versions of the new strings aren't written; they fall back to English.
- `POST /translate` caching ("offline mode shows the last-fetched language" in the Part 1 plan) isn't built, because `/translate` isn't built yet.