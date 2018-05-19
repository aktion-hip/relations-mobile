package org.elbe.relations.mobile.tabs

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan

/**
 * Adapter for the fragments used for the tabs on the main page.
 */
class TabsFragmentPagerAdapter constructor(fm: FragmentManager, context: Context): FragmentPagerAdapter(fm) {
    val tabs: Array<Tabs> = arrayOf(Tabs.TERMS, Tabs.TEXTS, Tabs.PERSONS, Tabs.SEARCH)
    val context: Context = context

    override fun getCount(): Int {
        return tabs.size
    }

    override fun getItem(position: Int): Fragment {
        return tabs[position].factory.invoke()
    }

    override fun getPageTitle(position: Int): CharSequence {
        val iconId = tabs[position].icon
        // no icon, display text only
        if (iconId == 0) {
            return context.resources.getString(tabs[position].title)
        }
        // with icon
        val image = getImage(iconId)
        image.setBounds(0, 0, 32, 32);
        // Replace blank spaces with image icon
        val spannable = SpannableString("   " + context.resources.getString(tabs[position].title))
        val imageSpan = ImageSpan(image, ImageSpan.ALIGN_BASELINE)
        spannable.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    @Suppress("DEPRECATION")
    private fun getImage(iconId: Int): Drawable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return context.resources.getDrawable(iconId, context.theme)
        }
        return context.resources.getDrawable(iconId)
    }

    /**
     * Returns the fragment showing the search result.
     */
    fun getSearchFragment(): Fragment {
        return tabs[3].factory.invoke()
    }

}