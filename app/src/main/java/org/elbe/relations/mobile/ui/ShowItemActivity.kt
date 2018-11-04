@file:Suppress("NAME_SHADOWING")
package org.elbe.relations.mobile.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.PersistableBundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup

import kotlinx.android.synthetic.main.content_toolbar.*
import org.elbe.relations.mobile.EXTRA_ITEM
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.cloud.CloudSynchronize
import org.elbe.relations.mobile.cloud.GoogleDriveService
import org.elbe.relations.mobile.model.MinItem
import org.elbe.relations.mobile.preferences.SettingsActivity
import org.elbe.relations.mobile.search.SearchUI
import org.elbe.relations.mobile.util.RetrieveListHelper

private const val TAG = "ShowItemActivity"

/**
 * Activity to display a selected term item.
 * This activity manages both the ItemDetailsFragment and the ItemRelatedFragment.
 */
class ShowItemActivity : AppCompatActivity() {
    private var mHelper: RetrieveListHelper? = null
    lateinit var mPager: ViewPager
    lateinit var mItem: MinItem
    private val mGoogleDriveService: GoogleDriveService by lazy {
        GoogleDriveService(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mHelper = RetrieveListHelper(this, "showItem")

        setContentView(R.layout.activity_show_item)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            val item = intent.getSerializableExtra(EXTRA_ITEM)
            if (item is MinItem) {
                mItem = item
                Log.v(TAG, "Creating activity with item '${item.getTitle()}' (id: ${item.getId()}).")
                if (isLandscape()) {
                    initDetailsView(mItem)
                    initRelatedView(mItem)
                } else {
                    createPager(mItem)
                }
            }
        } else {
            val item = savedInstanceState.getSerializable(EXTRA_ITEM)
            if (item is MinItem) {
                mItem = item
                Log.v(TAG, "Restoring activity with item '${item.getTitle()}' (id: ${item.getId()}).")
                if (isLandscape()) {
                    initDetailsView(mItem)
                    // related fragement is reused by default
                } else {
                    createPager(mItem)
                }
            }
        }
    }

    private fun initDetailsView(item: MinItem) {
        val detailsLayout = findViewById<ViewGroup>(R.id.activity_details_show_container)
        detailsLayout?.let {detailsLayout ->
            Log.v(TAG, "Adding ItemDetailsFragment.")
            val detailsFragment = ItemDetailsFragment.newInstance(item)
            supportFragmentManager.beginTransaction().replace(detailsLayout.id, detailsFragment).commit()
        }
    }

    private fun initRelatedView(item: MinItem) {
        val relatedLayout = findViewById<ViewGroup>(R.id.activity_details_related_container)
        relatedLayout?.let { relatedLayout ->
            Log.v(TAG, "Adding ItemRelatedFragment.")
            val relatedFragment = ItemRelatedFragment.newInstance(item)
            supportFragmentManager.beginTransaction().replace(relatedLayout.id, relatedFragment).commit()
        }
    }

    private fun createPager(item: MinItem) {
        Log.v(TAG, "Creating activity with fragments in pager.")
        mHelper?.let { db ->
            db.run(Runnable {
                val fullItem = db.getItem(item)
                mPager = findViewById(R.id.itemDetailPager)
                mPager.adapter = ScreenSlidePagerAdapter(supportFragmentManager, fullItem)
            })
        }
    }

    private fun isLandscape(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
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

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        mItem.let {item ->
            Log.v(TAG, "onSaveInstanceState: saving item '${item.getTitle()}' (id: ${item.getId()}).")
            outState?.putSerializable(EXTRA_ITEM, item)
        }
    }

    override fun onDestroy() {
        mHelper?.quit()
        mHelper = null
        super.onDestroy()
    }

//    ---- inner classes

    /**
     * https://developer.android.com/training/animation/screen-slide
     */
    class ScreenSlidePagerAdapter constructor(fm: FragmentManager, item : MinItem) : FragmentStatePagerAdapter(fm) {
        private val mItem: MinItem = item

        override fun getItem(position: Int): Fragment {
            Log.v(TAG, "ScreenSlidePagerAdapter.getItem: position is $position.")
            return if (position == 0) ItemDetailsFragment.newInstance(mItem) else ItemRelatedFragment.newInstance(mItem)
        }

        override fun getCount(): Int {
            return 2
        }
    }

}
