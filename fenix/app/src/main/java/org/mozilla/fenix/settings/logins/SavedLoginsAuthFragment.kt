/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.logins

import android.annotation.TargetApi
import android.app.Activity.RESULT_OK
import android.app.KeyguardManager
import android.content.Context.KEYGUARD_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION_CODES.M
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.service.fxa.SyncEngine
import mozilla.components.service.fxa.manager.SyncEnginesStorage
import org.mozilla.fenix.R
import org.mozilla.fenix.addons.runIfFragmentIsAttached
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.secure
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.ext.showToolbar
import org.mozilla.fenix.settings.SharedPreferenceUpdater
import org.mozilla.fenix.settings.requirePreference
import java.util.concurrent.Executor

@Suppress("TooManyFunctions", "LargeClass")
class SavedLoginsAuthFragment : PreferenceFragmentCompat(), AccountObserver {

    @TargetApi(M)
    private lateinit var biometricPromptCallback: BiometricPrompt.AuthenticationCallback

    @TargetApi(M)
    private lateinit var executor: Executor

    @TargetApi(M)
    private lateinit var biometricPrompt: BiometricPrompt

    @TargetApi(M)
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.logins_preferences, rootKey)
    }

    /**
     * There is a bug where while the biometric prompt is showing, you were able to quickly navigate
     * so we are disabling the settings that navigate while authenticating.
     * https://github.com/mozilla-mobile/fenix/issues/12312
     */
    private fun togglePrefsEnabledWhileAuthenticating(enabled: Boolean) {
        requirePreference<Preference>(R.string.pref_key_password_sync_logins).isEnabled = enabled
        requirePreference<Preference>(R.string.pref_key_save_logins_settings).isEnabled = enabled
        requirePreference<Preference>(R.string.pref_key_saved_logins).isEnabled = enabled
    }

    private fun navigateToSavedLogins() {
        runIfFragmentIsAttached {
            viewLifecycleOwner.lifecycleScope.launch(Main) {
                // Workaround for likely biometric library bug
                // https://github.com/mozilla-mobile/fenix/issues/8438
                delay(SHORT_DELAY_MS)
                navigateToSavedLoginsFragment()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        biometricPromptCallback = object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.e(LOG_TAG, "onAuthenticationError $errString")
                togglePrefsEnabledWhileAuthenticating(enabled = true)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.d(LOG_TAG, "onAuthenticationSucceeded")
                navigateToSavedLogins()
            }

            override fun onAuthenticationFailed() {
                Log.e(LOG_TAG, "onAuthenticationFailed")
                togglePrefsEnabledWhileAuthenticating(enabled = true)
            }
        }

        executor = ContextCompat.getMainExecutor(requireContext())

        biometricPrompt = BiometricPrompt(this, executor, biometricPromptCallback)

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.logins_biometric_prompt_message))
            .setDeviceCredentialAllowed(true)
            .build()
    }

    @Suppress("ComplexMethod")
    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.preferences_passwords_logins_and_passwords))

        requirePreference<Preference>(R.string.pref_key_save_logins_settings).apply {
            summary = getString(
                if (context.settings().shouldPromptToSaveLogins)
                    R.string.preferences_passwords_save_logins_ask_to_save else
                    R.string.preferences_passwords_save_logins_never_save
            )
            setOnPreferenceClickListener {
                navigateToSaveLoginSettingFragment()
                true
            }
        }

        requirePreference<SwitchPreference>(R.string.pref_key_autofill_logins).apply {
            isChecked = context.settings().shouldAutofillLogins
            onPreferenceChangeListener = object : SharedPreferenceUpdater() {
                override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
                    context?.components?.core?.engine?.settings?.loginAutofillEnabled =
                        newValue as Boolean
                    return super.onPreferenceChange(preference, newValue)
                }
            }
        }

        requirePreference<Preference>(R.string.pref_key_saved_logins).setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= M && isHardwareAvailable && hasBiometricEnrolled) {
                togglePrefsEnabledWhileAuthenticating(enabled = false)
                biometricPrompt.authenticate(promptInfo)
            } else {
                verifyPinOrShowSetupWarning()
            }
            true
        }

        val accountManager = requireComponents.backgroundServices.accountManager
        accountManager.register(this, owner = this)

        val accountExists = accountManager.authenticatedAccount() != null
        val needsReauth = accountManager.accountNeedsReauth()
        when {
            needsReauth -> updateSyncPreferenceNeedsReauth()
            accountExists -> updateSyncPreferenceStatus()
            !accountExists -> updateSyncPreferenceNeedsLogin()
        }
    }

    override fun onAuthenticated(account: OAuthAccount, authType: AuthType) =
        updateSyncPreferenceStatus()

    override fun onLoggedOut() = updateSyncPreferenceNeedsLogin()

    override fun onAuthenticationProblems() = updateSyncPreferenceNeedsReauth()

    private val isHardwareAvailable: Boolean by lazy {
        if (Build.VERSION.SDK_INT >= M) {
            context?.let {
                val bm = BiometricManager.from(it)
                val canAuthenticate = bm.canAuthenticate()
                !(canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
                        canAuthenticate == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE)
            } ?: false
        } else {
            false
        }
    }

    private val hasBiometricEnrolled: Boolean by lazy {
        if (Build.VERSION.SDK_INT >= M) {
            context?.let {
                val bm = BiometricManager.from(it)
                val canAuthenticate = bm.canAuthenticate()
                (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS)
            } ?: false
        } else {
            false
        }
    }

    private fun updateSyncPreferenceStatus() {
        requirePreference<Preference>(R.string.pref_key_password_sync_logins).apply {
            val syncEnginesStatus = SyncEnginesStorage(requireContext()).getStatus()
            val loginsSyncStatus = syncEnginesStatus.getOrElse(SyncEngine.Passwords) { false }
            summary = getString(
                if (loginsSyncStatus) R.string.preferences_passwords_sync_logins_on
                else R.string.preferences_passwords_sync_logins_off
            )
            setOnPreferenceClickListener {
                navigateToAccountSettingsFragment()
                true
            }
        }
    }

    private fun updateSyncPreferenceNeedsLogin() {
        requirePreference<Preference>(R.string.pref_key_password_sync_logins).apply {
            summary = getString(R.string.preferences_passwords_sync_logins_sign_in)
            setOnPreferenceClickListener {
                navigateToTurnOnSyncFragment()
                true
            }
        }
    }

    private fun updateSyncPreferenceNeedsReauth() {
        requirePreference<Preference>(R.string.pref_key_password_sync_logins).apply {
            summary = getString(R.string.preferences_passwords_sync_logins_reconnect)
            setOnPreferenceClickListener {
                navigateToAccountProblemFragment()
                true
            }
        }
    }

    private fun verifyPinOrShowSetupWarning() {
        val manager = activity?.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (manager.isKeyguardSecure) {
            showPinVerification()
        } else {
            if (context?.settings()?.shouldShowSecurityPinWarning == true) {
                showPinDialogWarning()
            } else {
                navigateToSavedLoginsFragment()
            }
        }
    }

    private fun showPinDialogWarning() {
        context?.let {
            AlertDialog.Builder(it).apply {
                setTitle(getString(R.string.logins_warning_dialog_title))
                setMessage(
                    getString(R.string.logins_warning_dialog_message)
                )

                setNegativeButton(getString(R.string.logins_warning_dialog_later)) { _: DialogInterface, _ ->
                    navigateToSavedLoginsFragment()
                }

                setPositiveButton(getString(R.string.logins_warning_dialog_set_up_now)) { it: DialogInterface, _ ->
                    it.dismiss()
                    val intent = Intent(
                        android.provider.Settings.ACTION_SECURITY_SETTINGS
                    )
                    startActivity(intent)
                }
                create()
            }.show().secure(activity)
            it.settings().incrementShowLoginsSecureWarningCount()
        }
    }

    @Suppress("Deprecation")
    private fun showPinVerification() {
        val manager = activity?.getSystemService<KeyguardManager>()!!
        val intent = manager.createConfirmDeviceCredentialIntent(
            getString(R.string.logins_biometric_prompt_message_pin),
            getString(R.string.logins_biometric_prompt_message)
        )
        startActivityForResult(
            intent,
            PIN_REQUEST
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PIN_REQUEST && resultCode == RESULT_OK) {
            navigateToSavedLoginsFragment()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun navigateToSavedLoginsFragment() {
        context?.components?.analytics?.metrics?.track(Event.OpenLogins)
        val directions =
            SavedLoginsAuthFragmentDirections.actionSavedLoginsAuthFragmentToLoginsListFragment()
        findNavController().navigate(directions)
    }

    private fun navigateToAccountSettingsFragment() {
        val directions =
            SavedLoginsAuthFragmentDirections.actionGlobalAccountSettingsFragment()
        findNavController().navigate(directions)
    }

    private fun navigateToAccountProblemFragment() {
        val directions = SavedLoginsAuthFragmentDirections.actionGlobalAccountProblemFragment()
        findNavController().navigate(directions)
    }

    private fun navigateToTurnOnSyncFragment() {
        val directions =
            SavedLoginsAuthFragmentDirections.actionSavedLoginsAuthFragmentToTurnOnSyncFragment()
        findNavController().navigate(directions)
    }

    private fun navigateToSaveLoginSettingFragment() {
        val directions =
            SavedLoginsAuthFragmentDirections.actionSavedLoginsAuthFragmentToSavedLoginsSettingFragment()
        findNavController().navigate(directions)
    }

    companion object {
        const val SHORT_DELAY_MS = 100L
        private const val LOG_TAG = "LoginsFragment"
        const val PIN_REQUEST = 303
    }
}
