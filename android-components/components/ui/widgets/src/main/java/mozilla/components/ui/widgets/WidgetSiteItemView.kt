package mozilla.components.ui.widgets

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible

/**
 * Shared UI widget for showing a website in a list of websites,
 * such as in bookmarks or history.
 */
class WidgetSiteItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val labelView: TextView by lazy { findViewById<TextView>(R.id.label) }
    private val captionView: TextView by lazy { findViewById<TextView>(R.id.caption) }
    private val iconWrapper: FrameLayout by lazy { findViewById<FrameLayout>(R.id.favicon_wrapper) }
    private val secondaryButton: ImageButton by lazy { findViewById<ImageButton>(R.id.secondary_button) }

    /**
     * ImageView that should display favicons.
     */
    val iconView: ImageView by lazy { findViewById<ImageView>(R.id.favicon) }

    init {
        LayoutInflater.from(context).inflate(R.layout.mozac_widget_site_item, this, true)
    }

    /**
     * Sets the text displayed inside of the site item view.
     * @param label Main label text, such as a site title.
     * @param caption Sub caption text, such as a URL. If null, the caption is hidden.
     */
    fun setText(label: CharSequence, caption: CharSequence?) {
        labelView.text = label
        captionView.text = caption
        captionView.isVisible = caption != null
    }

    /**
     * Add a view that will overlay the favicon, such as a checkmark.
     */
    fun addIconOverlay(overlay: View) {
        iconWrapper.addView(overlay)
    }

    /**
     * Add a secondary button, such as an overflow menu.
     * @param icon Drawable to display in the button.
     */
    fun setSecondaryButton(
        icon: Drawable,
        contentDescription: CharSequence,
        onClickListener: (View) -> Unit
    ) {
        secondaryButton.isVisible = true
        secondaryButton.setImageDrawable(icon)
        secondaryButton.contentDescription = contentDescription
        secondaryButton.setOnClickListener(onClickListener)
    }

    /**
     * Removes the secondary button if it was previously set in [setSecondaryButton].
     */
    fun removeSecondaryButton() {
        secondaryButton.isVisible = false
    }
}
