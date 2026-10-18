@file:Suppress("NAME_SHADOWING")
package org.elbe.relations.mobile

import androidx.appcompat.app.AppCompatActivity

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView

import org.elbe.relations.mobile.databinding.ActivityIntroBinding
import org.elbe.relations.mobile.databinding.FragmentIntroBinding
import org.elbe.relations.mobile.util.applyEdgeToEdge

private const val PAGES = 4

/**
 * Onboarding activity.
 *
 * see http://blog.grafixartist.com/onboarding-android-viewpager-google-way/
 * see https://github.com/Suleiman19/Android-Material-Design-for-pre-Lollipop/blob/master/MaterialSample/app/src/main/java/com/suleiman/material/activities/PagerActivity.java
 */
class IntroActivity : AppCompatActivity() {

    /**
     * The [androidx.viewpager.widget.PagerAdapter] that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * [androidx.fragment.app.FragmentStatePagerAdapter].
     */
    private var mSectionsPagerAdapter: SectionsPagerAdapter? = null

    private var mViewPager: ViewPager? = null

    private var mNextBtn: ImageButton? = null
    private var mSkipBtn: Button? = null
    private var mFinishBtn: Button? = null

    private var mPage: Int = 0
    private var mIndicators: MutableList<ImageView>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdge()

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)

        mIndicators = mutableListOf(findViewById(R.id.intro_indicator_0), findViewById(R.id.intro_indicator_1), findViewById(R.id.intro_indicator_2), findViewById(R.id.intro_indicator_3))
        updateIndicators(mPage)

        // Set up the ViewPager with the sections adapter.
        mViewPager = binding.introContainer
        mViewPager?.let {pager ->
            pager.adapter = mSectionsPagerAdapter
            pager.currentItem = mPage
            pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    // color update
                }

                override fun onPageSelected(position: Int) {
                    mPage = position
                    updateIndicators(mPage)

                    mNextBtn?.visibility = if (position == PAGES-1) View.GONE else View.VISIBLE
                    mFinishBtn?.visibility = if (position == PAGES-1) View.VISIBLE else View.GONE
                }

                override fun onPageScrollStateChanged(state: Int) {
                    // nothing to do
                }
            })
        }

        mNextBtn = findViewById(R.id.intro_btn_next)
        mNextBtn?.setOnClickListener {
            mPage += 1
            mViewPager?.setCurrentItem(mPage, true)
        }

        mSkipBtn = findViewById(R.id.intro_btn_skip)
        mSkipBtn?.setOnClickListener {
            doFinish(it)
        }

        mFinishBtn = findViewById(R.id.intro_btn_finish)
        mFinishBtn?.setOnClickListener {
            doFinish(it)
        }
    }

    private fun doFinish(view: View) {
        finish()
        val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        val editor = preferences.edit()
        editor.putBoolean(PREF_USER_FIRST_TIME, false)
        editor.apply()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_intro, menu)
        return true
    }

    private fun updateIndicators(position: Int) {
        mIndicators?.forEachIndexed { index, view ->
            view.setBackgroundResource(if (index == position) R.drawable.indicator_selected else R.drawable.indicator_unselected)
        }
    }


    /**
     * A [FragmentPagerAdapter] that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    inner class SectionsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        override fun getItem(position: Int): Fragment {
            // getItem is called to instantiate the fragment for the given mPage.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position)
        }

        override fun getCount(): Int {
            return PAGES
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    class PlaceholderFragment : Fragment() {
        private val mTitles = mutableListOf(R.string.intro_title_1, R.string.intro_title_2, R.string.intro_title_3, R.string.intro_title_4)
        private val mDescs = mutableListOf(R.string.intro_desc_1, R.string.intro_desc_2, R.string.intro_desc_3, R.string.intro_desc_4)
        private val mImgs = mutableListOf(R.drawable.intro1, R.drawable.intro2, R.drawable.intro3, R.drawable.intro4)

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val binding = FragmentIntroBinding.inflate(inflater, container, false)
            val index = arguments?.getInt(ARG_SECTION_NUMBER)
            index?.let {index ->
                binding.sectionImg.setImageResource(mImgs[index])
                binding.sectionLabel.text = getString(mTitles[index])
                binding.sectionDesc.text = getString(mDescs[index])
            }
            return binding.root
        }

        companion object {
            /**
             * The fragment argument representing the section number for this
             * fragment.
             */
            private const val ARG_SECTION_NUMBER = "section_number"

            /**
             * Returns a new instance of this fragment for the given section
             * number.
             */
            fun newInstance(sectionNumber: Int): PlaceholderFragment {
                val fragment = PlaceholderFragment()
                val args = Bundle()
                args.putInt(ARG_SECTION_NUMBER, sectionNumber)
                fragment.arguments = args
                return fragment
            }
        }
    }

}
