/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.healthconnect.controller.shared

object Constants {
    // Onboarding
    const val USER_ACTIVITY_TRACKER = "USER_ACTIVITY_TRACKER"
    const val ONBOARDING_SHOWN_PREF_KEY = "ONBOARDING_SHOWN_PREF_KEY"

    // Migration
    const val APP_UPDATE_NEEDED_BANNER_SEEN = "app_update_banner_seen"
    const val INTEGRATION_PAUSED_SEEN_KEY = "integration_paused_seen"
    const val APP_UPDATE_NEEDED_SEEN = "App Update Seen"
    const val MODULE_UPDATE_NEEDED_SEEN = "Module Update Seen"
    const val WHATS_NEW_DIALOG_SEEN = "Whats New Seen"
    const val MIGRATION_NOT_COMPLETE_DIALOG_SEEN = "Migration Not Complete Seen"

    // Connected apps
    const val EXTRA_APP_NAME = "app_name_extras"
    const val SHOW_MANAGE_APP_SECTION = "show_manage_app_section"

    // Onboarding banners
    const val START_USING_HC_BANNER_SEEN = "start_using_hc_seen"
    const val CONNECT_MORE_APPS_BANNER_SEEN = "connect_more_apps_seen"
    const val SEE_MORE_COMPATIBLE_APPS_BANNER_SEEN = "see_more_apps_seen"

    // Lock screen banners
    const val LOCK_SCREEN_BANNER_SEEN_FITNESS = "lock_screen_banner_seen_fitness"
    const val LOCK_SCREEN_BANNER_SEEN_MEDICAL = "lock_screen_banner_seen_medical"
}
