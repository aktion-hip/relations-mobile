package org.elbe.relations.mobile.util

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.cloud.SyncRunner

private const val ARG_NAME = "endpointName"
private const val ARG_TOKEN = "token"

/**
 * Asks the user to compare the authentication token with the one on the computer and to accept the connection.
 */
class ConfirmPeerDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        return AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.p2p_confirm_title, args.getString(ARG_NAME)))
                .setMessage(getString(R.string.p2p_confirm_message, args.getString(ARG_TOKEN)))
                .setPositiveButton(R.string.p2p_accept) { _, _ -> SyncRunner.instance.answerPeer(true) }
                .setNegativeButton(R.string.p2p_reject) { _, _ -> SyncRunner.instance.answerPeer(false) }
                .create()
    }

    companion object {
        fun newInstance(endpointName: String, token: String): ConfirmPeerDialog = ConfirmPeerDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_NAME, endpointName)
                putString(ARG_TOKEN, token)
            }
            isCancelable = false
        }
    }
}
