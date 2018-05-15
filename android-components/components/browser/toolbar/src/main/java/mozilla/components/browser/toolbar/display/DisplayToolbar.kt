/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.toolbar.display

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.support.ktx.android.view.dp
import mozilla.components.support.ktx.android.view.isVisible
import mozilla.components.ui.progress.AnimatedProgressBar
import android.util.TypedValue
import mozilla.components.browser.menu.BrowserMenuBuilder

/**
 * Sub-component of the browser toolbar responsible for displaying the URL and related controls.
 *
 * Structure:
 * +------+-----+--------------------+---------+------+
 * | icon | nav |         url        | actions | menu |
 * +------+-----+--------------------+---------+------+
 *
 * - icon: Security indicator, usually a lock or globe icon.
 * - nav: Optional navigation buttons (back/forward) to be displayed on large devices like tablets
 * - url: The URL of the currently displayed website (read-only). Clicking this element will switch
 *        to editing mode.
 * - actions: Optional (page) action icons injected by other components (e.g. reader mode).
 * - menu: three dot menu button that opens the browser menu.
 */
@SuppressLint("ViewConstructor") // This view is only instantiated in code
internal class DisplayToolbar(
    context: Context,
    val toolbar: BrowserToolbar
) : ViewGroup(context) {
    internal var menuBuilder: BrowserMenuBuilder? = null
        set(value) {
            field = value
            menuView.visibility = if (value == null)
                View.GONE
            else
                View.VISIBLE
        }

    private val iconView = ImageView(context).apply {
        val padding = dp(ICON_PADDING_DP)
        setPadding(padding, padding, padding, padding)

        setImageResource(mozilla.components.ui.icons.R.drawable.mozac_ic_globe)
    }

    private val urlView = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        textSize = URL_TEXT_SIZE
        setFadingEdgeLength(URL_FADING_EDGE_SIZE_DP)
        isHorizontalFadingEdgeEnabled = true

        setSingleLine(true)

        setOnClickListener {
            toolbar.editMode()
        }
    }

    private val menuView = ImageButton(context).apply {
        val padding = dp(MENU_PADDING_DP)
        setPadding(padding, padding, padding, padding)

        setImageResource(mozilla.components.ui.icons.R.drawable.mozac_ic_menu)

        val outValue = TypedValue()
        context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                outValue,
                true)

        setBackgroundResource(outValue.resourceId)
        visibility = View.GONE

        setOnClickListener {
            menuBuilder?.build(context)?.show(this)
        }
    }

    private val progressView = AnimatedProgressBar(context)

    init {
        addView(iconView)
        addView(urlView)
        addView(menuView)
        addView(progressView)
    }

    /**
     * Updates the URL to be displayed.
     */
    fun updateUrl(url: String) {
        urlView.text = url
    }

    /**
     * Updates the progress to be displayed.
     */
    fun updateProgress(progress: Int) {
        progressView.progress = progress

        progressView.visibility = if (progress < progressView.max) View.VISIBLE else View.GONE
    }

    // We measure the views manually to avoid overhead by using complex ViewGroup implementations
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // This toolbar is using the full size provided by the parent
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        setMeasuredDimension(width, height)

        // The icon and menu fill the whole height and have a square shape
        val squareSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        iconView.measure(squareSpec, squareSpec)
        menuView.measure(squareSpec, squareSpec)

        // The url uses whatever space is left. Substract the icon and (optionally) the menu
        val urlWidth = width - height - if (menuView.isVisible()) height else 0
        val urlWidthSpec = MeasureSpec.makeMeasureSpec(urlWidth, MeasureSpec.EXACTLY)
        urlView.measure(urlWidthSpec, heightMeasureSpec)

        val progressHeightSpec = MeasureSpec.makeMeasureSpec(dp(PROGRESS_BAR_HEIGHT_DP), MeasureSpec.EXACTLY)
        progressView.measure(widthMeasureSpec, progressHeightSpec)
    }

    // We layout the toolbar ourselves to avoid the overhead from using complex ViewGroup implementations
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        iconView.layout(left, top, left + iconView.measuredWidth, bottom)

        val urlRight = right - if (menuView.isVisible()) height else 0
        urlView.layout(left + iconView.measuredWidth, top, urlRight, bottom)

        progressView.layout(left, bottom - progressView.measuredHeight, right, bottom)

        menuView.layout(right - menuView.measuredWidth, top, right, bottom)
    }

    companion object {
        private const val ICON_PADDING_DP = 16
        private const val MENU_PADDING_DP = 16
        private const val URL_TEXT_SIZE = 15f
        private const val URL_FADING_EDGE_SIZE_DP = 24
        private const val PROGRESS_BAR_HEIGHT_DP = 3
    }
}
