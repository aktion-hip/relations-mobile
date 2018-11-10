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
 * Adapter for the fragments used for the mTabs on the main page.
 */
class TabsFragmentPagerAdapter constructor(fm: FragmentManager, context: Context): FragmentPagerAdapter(fm) {
    private val mTabs: Array<Tabs> = arrayOf(Tabs.TERMS, Tabs.TEXTS, Tabs.PERSONS, Tabs.SEARCH)
    private val mContext: Context = context

    override fun getCount(): Int {
        return mTabs.size
    }

    override fun getItem(position: Int): Fragment {
        return mTabs[position].factory.invoke()
    }

    override fun getPageTitle(position: Int): CharSequence {
        val iconId = mTabs[position].icon
        // no icon, display text only
        if (iconId == 0) {
            return mContext.resources.getString(mTabs[position].title)
        }
        // with icon
        val image = getImage(iconId)
        image.setBounds(0, 0, 32, 32)
        // Replace blank spaces with image icon
        val spannable = SpannableString("   " + mContext.resources.getString(mTabs[position].title))
        val imageSpan = ImageSpan(image, ImageSpan.ALIGN_BASELINE)
        spannable.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    @Suppress("DEPRECATION")
    private fun getImage(iconId: Int): Drawable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return mContext.resources.getDrawable(iconId, mContext.theme)
        }
        return mContext.resources.getDrawable(iconId)
    }

}