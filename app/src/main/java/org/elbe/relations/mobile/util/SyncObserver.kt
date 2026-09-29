package org.elbe.relations.mobile.util

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.elbe.relations.mobile.MainActivity
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.cloud.SyncRunner
import org.elbe.relations.mobile.cloud.SyncState

private const val DIALOG_TAG = "fragment_download"
private const val CONFIRM_TAG = "fragment_confirm_peer"

/**
 * Displays the state of the data synchronization (see SyncRunner) in an activity.
 */
object SyncObserver {

    /**
     * Observes the synchronization while the activity is started, call in onCreate().
     */
    fun observe(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SyncRunner.instance.state.collect { state -> render(activity, state) }
            }
        }
    }

    private fun render(activity: AppCompatActivity, state: SyncState) {
        val fm = activity.supportFragmentManager
        val dialog = fm.findFragmentByTag(DIALOG_TAG) as? ProgressDialog
        val confirm = fm.findFragmentByTag(CONFIRM_TAG) as? ConfirmPeerDialog
        if (state !is SyncState.ConfirmPeer) {
            confirm?.dismissAllowingStateLoss()
        }
        when (state) {
            is SyncState.Running -> {
                val progress = showProgress(activity, dialog, activity.getString(R.string.abstract_cloud_provider_dialog_title1))
                // receiving from a peer, i.e. no download: keep the dialog's title while importing
                if (state.max == 0 && state.cancelable) {
                    progress.setTitle(activity.getString(R.string.p2p_receiving))
                }
                progress.showDetail(null)
                progress.showCancel(state.cancelable)
                if (state.max > 0) {
                    progress.showProgress(state.current, state.max, activity.getString(R.string.abstract_cloud_provider_dialog_title2))
                }
            }
            is SyncState.WaitingForPeer -> {
                val progress = showProgress(activity, dialog, activity.getString(R.string.p2p_waiting))
                progress.setTitle(activity.getString(R.string.p2p_waiting))
                progress.showDetail(state.address)
                progress.showCancel(true)
            }
            is SyncState.ConfirmPeer -> {
                dialog?.showCancel(false)
                if (confirm == null) {
                    ConfirmPeerDialog.newInstance(state.endpointName, state.token).showNow(fm, CONFIRM_TAG)
                }
            }
            is SyncState.Done -> {
                dialog?.dismissAllowingStateLoss()
                SyncRunner.instance.acknowledge()
                Toast.makeText(activity, state.message, Toast.LENGTH_LONG).show()
                // display the synchronized data
                activity.finish()
                activity.startActivity(Intent(activity, MainActivity::class.java))
            }
            is SyncState.Failed -> {
                dialog?.dismissAllowingStateLoss()
                SyncRunner.instance.acknowledge()
                Toast.makeText(activity, state.message, Toast.LENGTH_LONG).show()
            }
            SyncState.Idle -> dialog?.dismissAllowingStateLoss()
        }
    }

    private fun showProgress(activity: AppCompatActivity, dialog: ProgressDialog?, title: String): ProgressDialog {
        val progress = dialog ?: ProgressDialog.newInstance(title).also {
            it.isCancelable = false
            // synchronously, so that the next progress update finds the dialog
            it.showNow(activity.supportFragmentManager, DIALOG_TAG)
        }
        // the dialog outlives the activity (e.g. rotation), the runner doesn't hold a reference to it
        progress.onCancel = { SyncRunner.instance.cancel() }
        return progress
    }
}
