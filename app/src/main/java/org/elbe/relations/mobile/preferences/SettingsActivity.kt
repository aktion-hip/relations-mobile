package org.elbe.relations.mobile.preferences

import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceScreen
import org.elbe.relations.mobile.R

/**
 * The activity to display the settings, i.e. preferences page.
 *
 * see http://www.programmierenlernenhq.de/tutorial-android-settings-preferences-und-einstellungen/
 */
class SettingsActivity: AppCompatPreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActionBar()
        fragmentManager.beginTransaction().replace(android.R.id.content, SettingsFragment()).commit()
    }

    private fun setupActionBar() {
        val actionBar = getSupportActionBar()
        actionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowHomeEnabled(true)
        }
    }

    //---
    /**
     * @see https://stackoverflow.com/questions/21440685/show-up-button-in-actionbar-in-subscreen-preferences
     */
    class SettingsFragment: PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.preferences)

            val preferenceScreen = findPreference(getString(R.string.key_preference_cloud))
            if (preferenceScreen is PreferenceScreen) {
                preferenceScreen.setOnPreferenceClickListener {
                    if (it is PreferenceScreen) {
                        TODO("it.dialog.actionBar is NULL")
//                        it.dialog.actionBar.setDisplayHomeAsUpEnabled(true)
                    }

                    true
                }
            }
        }
    }
}