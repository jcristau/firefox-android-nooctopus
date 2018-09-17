/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.InputType
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.URLUtil
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_urlinput.*
import kotlinx.android.synthetic.main.fragment_urlinput.view.*
import mozilla.components.browser.domains.DomainAutoCompleteProvider
import mozilla.components.support.utils.ThreadUtils
import mozilla.components.ui.autocomplete.InlineAutocompleteEditText
import mozilla.components.ui.autocomplete.InlineAutocompleteEditText.AutocompleteResult
import org.mozilla.focus.R
import org.mozilla.focus.R.string.pref_key_homescreen_tips
import org.mozilla.focus.R.string.teaser
import org.mozilla.focus.locale.LocaleAwareAppCompatActivity
import org.mozilla.focus.locale.LocaleAwareFragment
import org.mozilla.focus.menu.home.HomeMenu
import org.mozilla.focus.searchsuggestions.SearchSuggestionsViewModel
import org.mozilla.focus.searchsuggestions.ui.SearchSuggestionsFragment
import org.mozilla.focus.session.Session
import org.mozilla.focus.session.SessionManager
import org.mozilla.focus.session.Source
import org.mozilla.focus.telemetry.TelemetryWrapper
import org.mozilla.focus.tips.Tip
import org.mozilla.focus.tips.TipManager
import org.mozilla.focus.utils.AppConstants
import org.mozilla.focus.utils.Features
import org.mozilla.focus.utils.Settings
import org.mozilla.focus.utils.StatusBarUtils
import org.mozilla.focus.utils.SupportUtils
import org.mozilla.focus.utils.UrlUtils
import org.mozilla.focus.utils.ViewUtils
import org.mozilla.focus.viewmodel.MainViewModel
import org.mozilla.focus.whatsnew.WhatsNew

class FocusCrashException : Exception()

/**
 * Fragment for displaying the URL input controls.
 */
