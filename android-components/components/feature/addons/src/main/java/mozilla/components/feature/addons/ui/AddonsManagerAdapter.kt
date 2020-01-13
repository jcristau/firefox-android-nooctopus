/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.addons.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.R
import mozilla.components.feature.addons.amo.AddonCollectionProvider
import mozilla.components.feature.addons.ui.CustomViewHolder.AddonViewHolder
import mozilla.components.feature.addons.ui.CustomViewHolder.SectionViewHolder
import mozilla.components.feature.addons.ui.CustomViewHolder.UnsupportedSectionViewHolder

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
 * @property addons The list of add-on based on the AMO store.
 */
@Suppress("TooManyFunctions", "LargeClass")
class AddonsManagerAdapter(
    private val addonCollectionProvider: AddonCollectionProvider,
    private val addonsManagerDelegate: AddonsManagerAdapterDelegate,
    addons: List<Addon>
) : RecyclerView.Adapter<CustomViewHolder>() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val items: List<Any>
    private val unsupportedAddons = ArrayList<Addon>()

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

        return UnsupportedSectionViewHolder(view, titleView)
    }

    private fun createAddonViewHolder(parent: ViewGroup): AddonViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.mozac_feature_addons_item, parent, false)
        val iconView = view.findViewById<ImageView>(R.id.add_on_icon)
        val titleView = view.findViewById<TextView>(R.id.add_on_name)
        val summaryView = view.findViewById<TextView>(R.id.add_on_description)
        val ratingView = view.findViewById<RatingBar>(R.id.rating)
        val userCountView = view.findViewById<TextView>(R.id.users_count)
        val addButton = view.findViewById<ImageView>(R.id.add_button)
        return AddonViewHolder(
            view,
            iconView,
            titleView,
            summaryView,
            ratingView,
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

    private fun bindSection(holder: SectionViewHolder, section: Section) {
        holder.titleView.setText(section.title)
    }

    private fun bindNotYetSupportedSection(
        holder: UnsupportedSectionViewHolder,
        section: NotYetSupportedSection
    ) {
        holder.titleView.setText(section.title)
        holder.itemView.setOnClickListener {
            addonsManagerDelegate.onNotYetSupportedSectionClicked(unsupportedAddons)
        }
    }

    private fun bindAddon(holder: AddonViewHolder, addon: Addon) {
        val context = holder.itemView.context
        addon.rating?.let {
            val userCount = context.getString(R.string.mozac_feature_addons_user_rating_count)
            val ratingContentDescription =
                context.getString(R.string.mozac_feature_addons_rating_content_description)
            holder.ratingView.contentDescription =
                String.format(ratingContentDescription, it.average)
            holder.ratingView.rating = it.average
            holder.userCountView.text = String.format(userCount, getFormattedAmount(it.reviews))
        }

        holder.titleView.text =
            if (addon.translatableName.isNotEmpty()) {
                addon.translatableName.translate()
            } else {
                addon.id
            }

        if (addon.translatableSummary.isNotEmpty()) {
            holder.summaryView.text = addon.translatableSummary.translate()
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

        scope.launch {
            val iconBitmap = addonCollectionProvider.getAddonIconBitmap(addon)

            iconBitmap?.let {
                MainScope().launch {
                    holder.iconView.setImageBitmap(it)
                }
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun createListWithSections(addons: List<Addon>): List<Any> {
        val itemsWithSections = ArrayList<Any>()
        val installedAddons = ArrayList<Addon>()
        val recommendedAddons = ArrayList<Addon>()

        addons.forEach { addon ->
            if (addon.isInstalled()) {
                if (!addon.isSupported()) {
                    unsupportedAddons.add(addon)
                } else {
                    installedAddons.add(addon)
                }
            } else {
                recommendedAddons.add(addon)
            }
        }

        // Add installed section and addons if available
        if (installedAddons.isNotEmpty()) {
            itemsWithSections.add(Section(R.string.mozac_feature_addons_installed_section))
            itemsWithSections.addAll(installedAddons)
        }

        // Add recommended section and addons if available
        if (recommendedAddons.isNotEmpty()) {
            itemsWithSections.add(Section(R.string.mozac_feature_addons_recommended_section))
            itemsWithSections.addAll(recommendedAddons)
        }

        // Add unsupported section
        if (unsupportedAddons.isNotEmpty()) {
            itemsWithSections.add(NotYetSupportedSection(R.string.mozac_feature_addons_unsupported_section))
        }

        return itemsWithSections
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal inner class Section(@StringRes val title: Int)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal inner class NotYetSupportedSection(@StringRes val title: Int)
}
