package org.elbe.relations.mobile.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import org.elbe.relations.mobile.R

import kotlinx.android.synthetic.main.activity_show_related.*
import org.elbe.relations.mobile.cloud.CloudSynchronize
import org.elbe.relations.mobile.cloud.GoogleDriveService
import org.elbe.relations.mobile.preferences.SettingsActivity
import org.elbe.relations.mobile.search.SearchUI
import org.elbe.relations.mobile.util.RetrieveListHelper

/**
 * Activity to display an item's relations.
 */
class ShowRelatedActivity : AppCompatActivity() {
    private var mHelper: RetrieveListHelper? = null
    private val mGoogleDriveService: GoogleDriveService by lazy {
        GoogleDriveService(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.v("ShowRelatedActivity", ">>> onCreate: 1")
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // If the screen is now in landscape mode, we can show the
            // dialog in-line with the list so we don't need this activity.
            Log.v("ShowRelatedActivity", ">>> onCreate: landscape -> finishing")
            finish()
            return
        }

        Log.v("ShowRelatedActivity", ">>> onCreate: portrait -> 2")
        setContentView(R.layout.activity_show_related)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mHelper = RetrieveListHelper(this, "showRelated")

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            finish()
            return
        }
        if (savedInstanceState == null) {
            val related = ItemRelatedFragment.newInstance(null)
            supportFragmentManager.beginTransaction().add(R.id.item_relate_fragment_container, related).commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchUI = SearchUI(this, resources).setViewFromMenu(menu)
        searchUI.getSearchView()?.setOnQueryTextListener(searchUI.createQueryListener())

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            R.id.action_synchronize -> CloudSynchronize.synchronize(this, resources, mGoogleDriveService)
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        mHelper?.quit()
        mHelper = null
        super.onDestroy()
    }

}
