package org.elbe.relations.mobile.preferences

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.util.applyEdgeToEdge

/**
 * The activity to display the settings, i.e. preferences page.
 */
class SettingsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(hasDecorActionBar = true)
        setupActionBar()
        supportFragmentManager.beginTransaction().replace(android.R.id.content, SettingsFragment()).commit()
    }

    private fun setupActionBar() {
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowHomeEnabled(true)
        }
    }

    //---
    class SettingsFragment: PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preferences)
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            if (parentFragmentManager.findFragmentByTag(DIALOG_TAG) != null) {
                return
            }
            val dialogFragment: DialogFragment? = when (preference) {
                is CloudConfigPreference -> CloudConfigPreferenceDialogFragmentCompat.newInstance(preference.key)
                is AppInfo -> AppInfoFragment.newInstance()
                else -> null
            }
            if (dialogFragment != null) {
                // PreferenceDialogFragmentCompat still looks up its preference through the target fragment
                @Suppress("DEPRECATION")
                dialogFragment.setTargetFragment(this, 0)
                dialogFragment.show(parentFragmentManager, DIALOG_TAG)
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }

        companion object {
            private const val DIALOG_TAG = "androidx.preference.PreferenceFragment.DIALOG"
        }
    }
}