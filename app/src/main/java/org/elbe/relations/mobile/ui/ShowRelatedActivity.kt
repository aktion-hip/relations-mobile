package org.elbe.relations.mobile.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import org.elbe.relations.mobile.R

import org.elbe.relations.mobile.databinding.ActivityShowRelatedBinding
import org.elbe.relations.mobile.search.SearchUI
import org.elbe.relations.mobile.util.RetrieveListHelper
import org.elbe.relations.mobile.util.SyncObserver
import org.elbe.relations.mobile.util.applyEdgeToEdge
import org.elbe.relations.mobile.util.Utils

/**
 * Activity to display an item's relations.
 */
class ShowRelatedActivity : AppCompatActivity() {
    private var mHelper: RetrieveListHelper? = null

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
        val binding = ActivityShowRelatedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        SyncObserver.observe(this)
        applyEdgeToEdge()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return Utils.runOptions(item, this) {
            item ->  super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        mHelper?.quit()
        mHelper = null
        super.onDestroy()
    }

}
