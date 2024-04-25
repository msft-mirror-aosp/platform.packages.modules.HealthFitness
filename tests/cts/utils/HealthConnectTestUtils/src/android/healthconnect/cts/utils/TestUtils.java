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

package android.healthconnect.cts.utils;

import static com.android.compatibility.common.util.FeatureUtil.AUTOMOTIVE_FEATURE;
import static com.android.compatibility.common.util.FeatureUtil.hasSystemFeature;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

public final class TestUtils {

    public static boolean isHardwareAutomotive() {
        return hasSystemFeature(AUTOMOTIVE_FEATURE);
    }

    public static boolean isHardwareSupported() {
        return isHardwareSupported(ApplicationProvider.getApplicationContext());
    }

    /** returns true if the hardware is supported by HealthConnect. */
    public static boolean isHardwareSupported(Context context) {
        PackageManager pm = context.getPackageManager();
        return (!pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)
                && !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
    }
}
