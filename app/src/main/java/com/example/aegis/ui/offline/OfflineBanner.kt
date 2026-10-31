package com.example.aegis.ui.offline

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.aegis.R
import com.example.aegis.data.ServiceLocator
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The strip at the top of the app:
 *   offline                      "You're offline. Showing saved data. · 2 changes waiting"
 *   back online, still sending   "Back online. Syncing 2 changes waiting…"
 *   online and nothing waiting   hidden
 *
 * Call OfflineBanner.bind(this, findViewById(R.id.offlineBanner)) from MainActivity.onCreate.
 */
object OfflineBanner {

    fun bind(activity: AppCompatActivity, banner: TextView) {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(ServiceLocator.connectivity.online, ServiceLocator.syncState.pendingCount) { online, pending ->
                    online to pending
                }.collect { (online, pending) -> render(activity, banner, online, pending) }
            }
        }
    }

    private fun render(activity: AppCompatActivity, banner: TextView, online: Boolean, pending: Int) {
        val waiting = if (pending > 0) {
            activity.resources.getQuantityString(R.plurals.offline_pending_changes, pending, pending)
        } else {
            null
        }

        when {
            !online -> {
                val offline = activity.getString(R.string.offline_banner_offline)
                banner.text = if (waiting != null) "$offline · $waiting" else offline
                banner.visibility = View.VISIBLE
            }
            waiting != null -> {
                banner.text = activity.getString(R.string.offline_banner_syncing, waiting)
                banner.visibility = View.VISIBLE
            }
            else -> banner.visibility = View.GONE
        }
    }
}