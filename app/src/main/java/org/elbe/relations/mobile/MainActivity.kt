package org.elbe.relations.mobile

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem

import kotlinx.android.synthetic.main.content_toolbar.*
import org.elbe.relations.mobile.cloud.CloudSynchronize
import org.elbe.relations.mobile.cloud.GoogleDriveService
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.preferences.SettingsActivity
import org.elbe.relations.mobile.search.SearchUI
import org.elbe.relations.mobile.tabs.*
import org.elbe.relations.mobile.util.RetrieveListHelper

const val EXTRA_ITEM = "org.elbe.relations.mobile.ITEM"
const val EXTRA_QUERY = "org.elbe.relations.mobile.QUERY"
const val EXTRA_QUERY_FLAG = "org.elbe.relations.mobile.QUERY.FLAG"
const val PREF_USER_FIRST_TIME = "user_first_time"
private const val TAG = "MainActivity"

/**
 * Initial view of the Relations Mobile app.
 */
class MainActivity : AppCompatActivity() {
    private var mHelper: RetrieveListHelper? = null
    private val mTabsAdapter: TabsFragmentPagerAdapter = TabsFragmentPagerAdapter(supportFragmentManager, this)
    private val mGoogleDriveService: GoogleDriveService by lazy {
        GoogleDriveService(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firstTimeUser = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_USER_FIRST_TIME, true)
        if (firstTimeUser) {
            val introIntent = Intent(this, IntroActivity::class.java)
            introIntent.putExtra(PREF_USER_FIRST_TIME, firstTimeUser)
            startActivity(introIntent)
        }

        mHelper = RetrieveListHelper(this, "main")

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val tabsViewer = findViewById<ViewPager>(R.id.relation_tabs_views)
        tabsViewer.adapter = mTabsAdapter

        val tabLayout = findViewById<TabLayout>(R.id.relation_tabs)
        tabLayout.setupWithViewPager(tabsViewer)

        if (intent.getBooleanExtra(EXTRA_QUERY_FLAG, false)) {
            val query = intent.getStringExtra(EXTRA_QUERY)
            val searchUI = SearchUI(this, resources)
            searchUI.process(query)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchUI = SearchUI( this, resources).setViewFromMenu(menu)
        searchUI.getSearchView()?.setOnQueryTextListener(searchUI.createQueryListener(menu.findItem(R.id.action_search)))

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            R.id.action_synchronize -> CloudSynchronize.synchronize(this, resources, mGoogleDriveService)
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.v(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        if (requestCode == GoogleDriveService.REQUEST_CODE_SIGN_IN) {
            CloudSynchronize.synchronizeFromGoogleDrive(this, resources, mGoogleDriveService, data)
        }
    }

    override fun onDestroy() {
        mHelper?.quit()
        mHelper = null
        RelationsDataBase.destroyInstance()
        super.onDestroy()
    }

}
