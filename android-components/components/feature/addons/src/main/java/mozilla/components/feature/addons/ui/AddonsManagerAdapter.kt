/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.addons.ui

import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.TransitionDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.R
import mozilla.components.feature.addons.amo.AddonCollectionProvider
import mozilla.components.feature.addons.ui.CustomViewHolder.AddonViewHolder
import mozilla.components.feature.addons.ui.CustomViewHolder.SectionViewHolder
import mozilla.components.feature.addons.ui.CustomViewHolder.UnsupportedSectionViewHolder
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.content.res.resolveAttribute
import java.io.IOException

private const val VIEW_HOLDER_TYPE_SECTION = 0
private const val VIEW_HOLDER_TYPE_NOT_YET_SUPPORTED_SECTION = 1
private const val VIEW_HOLDER_TYPE_ADDON = 2

/**
 * An adapter for displaying add-on items. This will display information related to the state of
 * an add-on such as recommended, unsupported or installed. In addition, it will perform actions
 * such as installing an add-on.
 *
 * @property addonCollectionProvider Provider of AMO collection API.
 * @property addonsManagerDelegate Delegate that will provides method for handling the add-on items.
 * @param addons The list of add-on based on the AMO store.
 * @property style Indicates how items should look like.
 */
@Suppress("TooManyFunctions", "LargeClass")
class AddonsManagerAdapter(
    private val addonCollectionProvider: AddonCollectionProvider,
    private val addonsManagerDelegate: AddonsManagerAdapterDelegate,
    addons: List<Addon>,
    private val style: Style? = null
) : RecyclerView.Adapter<CustomViewHolder>() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val items: List<Any>
    private val unsupportedAddons = ArrayList<Addon>()
    private val logger = Logger("AddonsManagerAdapter")

    init {
        items = createListWithSections(addons)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        return when (viewType) {
            VIEW_HOLDER_TYPE_ADDON -> createAddonViewHolder(parent)
            VIEW_HOLDER_TYPE_SECTION -> createSectionViewHolder(parent)
            VIEW_HOLDER_TYPE_NOT_YET_SUPPORTED_SECTION -> createUnsupportedSectionViewHolder(parent)
            else -> throw IllegalArgumentException("Unrecognized viewType")
        }
    }

    private fun createSectionViewHolder(parent: ViewGroup): CustomViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.mozac_feature_addons_section_item, parent, false)
        val titleView = view.findViewById<TextView>(R.id.title)
        return SectionViewHolder(view, titleView)
    }

    private fun createUnsupportedSectionViewHolder(parent: ViewGroup): CustomViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(
            R.layout.mozac_feature_addons_section_unsupported_section_item,
            parent,
            false
        )
        val titleView = view.findViewById<TextView>(R.id.title)
        val descriptionView = view.findViewById<TextView>(R.id.description)

        return UnsupportedSectionViewHolder(view, titleView, descriptionView)
    }

    private fun createAddonViewHolder(parent: ViewGroup): AddonViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.mozac_feature_addons_item, parent, false)
        val iconContainer = view.findViewById<CardView>(R.id.icon_container)
        val iconView = view.findViewById<ImageView>(R.id.add_on_icon)
        val titleView = view.findViewById<TextView>(R.id.add_on_name)
        val summaryView = view.findViewById<TextView>(R.id.add_on_description)
        val ratingView = view.findViewById<RatingBar>(R.id.rating)
        val ratingAccessibleView = view.findViewById<TextView>(R.id.rating_accessibility)
        val userCountView = view.findViewById<TextView>(R.id.users_count)
        val addButton = view.findViewById<ImageView>(R.id.add_button)
        return AddonViewHolder(
            view,
            iconContainer,
            iconView,
            titleView,
            summaryView,
            ratingView,
            ratingAccessibleView,
            userCountView,
            addButton
        )
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Addon -> VIEW_HOLDER_TYPE_ADDON
            is Section -> VIEW_HOLDER_TYPE_SECTION
            is NotYetSupportedSection -> VIEW_HOLDER_TYPE_NOT_YET_SUPPORTED_SECTION
            else -> throw IllegalArgumentException("items[position] has unrecognized type")
        }
    }

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val item = items[position]

        when (holder) {
            is SectionViewHolder -> bindSection(holder, item as Section)
            is AddonViewHolder -> bindAddon(holder, item as Addon)
            is UnsupportedSectionViewHolder -> bindNotYetSupportedSection(
                holder,
                item as NotYetSupportedSection
            )
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun bindSection(holder: SectionViewHolder, section: Section) {
        holder.titleView.setText(section.title)
        style?.maybeSetSectionsTextColor(holder.titleView)
        style?.maybeSetSectionsTypeFace(holder.titleView)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun bindNotYetSupportedSection(
        holder: UnsupportedSectionViewHolder,
        section: NotYetSupportedSection
    ) {
        val context = holder.itemView.context
        holder.titleView.setText(section.title)
        holder.descriptionView.text =
            if (unsupportedAddons.size == 1) {
                context.getString(R.string.mozac_feature_addons_unsupported_caption)
            } else {
                context.getString(
                    R.string.mozac_feature_addons_unsupported_caption_plural,
                    unsupportedAddons.size.toString()
                )
            }

        holder.itemView.setOnClickListener {
            addonsManagerDelegate.onNotYetSupportedSectionClicked(unsupportedAddons)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun bindAddon(holder: AddonViewHolder, addon: Addon) {
        val context = holder.itemView.context
        addon.rating?.let {
            val userCount = context.getString(R.string.mozac_feature_addons_user_rating_count)
            val ratingContentDescription =
                String.format(
                    context.getString(R.string.mozac_feature_addons_rating_content_description),
                    it.average
                )
            holder.ratingView.contentDescription = ratingContentDescription
            // Android RatingBar is not very accessibility-friendly, we will use non visible TextView
            // for contentDescription for the TalkBack feature
            holder.ratingAccessibleView.text = ratingContentDescription
            holder.ratingView.rating = it.average
            holder.userCountView.text = String.format(userCount, getFormattedAmount(it.reviews))
        }

        holder.titleView.text =
            if (addon.translatableName.isNotEmpty()) {
                addon.translatedName
            } else {
                addon.id
            }

        if (addon.translatableSummary.isNotEmpty()) {
            holder.summaryView.text = addon.translatedSummary
        } else {
            holder.summaryView.visibility = View.GONE
        }

        holder.itemView.tag = addon
        holder.itemView.setOnClickListener {
            addonsManagerDelegate.onAddonItemClicked(addon)
        }

        holder.addButton.isVisible = !addon.isInstalled()
        holder.addButton.setOnClickListener {
            if (!addon.isInstalled()) {
                addonsManagerDelegate.onInstallAddonButtonClicked(addon)
            }
        }

        style?.addonBackgroundIconColor?.let {
            val backgroundColor = ContextCompat.getColor(holder.iconContainer.context, it)
            holder.iconContainer.setCardBackgroundColor(backgroundColor)
        }
        fetchIcon(addon, holder.iconView)
        style?.maybeSetAddonNameTextColor(holder.titleView)
        style?.maybeSetAddonSummaryTextColor(holder.summaryView)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun fetchIcon(addon: Addon, iconView: ImageView, scope: CoroutineScope = this.scope): Job {
        return scope.launch {
            try {
                val iconBitmap = addonCollectionProvider.getAddonIconBitmap(addon)
                iconBitmap?.let {
                    scope.launch(Main) {
                        iconView.setWithCrossFadeAnimation(it)
                    }
                }
            } catch (e: IOException) {
                scope.launch(Main) {
                    val context = iconView.context
                    val att = context.theme.resolveAttribute(android.R.attr.textColorPrimary)
                    iconView.setColorFilter(ContextCompat.getColor(context, att))
                    iconView.setImageDrawable(context.getDrawable(R.drawable.mozac_ic_extensions))
                }
                logger.error("Attempt to fetch the ${addon.id} icon failed", e)
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Suppress("ComplexMethod")
    internal fun createListWithSections(addons: List<Addon>): List<Any> {
        val itemsWithSections = ArrayList<Any>()
        val installedAddons = ArrayList<Addon>()
        val recommendedAddons = ArrayList<Addon>()
        val disabledAddons = ArrayList<Addon>()

        addons.forEach { addon ->
            when {
                addon.inUnsupportedSection() -> unsupportedAddons.add(addon)
                addon.inRecommendedSection() -> recommendedAddons.add(addon)
                addon.inInstalledSection() -> installedAddons.add(addon)
                addon.inDisabledSection() -> disabledAddons.add(addon)
            }
        }

        // Add installed section and addons if available
        if (installedAddons.isNotEmpty()) {
            itemsWithSections.add(Section(R.string.mozac_feature_addons_enabled))
            itemsWithSections.addAll(installedAddons)
        }

        // Add disabled section and addons if available
        if (disabledAddons.isNotEmpty()) {
            itemsWithSections.add(Section(R.string.mozac_feature_addons_disabled_section))
            itemsWithSections.addAll(disabledAddons)
        }

        // Add recommended section and addons if available
        if (recommendedAddons.isNotEmpty()) {
            itemsWithSections.add(Section(R.string.mozac_feature_addons_recommended_section))
            itemsWithSections.addAll(recommendedAddons)
        }

        // Add unsupported section
        if (unsupportedAddons.isNotEmpty()) {
            itemsWithSections.add(NotYetSupportedSection(R.string.mozac_feature_addons_unavailable_section))
        }

        return itemsWithSections
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal data class Section(@StringRes val title: Int)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal data class NotYetSupportedSection(@StringRes val title: Int)

    /**
     * Allows to customize how items should look like.
     */
    data class Style(
        @ColorRes
        val sectionsTextColor: Int? = null,
        @ColorRes
        val addonNameTextColor: Int? = null,
        @ColorRes
        val addonSummaryTextColor: Int? = null,
        val sectionsTypeFace: Typeface? = null,
        @ColorRes
        val addonBackgroundIconColor: Int? = null
    ) {
        internal fun maybeSetSectionsTextColor(textView: TextView) {
            sectionsTextColor?.let {
                val color = ContextCompat.getColor(textView.context, it)
                textView.setTextColor(color)
            }
        }

        internal fun maybeSetSectionsTypeFace(textView: TextView) {
            sectionsTypeFace?.let {
                textView.typeface = it
            }
        }

        internal fun maybeSetAddonNameTextColor(textView: TextView) {
            addonNameTextColor?.let {
                val color = ContextCompat.getColor(textView.context, it)
                textView.setTextColor(color)
            }
        }

        internal fun maybeSetAddonSummaryTextColor(textView: TextView) {
            addonSummaryTextColor?.let {
                val color = ContextCompat.getColor(textView.context, it)
                textView.setTextColor(color)
            }
        }
    }
}

private fun Addon.inUnsupportedSection() = isInstalled() && !isSupported()
private fun Addon.inRecommendedSection() = !isInstalled()
private fun Addon.inInstalledSection() = isInstalled() && isSupported() && isEnabled()
private fun Addon.inDisabledSection() = isInstalled() && isSupported() && !isEnabled()
private fun ImageView.setWithCrossFadeAnimation(bitmap: Bitmap, durationMillis: Int = 1700) {
    val bitmapDrawable = BitmapDrawable(context.resources, bitmap)
    val animation = TransitionDrawable(arrayOf(drawable, bitmapDrawable))
    animation.isCrossFadeEnabled = true
    setImageDrawable(animation)
    animation.startTransition(durationMillis)
}
