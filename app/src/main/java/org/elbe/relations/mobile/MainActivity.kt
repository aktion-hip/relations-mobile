package org.elbe.relations.mobile

import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.google.android.material.tabs.TabLayout
import androidx.viewpager.widget.ViewPager
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

import androidx.appcompat.widget.Toolbar
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.search.SearchUI
import org.elbe.relations.mobile.tabs.*
import org.elbe.relations.mobile.util.RetrieveListHelper
import org.elbe.relations.mobile.util.SyncObserver
import org.elbe.relations.mobile.util.applyEdgeToEdge
import org.elbe.relations.mobile.util.Utils

const val EXTRA_ITEM = "org.elbe.relations.mobile.ITEM"
const val EXTRA_QUERY = "org.elbe.relations.mobile.QUERY"
const val EXTRA_QUERY_FLAG = "org.elbe.relations.mobile.QUERY.FLAG"
const val PREF_USER_FIRST_TIME = "user_first_time"

/**
 * Initial view of the Relations Mobile app.
 */
class MainActivity : AppCompatActivity() {
    private var mHelper: RetrieveListHelper? = null
    private val mTabsAdapter: TabsFragmentPagerAdapter = TabsFragmentPagerAdapter(supportFragmentManager, this)

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
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        SyncObserver.observe(this)
        applyEdgeToEdge()

        val tabsViewer = findViewById<ViewPager>(R.id.relation_tabs_views)
        tabsViewer.adapter = mTabsAdapter

        val tabLayout = findViewById<TabLayout>(R.id.relation_tabs)
        tabLayout.setupWithViewPager(tabsViewer)

        if (intent.getBooleanExtra(EXTRA_QUERY_FLAG, false)) {
            val query = intent.getStringExtra(EXTRA_QUERY)
            val searchUI = SearchUI(this, resources)
            query?.let { searchUI.process(it) }
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
        return Utils.runOptions(item, this) {
            item ->  super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        mHelper?.quit()
        mHelper = null
        RelationsDataBase.destroyInstance()
        super.onDestroy()
    }

}
