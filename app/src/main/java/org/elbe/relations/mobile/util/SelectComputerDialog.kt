package org.elbe.relations.mobile.util

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.cloud.SyncRunner
import org.elbe.relations.mobile.p2p.PeerAddress

/**
 * Asks for the Relations desktop application's address: scanned from the QR code the desktop shows, pasted or typed,
 * see design.md of the change reverse-p2p-roles (Decision 6).
 *
 * The same in all builds. The field is empty every time, the address is not remembered.
 */
class SelectComputerDialog : DialogFragment() {
    private var mInput: EditText? = null
    private var mRequested = false

    /** Registered before the fragment is created, so that the result reaches it also after a rotation. */
    private val mScanner: ActivityResultLauncher<ScanOptions> =
            registerForActivityResult(ScanContract()) { result -> onScanned(result) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_select_computer, null)
        val input = view.findViewById<EditText>(R.id.edit_p2p_connection_string)
        // after a rotation, the view state restores the entered text
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateButton()
        })
        mInput = input
        view.findViewById<Button>(R.id.button_p2p_scan).setOnClickListener { scan() }
        val dialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.p2p_select_title)
                .setView(view)
                .setPositiveButton(R.string.p2p_connect, null)
                .setNegativeButton(android.R.string.cancel) { _, _ -> SyncRunner.instance.cancel() }
                .create()
        dialog.setOnShowListener {
            // replaces the default listener, which would close the dialog also for an invalid entry
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { connect() }
            updateButton()
        }
        return dialog
    }

    override fun onDestroyView() {
        mInput = null
        super.onDestroyView()
    }

    /** ZXing's scan screen requests the camera permission itself. */
    private fun scan() {
        mScanner.launch(ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.p2p_scan_prompt))
                .setOrientationLocked(false)
                .setBeepEnabled(false))
    }

    /** A valid address connects right away, anything else is shown with the error. A canceled scan changes nothing. */
    private fun onScanned(result: ScanIntentResult) {
        val text = result.contents ?: return
        val address = PeerAddress.parse(text)
        if (address != null) {
            mInput?.setText(address.toString())
            request(address)
        } else {
            mInput?.let {
                it.setText(text)
                it.error = getString(R.string.p2p_connection_string_invalid)
            }
        }
    }

    private fun connect() {
        val input = mInput ?: return
        val address = PeerAddress.parse(input.text.toString())
        if (address == null) {
            input.error = getString(R.string.p2p_connection_string_invalid)
            return
        }
        request(address)
    }

    private fun request(address: PeerAddress) {
        mRequested = true
        updateButton()
        SyncRunner.instance.connectTo(address.toString())
    }

    private fun updateButton() {
        val button = (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE) ?: return
        button.isEnabled = !mRequested && mInput?.text?.isNotBlank() == true
    }

    companion object {
        fun newInstance(): SelectComputerDialog = SelectComputerDialog().apply {
            isCancelable = false
        }
    }
}
