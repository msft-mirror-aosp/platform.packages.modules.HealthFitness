/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.healthconnect.storage.utils;

import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

/**
 * Manages entries in {@link PreferenceHelper} which do not belong to a specific class.
 *
 * @hide
 */
public class PreferencesManager {

    private static final String AUTO_DELETE_DURATION_RECORDS_KEY =
            "auto_delete_duration_records_key";

    private final PreferenceHelper mPreferenceHelper;

    public PreferencesManager(PreferenceHelper preferenceHelper) {
        mPreferenceHelper = preferenceHelper;
    }

    /** Gets auto delete period for automatically deleting record entries */
    public int getRecordRetentionPeriodInDays() {
        String result = mPreferenceHelper.getPreference(AUTO_DELETE_DURATION_RECORDS_KEY);

        if (result == null) return 0;
        return Integer.parseInt(result);
    }

    /** Sets auto delete period for automatically deleting record entries */
    public void setRecordRetentionPeriodInDays(int days) {
        mPreferenceHelper.insertOrReplacePreference(
                AUTO_DELETE_DURATION_RECORDS_KEY, String.valueOf(days));
    }
}