// Refactoring the size and function count of this fragment is non-trivial at this point.
// Therefore we ignore those violations for now.
@Suppress("LargeClass", "TooManyFunctions")
class UrlInputFragment :
    LocaleAwareFragment(),
    View.OnClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        @JvmField
        val FRAGMENT_TAG = "url_input"

        private const val duckDuckGo = "DuckDuckGo"

        private val ARGUMENT_ANIMATION = "animation"
        private val ARGUMENT_X = "x"
        private val ARGUMENT_Y = "y"
        private val ARGUMENT_WIDTH = "width"
        private val ARGUMENT_HEIGHT = "height"

        private val ARGUMENT_SESSION_UUID = "sesssion_uuid"

        private val ANIMATION_BROWSER_SCREEN = "browser_screen"

        private val PLACEHOLDER = "5981086f-9d45-4f64-be99-7d2ffa03befb"

        private val ANIMATION_DURATION = 200
        private val TIPS_ALPHA = 0.65f

        private lateinit var searchSuggestionsViewModel: SearchSuggestionsViewModel

        @JvmStatic
        fun createWithoutSession(): UrlInputFragment {
            val arguments = Bundle()

            val fragment = UrlInputFragment()
            fragment.arguments = arguments

            return fragment
        }

        @JvmStatic
        fun createWithSession(session: Session, urlView: View): UrlInputFragment {
            val arguments = Bundle()

            arguments.putString(ARGUMENT_SESSION_UUID, session.uuid)
            arguments.putString(ARGUMENT_ANIMATION, ANIMATION_BROWSER_SCREEN)

            val screenLocation = IntArray(2)
            urlView.getLocationOnScreen(screenLocation)

            arguments.putInt(ARGUMENT_X, screenLocation[0])
            arguments.putInt(ARGUMENT_Y, screenLocation[1])
            arguments.putInt(ARGUMENT_WIDTH, urlView.width)
            arguments.putInt(ARGUMENT_HEIGHT, urlView.height)

            val fragment = UrlInputFragment()
            fragment.arguments = arguments

            return fragment
        }

        /**
         * Create a new UrlInputFragment with a gradient background (and the Focus logo). This configuration
         * is usually shown if there's no content to be shown below (e.g. the current website).
         */
        @JvmStatic
        fun createWithBackground(): UrlInputFragment {
            val arguments = Bundle()

            val fragment = UrlInputFragment()
            fragment.arguments = arguments

            return fragment
        }
    }

    private val autoCompleteProvider: DomainAutoCompleteProvider = DomainAutoCompleteProvider()
    private var displayedPopupMenu: HomeMenu? = null

    @Volatile
    private var isAnimating: Boolean = false

    private var session: Session? = null
    private var model: MainViewModel? = null

    private val isOverlay: Boolean
        get() = session != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProviders.of(activity!!).get(MainViewModel::class.java)
        searchSuggestionsViewModel = ViewModelProviders.of(this).get(SearchSuggestionsViewModel::class.java)

        PreferenceManager.getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(this)

        // Get session from session manager if there's a session UUID in the fragment's arguments
        arguments?.getString(ARGUMENT_SESSION_UUID)?.let {
            session = if (SessionManager.getInstance().hasSessionWithUUID(it)) SessionManager
                .getInstance().getSessionByUUID(it) else null
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        childFragmentManager.beginTransaction()
                .replace(searchViewContainer.id, SearchSuggestionsFragment.create())
                .commit()

        searchSuggestionsViewModel.selectedSearchSuggestion.observe(this, Observer {
            val isSuggestion = searchSuggestionsViewModel.searchQuery.value != it
            it?.let {
                if (searchSuggestionsViewModel.alwaysSearch) {
                    onSearch(it, false, true)
                } else {
                    onSearch(it, isSuggestion)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()

        activity?.let {
            val settings = Settings.getInstance(it.applicationContext)
            val useCustomDomains = settings.shouldAutocompleteFromCustomDomainList()
            val useShippedDomains = settings.shouldAutocompleteFromShippedDomainList()
            autoCompleteProvider.initialize(it.applicationContext, useShippedDomains,
                useCustomDomains)
        }

        StatusBarUtils.getStatusBarHeight(keyboardLinearLayout) {
            adjustViewToStatusBarHeight(it)
        }
    }

    private fun updateTipsLabel() {
        val context = context ?: return
        val tip = TipManager.getNextTipIfAvailable(context)
        updateSubtitle(tip)
    }

    private fun updateSubtitle(tip: Tip?) {
        if (tip == null) {
            showFocusSubtitle()
            return
        }

        val tipText = String.format(tip.text, System.getProperty("line.separator"))
        keyboardLinearLayout.homeViewTipsLabel.alpha = TIPS_ALPHA

        if (tip.deepLink == null) {
            homeViewTipsLabel.text = tipText
            return
        }

        // Only make the second line clickable if applicable
        val linkStartIndex =
                if (tipText.contains("\n")) tipText.indexOf("\n") + 2
                else 0

        keyboardLinearLayout.homeViewTipsLabel.movementMethod = LinkMovementMethod()
        homeViewTipsLabel.setText(tipText, TextView.BufferType.SPANNABLE)

        val deepLinkAction = object : ClickableSpan() {
            override fun onClick(widget: View?) {
                tip.deepLink.invoke()
            }
        }

        val textWithDeepLink = SpannableString(tipText).apply {
            setSpan(deepLinkAction, linkStartIndex, tipText.length, 0)

            val colorSpan = ForegroundColorSpan(homeViewTipsLabel.currentTextColor)
            setSpan(colorSpan, linkStartIndex, tipText.length, 0)
        }

        homeViewTipsLabel.text = textWithDeepLink
    }

    private fun showFocusSubtitle() {
        keyboardLinearLayout.homeViewTipsLabel.text = getString(teaser)
        keyboardLinearLayout.homeViewTipsLabel.alpha = 1f
        keyboardLinearLayout.homeViewTipsLabel.setOnClickListener(null)
    }

    private fun adjustViewToStatusBarHeight(statusBarHeight: Int) {
        val inputHeight = resources.getDimension(R.dimen.urlinput_height)
        if (keyboardLinearLayout.layoutParams is ViewGroup.MarginLayoutParams) {
            val marginParams = keyboardLinearLayout.layoutParams as ViewGroup.MarginLayoutParams
            marginParams.topMargin = (inputHeight + statusBarHeight).toInt()
        }

        urlInputLayout.layoutParams.height = (inputHeight + statusBarHeight).toInt()

        if (searchViewContainer.layoutParams is ViewGroup.MarginLayoutParams) {
            val marginParams = searchViewContainer.layoutParams as ViewGroup.MarginLayoutParams
            marginParams.topMargin = (inputHeight + statusBarHeight).toInt()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?):
        View? = inflater.inflate(R.layout.fragment_urlinput, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listOf(dismissView, clearView).forEach { it.setOnClickListener(this) }

        urlView?.setOnFilterListener(::onFilter)
        urlView?.imeOptions = urlView.imeOptions or ViewUtils.IME_FLAG_NO_PERSONALIZED_LEARNING
        urlView?.inputType = urlView.inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        if (urlInputContainerView != null) {
            urlInputContainerView.viewTreeObserver.addOnPreDrawListener(object :
                ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (urlInputContainerView != null) {
                        urlInputContainerView.viewTreeObserver.removeOnPreDrawListener(this)
                    }

                    animateFirstDraw()

                    return true
                }
            })
        }

        if (isOverlay) {
            keyboardLinearLayout?.visibility = View.GONE
        } else {
            backgroundView?.setBackgroundResource(R.drawable.background_gradient)

            dismissView?.visibility = View.GONE

            menuView?.visibility = View.VISIBLE
            menuView?.setOnClickListener(this)
        }

        urlView?.setOnCommitListener(::onCommit)

        val geckoViewAndDDG: Boolean =
            Settings.getInstance(requireContext()).defaultSearchEngineName == duckDuckGo &&
                    AppConstants.isGeckoBuild

        session?.let {
            urlView?.setText(
                if (it.isSearch &&
                    !geckoViewAndDDG &&
                    Features.SEARCH_TERMS_OR_URL
                )
                    it.searchTerms else
                    it.url.value
            )
            clearView?.visibility = View.VISIBLE
            searchViewContainer?.visibility = View.GONE
        }

        updateTipsLabel()
    }

    override fun applyLocale() {
        if (isOverlay) {
            activity?.supportFragmentManager
                    ?.beginTransaction()
                    ?.replace(
                            R.id.container,
                            UrlInputFragment.createWithSession(session!!, urlView),
                            UrlInputFragment.FRAGMENT_TAG)
                    ?.commit()
        } else {
            activity?.supportFragmentManager
                    ?.beginTransaction()
                    ?.replace(
                            R.id.container,
                            UrlInputFragment.createWithBackground(),
                            UrlInputFragment.FRAGMENT_TAG)
                    ?.commit()
        }
    }

    fun onBackPressed(): Boolean {
        if (isOverlay) {
            animateAndDismiss()
            return true
        }

        return false
    }

    override fun onStart() {
        super.onStart()

        showKeyboard()
    }

    override fun onStop() {
        super.onStop()

        // Reset the keyboard layout to avoid a jarring animation when the view is started again. (#1135)
        keyboardLinearLayout?.reset()
    }

    fun showKeyboard() {
        ViewUtils.showKeyboard(urlView)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

        if (newConfig?.orientation != Configuration.ORIENTATION_UNDEFINED) {
            //This is a hack to make the HomeMenu actually stick on top of the menuView (#3287)

            displayedPopupMenu?.let {
                it.dismiss()

                menuView.viewTreeObserver.addOnPreDrawListener (object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        menuView.viewTreeObserver.removeOnPreDrawListener(this)
                        showHomeMenu(menuView)

                        return false
                    }
                })
            }
        }
    }

    // This method triggers the complexity warning. However it's actually not that hard to understand.
    @Suppress("ComplexMethod")
    override fun onClick(view: View) {
        when (view.id) {
            R.id.clearView -> clear()

            R.id.dismissView -> if (isOverlay) {
                animateAndDismiss()
            } else {
                clear()
            }

            R.id.menuView -> showHomeMenu(view)

            R.id.whats_new -> context?.let {
                TelemetryWrapper.openWhatsNewEvent(WhatsNew.shouldHighlightWhatsNew(it))

                WhatsNew.userViewedWhatsNew(it)

                SessionManager.getInstance().createSession(Source.MENU,
                    SupportUtils.getSumoURLForTopic(it, SupportUtils.SumoTopic.WHATS_NEW))
            }

            R.id.settings -> (activity as LocaleAwareAppCompatActivity).openPreferences()

            R.id.help -> {
                SessionManager.getInstance().createSession(Source.MENU, SupportUtils.HELP_URL)
            }

            else -> throw IllegalStateException("Unhandled view in onClick()")
        }
    }

    private fun clear() {
        urlView?.setText("")
        urlView?.requestFocus()
    }

    private fun showHomeMenu(anchor: View) = context?.let {
        displayedPopupMenu = HomeMenu(it, this)

        displayedPopupMenu?.show(anchor)

        displayedPopupMenu?.dismissListener = {
            displayedPopupMenu = null
        }
    }

    override fun onDetach() {
        super.onDetach()

        // On detach, the PopupMenu is no longer relevant to other content (e.g. BrowserFragment) so dismiss it.
        // Note: if we don't dismiss the PopupMenu, its onMenuItemClick method references the old Fragment, which now
        // has a null Context and will cause crashes.
        displayedPopupMenu?.dismiss()
    }

    private fun animateFirstDraw() {
        if (ANIMATION_BROWSER_SCREEN == arguments?.getString(ARGUMENT_ANIMATION)) {
            playVisibilityAnimation(false)
        }
    }

    private fun animateAndDismiss() {
        ThreadUtils.assertOnUiThread()

        if (isAnimating) {
            // We are already animating some state change. Ignore all other requests.
            return
        }

        // Don't allow any more clicks: dismissView is still visible until the animation ends,
        // but we don't want to restart animations and/or trigger hiding again (which could potentially
        // cause crashes since we don't know what state we're in). Ignoring further clicks is the simplest
        // solution, since dismissView is about to disappear anyway.
        dismissView?.isClickable = false

        if (ANIMATION_BROWSER_SCREEN == arguments?.getString(ARGUMENT_ANIMATION)) {
            playVisibilityAnimation(true)
        } else {
            dismiss()
        }
    }

    /**
     * This animation is quite complex. The 'reverse' flag controls whether we want to show the UI
     * (false) or whether we are going to hide it (true). Additionally the animation is slightly
     * different depending on whether this fragment is shown as an overlay on top of other fragments
     * or if it draws its own background.
     */
    // This method correctly triggers a complexity warning. This method is indeed very and too complex.
    // However refactoring it is not trivial at this point so we ignore the warning for now.
    @Suppress("ComplexMethod")
    private fun playVisibilityAnimation(reverse: Boolean) {
        if (isAnimating) {
            // We are already animating, let's ignore another request.
            return
        }

        isAnimating = true

        val xyOffset = (if (isOverlay)
            (urlInputContainerView?.layoutParams as FrameLayout.LayoutParams).bottomMargin
        else
            0).toFloat()

        if (urlInputBackgroundView != null) {
            val width = urlInputBackgroundView.width.toFloat()
            val height = urlInputBackgroundView.height.toFloat()

            val widthScale = if (isOverlay)
                (width + 2 * xyOffset) / width
            else
                1f

            val heightScale = if (isOverlay)
                (height + 2 * xyOffset) / height
            else
                1f

            if (!reverse) {
                urlInputBackgroundView?.pivotX = 0f
                urlInputBackgroundView?.pivotY = 0f
                urlInputBackgroundView?.scaleX = widthScale
                urlInputBackgroundView?.scaleY = heightScale
                urlInputBackgroundView?.translationX = -xyOffset
                urlInputBackgroundView?.translationY = -xyOffset

                clearView?.alpha = 0f
            }

            // Let the URL input use the full width/height and then shrink to the actual size
            urlInputBackgroundView.animate()
                .setDuration(ANIMATION_DURATION.toLong())
                .scaleX(if (reverse) widthScale else 1f)
                .scaleY(if (reverse) heightScale else 1f)
                .alpha((if (reverse && isOverlay) 0 else 1).toFloat())
                .translationX(if (reverse) -xyOffset else 0f)
                .translationY(if (reverse) -xyOffset else 0f)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        if (reverse) {
                            clearView?.alpha = 0f
                        }
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (reverse) {
                            if (isOverlay) {
                                dismiss()
                            }
                        } else {
                            clearView?.alpha = 1f
                        }

                        isAnimating = false
                    }
                })
        }

        // We only need to animate the toolbar if we are an overlay.
        if (isOverlay) {
            val screenLocation = IntArray(2)
            urlView?.getLocationOnScreen(screenLocation)

            val leftDelta = arguments!!.getInt(ARGUMENT_X) - screenLocation[0] - urlView.paddingLeft

            if (!reverse) {
                urlView?.pivotX = 0f
                urlView?.pivotY = 0f
                urlView?.translationX = leftDelta.toFloat()
            }

            if (urlView != null) {
                // The URL moves from the right (at least if the lock is visible) to it's actual position
                urlView.animate()
                    .setDuration(ANIMATION_DURATION.toLong())
                    .translationX((if (reverse) leftDelta else 0).toFloat())
            }
        }

        if (!reverse) {
            clearView?.alpha = 0f
        }

        if (toolbarBackgroundView != null) {
            val transitionDrawable = toolbarBackgroundView?.background as TransitionDrawable

            if (reverse) {
                transitionDrawable.reverseTransition(ANIMATION_DURATION)
                toolbarBottomBorder?.visibility = View.VISIBLE

                if (!isOverlay) {
                    dismissView?.visibility = View.GONE
                    menuView?.visibility = View.VISIBLE
                }
            } else {
                transitionDrawable.startTransition(ANIMATION_DURATION)
                toolbarBottomBorder?.visibility = View.GONE
            }
        }
    }

    private fun dismiss() {
        // This method is called from animation callbacks. In the short time frame between the animation
        // starting and ending the activity can be paused. In this case this code can throw an
        // IllegalStateException because we already saved the state (of the activity / fragment) before
        // this transaction is committed. To avoid this we commit while allowing a state loss here.
        // We do not save any state in this fragment (It's getting destroyed) so this should not be a problem.
        activity?.supportFragmentManager
            ?.beginTransaction()
            ?.remove(this)
            ?.commitAllowingStateLoss()
    }

    private fun onCommit() {
        val input = urlView.autocompleteResult.formattedText.let {
            if (it.isEmpty() || !URLUtil.isValidUrl(urlView?.text.toString())) {
                urlView?.text.toString()
            } else it
        }

        if (!input.trim { it <= ' ' }.isEmpty()) {
            handleCrashTrigger(input)

            ViewUtils.hideKeyboard(urlView)

            if (handleExperimentsTrigger(input)) return
            if (handleL10NTrigger(input)) return

            val (isUrl, url, searchTerms) = normalizeUrlAndSearchTerms(input)

            openUrl(url, searchTerms)

            TelemetryWrapper.urlBarEvent(isUrl, urlView.autocompleteResult)
        }
    }

    private fun handleL10NTrigger(input: String): Boolean {
        if (!AppConstants.isDevBuild) return false

        var triggerHandled = true

        when (input) {
            "l10n:tip:1" -> updateSubtitle(Tip.createTrackingProtectionTip(context!!))
            "l10n:tip:2" -> updateSubtitle(Tip.createHomescreenTip(context!!))
            "l10n:tip:3" -> updateSubtitle(Tip.createDefaultBrowserTip(context!!))
            "l10n:tip:4" -> updateSubtitle(Tip.createAutocompleteURLTip(context!!))
            "l10n:tip:5" -> updateSubtitle(Tip.createOpenInNewTabTip(context!!))
            "l10n:tip:6" -> updateSubtitle(Tip.createRequestDesktopTip(context!!))
            else -> triggerHandled = false
        }

        if (triggerHandled) clear()
        return triggerHandled
    }

    private fun handleExperimentsTrigger(input: String): Boolean {
        if (input == "focus:test") {
            model?.showExperiments()
            return true
        }
        return false
    }

    private fun handleCrashTrigger(input: String) {
        if (input == "focus:crash") {
            throw FocusCrashException()
        }
    }

    private fun normalizeUrlAndSearchTerms(input: String): Triple<Boolean, String, String?> {
        val isUrl = UrlUtils.isUrl(input)

        val url = if (isUrl)
            UrlUtils.normalize(input)
        else
            UrlUtils.createSearchUrl(context, input)

        val searchTerms = if (isUrl)
            null
        else
            input.trim { it <= ' ' }
        return Triple(isUrl, url, searchTerms)
    }

    private fun onSearch(query: String, isSuggestion: Boolean = false, alwaysSearch: Boolean = false) {
        if (alwaysSearch) {
            openUrl(UrlUtils.createSearchUrl(context, query), query)
        } else {
            val searchTerms = if (UrlUtils.isUrl(query)) null else query
            val searchUrl =
                if (searchTerms != null) UrlUtils.createSearchUrl(context, searchTerms) else UrlUtils.normalize(query)

            openUrl(searchUrl, searchTerms)
        }

        TelemetryWrapper.searchSelectEvent(isSuggestion)
    }

    private fun openUrl(url: String, searchTerms: String?) {
        session?.searchTerms = searchTerms

        val fragmentManager = activity!!.supportFragmentManager

        // Replace all fragments with a fresh browser fragment. This means we either remove the
        // HomeFragment with an UrlInputFragment on top or an old BrowserFragment with an
        // UrlInputFragment.
        val browserFragment = fragmentManager.findFragmentByTag(BrowserFragment.FRAGMENT_TAG)

        if (browserFragment != null && browserFragment is BrowserFragment && browserFragment.isVisible) {
            // Reuse existing visible fragment - in this case we know the user is already browsing.
            // The fragment might exist if we "erased" a browsing session, hence we need to check
            // for visibility in addition to existence.
            browserFragment.loadUrl(url)

            // And this fragment can be removed again.
            fragmentManager.beginTransaction()
                .remove(this)
                .commit()
        } else {
            if (!TextUtils.isEmpty(searchTerms)) {
                SessionManager.getInstance()
                    .createSearchSession(Source.USER_ENTERED, url, searchTerms)
            } else {
                SessionManager.getInstance().createSession(Source.USER_ENTERED, url)
            }
        }
    }

    private fun onFilter(searchText: String, view: InlineAutocompleteEditText?) {
        // If the UrlInputFragment has already been hidden, don't bother with filtering. Because of the text
        // input architecture on Android it's possible for onFilter() to be called after we've already
        // hidden the Fragment, see the relevant bug for more background:
        // https://github.com/mozilla-mobile/focus-android/issues/441#issuecomment-293691141
        if (!isVisible) {
            return
        }

        view?.let {
            val result = autoCompleteProvider.autocomplete(searchText)
            view.applyAutocompleteResult(AutocompleteResult(result.text, result.source, result.size, { result.url }))
        }

        searchSuggestionsViewModel.setSearchQuery(searchText)

        if (searchText.trim { it <= ' ' }.isEmpty()) {
            clearView?.visibility = View.GONE
            searchViewContainer?.visibility = View.GONE

            if (!isOverlay) {
                playVisibilityAnimation(true)
            }
        } else {
            clearView?.visibility = View.VISIBLE
            menuView?.visibility = View.GONE

            if (!isOverlay && dismissView?.visibility != View.VISIBLE) {
                playVisibilityAnimation(false)
                dismissView?.visibility = View.VISIBLE
            }

            // LTR languages sometimes have grammar where the search terms are displayed to the left
            // of the hint string. To take care of LTR, RTL, and special LTR cases, we use a
            // placeholder to know the start and end indices of where we should bold the search text
            val hint = getString(R.string.search_hint, PLACEHOLDER)
            val start = hint.indexOf(PLACEHOLDER)

            val content = SpannableString(hint.replace(PLACEHOLDER, searchText))
            content.setSpan(StyleSpan(Typeface.BOLD), start, start + searchText.length, 0)
            searchViewContainer?.visibility = View.VISIBLE
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == activity?.getString(pref_key_homescreen_tips)) { updateTipsLabel() }
    }
}
