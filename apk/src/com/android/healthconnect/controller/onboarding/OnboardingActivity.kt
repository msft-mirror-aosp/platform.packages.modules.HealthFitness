/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.android.healthconnect.controller.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.shared.Constants.ONBOARDING_SHOWN_PREF_KEY
import com.android.healthconnect.controller.shared.Constants.USER_ACTIVITY_TRACKER
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.OnboardingElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthfitness.flags.AconfigFlagHelper.isPersonalHealthRecordEnabled
import com.android.settingslib.collapsingtoolbar.EdgeToEdgeUtils
import com.google.android.setupcompat.template.FooterBarMixin
import com.google.android.setupcompat.template.FooterButton
import com.google.android.setupdesign.GlifLayout
import com.google.android.setupdesign.template.DescriptionMixin
import com.google.android.setupdesign.template.FloatingBackButtonMixin
import com.google.android.setupdesign.template.HeaderMixin
import com.google.android.setupdesign.template.IconMixin
import com.google.android.setupdesign.util.HeaderAreaStyler
import com.google.android.setupdesign.util.ThemeHelper
import com.google.android.setupdesign.view.RichTextView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Onboarding activity for Health Connect. */
@AndroidEntryPoint(FragmentActivity::class)
class OnboardingActivity : Hilt_OnboardingActivity() {

    /** Companion object for OnboardingActivity. */
    companion object {
        private const val TARGET_ACTIVITY_INTENT = "ONBOARDING_TARGET_ACTIVITY_INTENT"

        fun shouldRedirectToOnboardingActivity(activity: Activity): Boolean {
            val sharedPreference =
                activity.getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
            val previouslyOpened = sharedPreference.getBoolean(ONBOARDING_SHOWN_PREF_KEY, false)
            return !previouslyOpened
        }

        fun createIntent(context: Context, targetIntent: Intent? = null): Intent {
            val flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION

            return Intent(context, OnboardingActivity::class.java).apply {
                addFlags(flags)
                if (targetIntent != null) {
                    putExtra(TARGET_ACTIVITY_INTENT, targetIntent)
                }
            }
        }
    }

    @Inject lateinit var logger: HealthConnectLogger

    private var targetIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This flag ensures a non system app cannot show an overlay on Health Connect. b/313425281
        window.addSystemFlags(
            WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
        )
        if (intent.hasExtra(TARGET_ACTIVITY_INTENT)) {
            targetIntent = intent.getParcelableExtra(TARGET_ACTIVITY_INTENT)
        }

        logger.setPageId(PageName.ONBOARDING_PAGE)

        if (ThemeHelper.shouldApplyGlifExpressiveStyle(this)) {
            setupExpressiveOnboarding()
        } else {
            setupLegacyOnboarding()
        }
    }

    override fun onResume() {
        super.onResume()
        logger.logPageImpression()
    }

    private fun setupExpressiveOnboarding() {
        ThemeHelper.trySetSuwTheme(this)
        setContentView(R.layout.onboarding_screen)
        val layout = findViewById<GlifLayout>(R.id.onboarding)

        val headerMixin = layout.getMixin(HeaderMixin::class.java)
        headerMixin.text = getString(R.string.onboarding_title)

        val descriptionMixin = layout.getMixin(DescriptionMixin::class.java)
        val withHealthConnectTitle =
            findViewById<TextView>(R.id.onboarding_description_with_health_connect)

        if (isPersonalHealthRecordEnabled()) {
            descriptionMixin.setText(R.string.onboarding_description_health_records)
            withHealthConnectTitle.visibility = View.VISIBLE
            logger.logImpression(OnboardingElement.ONBOARDING_MESSAGE_WITH_PHR)
        } else {
            descriptionMixin.setText(R.string.onboarding_description)
            withHealthConnectTitle.visibility = View.GONE
        }

        val shareText = findViewById<RichTextView>(R.id.share_text)
        HeaderAreaStyler.applyPartnerCustomizationDescriptionHeavyStyle(shareText)
        val manageText = findViewById<RichTextView>(R.id.manage_text)
        HeaderAreaStyler.applyPartnerCustomizationDescriptionHeavyStyle(manageText)

        val footerBarMixin = layout.getMixin(FooterBarMixin::class.java)
        logger.logImpression(OnboardingElement.ONBOARDING_GO_BACK_BUTTON)
        logger.logImpression(OnboardingElement.ONBOARDING_COMPLETED_BUTTON)

        val sharedPreference = getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        footerBarMixin?.setPrimaryButton(
            FooterButton.Builder(this)
                .setText(R.string.onboarding_get_started_button_text)
                .setButtonType(FooterButton.ButtonType.NEXT)
                .setListener {
                    logger.logInteraction(OnboardingElement.ONBOARDING_COMPLETED_BUTTON)
                    val editor = sharedPreference.edit()
                    editor.putBoolean(ONBOARDING_SHOWN_PREF_KEY, true)
                    editor.apply()
                    if (targetIntent == null) {
                        setResult(Activity.RESULT_OK)
                    } else {
                        startActivity(targetIntent)
                    }
                    finish()
                }
                .build()
        )

        footerBarMixin?.secondaryButton =
            FooterButton.Builder(this)
                .setText(R.string.onboarding_go_back_button_text)
                .setButtonType(FooterButton.ButtonType.SKIP)
                .setListener {
                    logger.logInteraction(OnboardingElement.ONBOARDING_GO_BACK_BUTTON)
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
                .build()

        layout.getMixin(FloatingBackButtonMixin::class.java)?.visibility = View.GONE
        layout.getMixin(IconMixin::class.java)?.setVisibility(View.GONE)
    }

    private fun setupLegacyOnboarding() {
        setContentView(R.layout.onboarding_legacy_screen)
        EdgeToEdgeUtils.enable(this)

        val onboardingDescription = findViewById<TextView>(R.id.onboarding_description)
        val withHealthConnectTitle =
            findViewById<TextView>(R.id.onboarding_description_with_health_connect)
        if (isPersonalHealthRecordEnabled()) {
            onboardingDescription.setText(R.string.onboarding_description_health_records)
            withHealthConnectTitle.setVisibility(View.VISIBLE)
            logger.logImpression(OnboardingElement.ONBOARDING_MESSAGE_WITH_PHR)
        } else {
            onboardingDescription.setText(R.string.onboarding_description)
            withHealthConnectTitle.setVisibility(View.GONE)
        }

        val sharedPreference = getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val goBackButton = findViewById<Button>(R.id.go_back_button)
        val getStartedButton = findViewById<Button>(R.id.get_started_button)
        logger.logImpression(OnboardingElement.ONBOARDING_GO_BACK_BUTTON)
        logger.logImpression(OnboardingElement.ONBOARDING_COMPLETED_BUTTON)

        goBackButton.setOnClickListener {
            logger.logInteraction(OnboardingElement.ONBOARDING_GO_BACK_BUTTON)
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        getStartedButton.setOnClickListener {
            logger.logInteraction(OnboardingElement.ONBOARDING_COMPLETED_BUTTON)
            val editor = sharedPreference.edit()
            editor.putBoolean(ONBOARDING_SHOWN_PREF_KEY, true)
            editor.apply()
            if (targetIntent == null) {
                setResult(Activity.RESULT_OK)
            } else {
                startActivity(targetIntent)
            }
            finish()
        }
    }
}
