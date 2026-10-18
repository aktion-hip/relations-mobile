package org.elbe.relations.mobile.util

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.cloud.FoundComputer
import org.elbe.relations.mobile.cloud.SyncRunner
import org.elbe.relations.mobile.p2p.PeerAddress

private const val ARG_EDITABLE = "editable"
private const val STATE_SELECTED = "selected"
/** The last connection string used in a debug build. */
private const val P2P_DEBUG_CONNECT_ADDRESS = "p2pDebugConnectAddress"
private val IP4_TCP = Regex("^/ip4/([\\d.]+)/tcp/(\\d+)$")

/**
 * Lists the Relations desktop applications found on the local network and lets the user connect to one.
 * In debug builds, the user can also enter or edit the connection string.
 */
class SelectComputerDialog : DialogFragment() {
    private var mComputers: List<FoundComputer> = emptyList()
    private var mSelected: String? = null
    private var mList: ListView? = null
    private var mInput: EditText? = null
    private var mAdapter: ArrayAdapter<String>? = null
    private var mRequested = false

    private val editable: Boolean
        get() = requireArguments().getBoolean(ARG_EDITABLE)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mSelected = savedInstanceState?.getString(STATE_SELECTED)
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_select_computer, null)
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_single_choice, mutableListOf())
        mAdapter = adapter
        val list = view.findViewById<ListView>(R.id.list_p2p_computers)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val computer = mComputers.getOrNull(position) ?: return@setOnItemClickListener
            mSelected = computer.peerId
            mInput?.let {
                it.setText(computer.preferred)
                it.error = null
            }
            updateButton()
        }
        mList = list
        val input = view.findViewById<EditText>(R.id.edit_p2p_connection_string)
        if (editable) {
            input.visibility = android.view.View.VISIBLE
            // after a rotation, the view state restores the edited text
            if (savedInstanceState == null) {
                input.setText(PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(P2P_DEBUG_CONNECT_ADDRESS, ""))
            }
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = updateButton()
            })
            mInput = input
        }
        val dialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.p2p_select_title)
                .setView(view)
                .setPositiveButton(R.string.p2p_connect, null)
                .setNegativeButton(android.R.string.cancel) { _, _ -> SyncRunner.instance.cancel() }
                .create()
        dialog.setOnShowListener {
            // replaces the default listener, which would close the dialog also for an invalid entry
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { connect() }
            showComputers()
        }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED, mSelected)
    }

    override fun onDestroyView() {
        mList = null
        mInput = null
        mAdapter = null
        super.onDestroyView()
    }

    /**
     * @param computers List<FoundComputer> the computers found so far, the selection and the entered text are kept
     */
    fun update(computers: List<FoundComputer>) {
        mComputers = computers
        showComputers()
    }

    private fun showComputers() {
        val adapter = mAdapter ?: return
        adapter.clear()
        adapter.addAll(mComputers.map { label(it) })
        val position = mComputers.indexOfFirst { it.peerId == mSelected }
        mList?.clearChoices()
        // the list only grows, i.e. a selected computer stays (after a rotation, it appears with the next update)
        if (position >= 0) {
            mList?.setItemChecked(position, true)
        }
        updateButton()
    }

    private fun label(computer: FoundComputer): String {
        val address = IP4_TCP.matchEntire(computer.addresses.first())?.let { "${it.groupValues[1]}:${it.groupValues[2]}" }
                ?: computer.addresses.first()
        return "$address · …${computer.peerId.takeLast(6)}"
    }

    private fun updateButton() {
        val button = (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE) ?: return
        val input = mInput
        button.isEnabled = !mRequested && if (input != null) input.text.isNotBlank() else mSelected != null
    }

    private fun connect() {
        val input = mInput
        if (input == null) {
            val selected = mSelected ?: return
            mRequested = true
            SyncRunner.instance.connectTo(computer = selected)
        } else {
            val text = input.text.toString()
            val address = PeerAddress.parse(text)
            if (address == null) {
                input.error = getString(R.string.p2p_connection_string_invalid)
                return
            }
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putString(P2P_DEBUG_CONNECT_ADDRESS, text.trim()).apply()
            mRequested = true
            SyncRunner.instance.connectTo(address = address.toString())
        }
        updateButton()
    }

    companion object {
        /**
         * @param editable Boolean true to show the connection string (debug builds)
         */
        fun newInstance(editable: Boolean): SelectComputerDialog = SelectComputerDialog().apply {
            arguments = Bundle().apply { putBoolean(ARG_EDITABLE, editable) }
            isCancelable = false
        }
    }
}
