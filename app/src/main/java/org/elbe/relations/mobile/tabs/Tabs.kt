package org.elbe.relations.mobile.tabs

import android.support.v4.app.Fragment
import org.elbe.relations.mobile.R

/**
 * Enum for tabs on main page.
 *
 * see https://github.com/codepath/android_guides/wiki/Google-Play-Style-Tabs-using-TabLayout
 */
enum class Tabs(val title: Int, val icon: Int, val factory: () -> Fragment) {
    TERMS(R.string.tab_terms, R.drawable.ic_term, { AllTermsFragment.newInstance() }),
    TEXTS(R.string.tab_texts, R.drawable.ic_text, { AllTextsFragment.newInstance() }),
    PERSONS(R.string.tab_persons, R.drawable.ic_person, { AllPersonsFragment.newInstance() }),
    SEARCH(R.string.tab_search_result, 0, { AllSearchFragment.newInstance() })
}