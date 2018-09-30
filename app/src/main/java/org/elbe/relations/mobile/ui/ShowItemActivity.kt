package org.elbe.relations.mobile.ui

import android.content.Intent
import android.os.Bundle
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
import org.elbe.relations.mobile.model.Item
import org.elbe.relations.mobile.model.MinItem
import org.elbe.relations.mobile.preferences.SettingsActivity
import org.elbe.relations.mobile.search.SearchUI
import org.elbe.relations.mobile.util.RetrieveListHelper

private const val TAG = "ShowItemActivity"

/**
 * Activity to display a selected term item.
 * This activity manages both the ItemDetailsFragment and the ItemRelatedFragment.
 */
class ShowItemActivity : AppCompatActivity(), ItemDetailsFragment.OnFragmentInteractionListener {
    private var mHelper: RetrieveListHelper? = null
    lateinit var mPager: ViewPager
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
            var item = intent.getSerializableExtra(EXTRA_ITEM)
            if (item is MinItem) {

                // check placeholder for related fragment
                var relatedLayout = findViewById<ViewGroup>(R.id.activity_details_related_container)
                if (relatedLayout == null) {
                    // portrait case: we need pager to slide from details to related
                    mHelper?.let { db ->
                        db.run(Runnable {
                            val fullItem = db.getItem(item)
                            mPager = findViewById(R.id.itemDetailPager)
                            mPager.adapter = ScreenSlidePagerAdapter(supportFragmentManager, fullItem)
                        })
                    }
                } else {
                    // landscape case with two views side by side
                    var detailsLayout = findViewById<ViewGroup>(R.id.activity_details_show_container)
                    detailsLayout?.let {detailsLayout ->
                        Log.v(TAG, "onCreate: adding ItemDetailsFragment.")
                        var detailsFragment = ItemDetailsFragment.newInstance(item)
                        supportFragmentManager.beginTransaction().replace(detailsLayout.id, detailsFragment).commit()
                    }

                    Log.v(TAG, "onCreate: adding ItemRelatedFragment.")
                    var relatedFragment = ItemRelatedFragment.newInstance(item)
                    supportFragmentManager.beginTransaction().replace(relatedLayout.id, relatedFragment).commit()
                }
            }
        }
    }

    /**
     * Method to interact with ItemDetailsFragment.
     */
    override fun onShowFragment(item: Item) {
//        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentItemDetails)
//        if (fragment is ItemDetailsFragment) {
//            fragment.showItem(item, fragment.view!!)
//        } else {
//            val newFragment = ItemDetailsFragment.newInstance(item)
//            val transaction = supportFragmentManager.beginTransaction()
//            transaction.replace(R.id.item_details_fragment_container, newFragment)
//            transaction.addToBackStack(null);
//            transaction.commit()
//        }
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
