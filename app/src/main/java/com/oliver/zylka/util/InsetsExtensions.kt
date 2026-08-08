package com.oliver.zylka.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Since API 35 the system draws edge-to-edge by default and no longer honors
 * `android:statusBarColor`/`android:windowLightStatusBar` - the toolbar's background now
 * extends up underneath the status bar, but its *content* (title, icons) needs to be
 * pushed down by hand or it sits behind the clock/battery indicators. Call this once on
 * each screen's toolbar (which must use `layout_height="wrap_content"` +
 * `minHeight="?attr/actionBarSize"` so it can grow to fit the inset).
 */
fun View.applyStatusBarTopInset() {
    val initialPadding = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.updatePadding(top = initialPadding + statusBarInset)
        insets
    }
}

/** Same idea for scrollable content that reaches the bottom edge (gesture nav bar). */
fun View.applyNavigationBarBottomInset() {
    val initialPadding = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        view.updatePadding(bottom = initialPadding + navigationBarInset)
        insets
    }
}
