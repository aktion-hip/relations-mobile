package org.elbe.relations.mobile.search

import android.content.Intent
import android.content.res.Resources
import android.os.Handler
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SearchView
import android.view.Menu
import android.view.MenuItem
import org.apache.lucene.document.Document
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.TopDocs
import org.elbe.relations.mobile.EXTRA_QUERY
import org.elbe.relations.mobile.EXTRA_QUERY_FLAG
import org.elbe.relations.mobile.MainActivity
import org.elbe.relations.mobile.R
import org.elbe.relations.mobile.model.MinItem
import org.elbe.relations.mobile.model.RetrievedItem
import org.elbe.relations.mobile.util.UniqueID

/**
 * Class providing the functionality to process the search query the user entered.
 * Note: the search might be called from the main activity as well as from the show details or show related activity!
 */
class SearchUI(context: AppCompatActivity, r: Resources) {
    private val mContext = context
    private var mSearchView: SearchView? = null
    private val mHandler = Handler()
    private val mIndexReaderFactory = IndexReaderFactory(context, r)

    /**
     * @return SearchView configured in the menu
     */
    fun getSearchView(): SearchView? {
        return mSearchView
    }

    /**
     * @param menu: Menu?
     * @return SearchUI
     */
    fun setViewFromMenu(menu: Menu?): SearchUI {
        mSearchView = menu?.findItem(R.id.action_search)?.actionView as SearchView
        return this
    }

    /**
     * @param menuItem: MenuItem? the search view's menu item
     * @return OnQueryTextListener the listener to process the search query entered by the user
     */
    fun createQueryListener(menuItem: MenuItem?): SearchView.OnQueryTextListener {
        return object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                mSearchView?.isIconified = true
                mSearchView?.clearFocus()
                process(query)
                menuItem?.collapseActionView()
                return true
            }
        }
    }

    /**
     * Process the specified query, i.e. by doing the full text search and displaying the search result.
     * Note: we need this function public to call it from MainActivity.onCreate(), i.e. when the search term is entered in child activities.
     *
     * @param query: String the search query
     */
    fun process(query: String) {
        mIndexReaderFactory.createIndexReader().use { reader ->
            val searcher = IndexSearcher(reader)
            val docs = searcher.search(parseQuery(query), MAX_HITS)
            SearchCache.setResult(createResults(docs, searcher))
        }
        showResult()
    }

    private fun showResult() {
        val tabLayout = mContext.findViewById<TabLayout>(R.id.relation_tabs)
        val existing = mContext.supportFragmentManager.fragments
        val retrieved = retrieveSearchFragment(existing)
        if (retrieved == null) {
            selectSearchTag(tabLayout)
        } else {
            mContext.supportFragmentManager.beginTransaction().detach(retrieved).attach(retrieved).commit()
            if (tabLayout.selectedTabPosition != 3) {
                selectSearchTag(tabLayout)
            }
        }
    }

    private fun selectSearchTag(tabLayout: TabLayout) {
            val searchTab = tabLayout.getTabAt(3)
            searchTab?.let {searchTab ->
                mHandler.post {
                    searchTab.select()
                }
            }
    }

    private fun retrieveSearchFragment(existing: List<Fragment>): Fragment? {
        val tag = "android:switcher:" + R.id.relation_tabs_views + ":" + 3
        existing.forEach {fragment ->
            if (tag.equals(fragment.tag)) {
                return fragment
            }
        }
        return null
    }

    private fun createResults(docs: TopDocs, searcher: IndexSearcher): List<MinItem> {
        val searchResult: MutableList<MinItem> = mutableListOf()
        val scores = docs.scoreDocs
        scores.forEach {
            val docId = it.doc
            val doc = searcher.doc(docId)
            searchResult.add(getItem(doc))
        }
        return searchResult
    }

    private fun getItem(doc: Document): MinItem {
        return RetrievedItem(UniqueID(doc.get("uniqueID")), doc.get("itemTitle"))
    }

    private fun parseQuery(query: String): Query {
        val parser = QueryParser(LUCENE_VERSION, INDX_CONTENT_FULL, mIndexReaderFactory.getAnalyzer())
        return parser.parse(query)
    }

    /**
     * @return OnQueryTextListener the listener to switch to the main activity and triggering the search there
     */
    fun createQueryListener(): SearchView.OnQueryTextListener {
        return object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                val intent = Intent(mContext, MainActivity::class.java).apply {
                    putExtra(EXTRA_QUERY_FLAG, true)
                    putExtra(EXTRA_QUERY, query)
                }
                mContext.startActivityIfNeeded(intent, -1)
                return true
            }
        }
    }

}