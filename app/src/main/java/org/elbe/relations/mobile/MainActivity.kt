package org.elbe.relations.mobile

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_main.*
import org.elbe.relations.mobile.cloud.CloudSynchronize
import org.elbe.relations.mobile.data.RelationsDataBase
import org.elbe.relations.mobile.preferences.CloudProviders
import org.elbe.relations.mobile.preferences.SettingsActivity
import org.elbe.relations.mobile.search.SearchUI
import org.elbe.relations.mobile.tabs.*
import org.elbe.relations.mobile.util.RetrieveListHelper

const val EXTRA_ITEM = "org.elbe.relations.mobile.ITEM"
const val EXTRA_QUERY = "org.elbe.relations.mobile.QUERY"
const val EXTRA_QUERY_FLAG = "org.elbe.relations.mobile.QUERY.FLAG"

/**
 * Initial view of the Relations Mobile app.
 */
class MainActivity : AppCompatActivity() {
    private var mHelper: RetrieveListHelper? = null
    private val mTabsAdapter: TabsFragmentPagerAdapter = TabsFragmentPagerAdapter(supportFragmentManager, this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        searchUI.getSearchView()?.setOnQueryTextListener(searchUI.createQueryListener(menu?.findItem(R.id.action_search)))

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
            R.id.action_synchronize -> CloudSynchronize.synchronize(this, resources)
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        mHelper?.quit()
        mHelper = null
        RelationsDataBase.destroyInstance()
        super.onDestroy()
    }

}
