package org.elbe.relations.mobile.util

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.AppBarLayout

/**
 * Draws the activity edge-to-edge (enforced when targeting API 35+) and keeps its content out of the system bars:
 * the app bar is extended below the status bar, the rest of the content is padded by the remaining insets.
 * Activities with an app bar must use their own Toolbar in an AppBarLayout (theme AppTheme.NoActionBar):
 * with the theme's action bar, the content is partly hidden below the action bar.
 */
fun AppCompatActivity.applyEdgeToEdge() {
    // light status bar icons on the (colored) app bar
    enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
    val content = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val appBar = findAppBar(view)
        when {
            appBar != null -> {
                appBar.updatePadding(top = bars.top)
                view.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            }
            else -> view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
        }
        WindowInsetsCompat.CONSUMED
    }
}

private fun findAppBar(view: View): AppBarLayout? {
    if (view is AppBarLayout) {
        return view
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            findAppBar(view.getChildAt(i))?.let { return it }
        }
    }
    return null
}
