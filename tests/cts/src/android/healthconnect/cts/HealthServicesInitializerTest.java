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

package android.healthconnect.cts;

import static android.healthconnect.cts.utils.TestUtils.isHardwareSupported;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.health.connect.HealthPermissions;
import android.health.connect.HealthServicesInitializer;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.healthfitness.flags.Flags;

import org.junit.Test;

public class HealthServicesInitializerTest {
    /**
     * HealthServicesInitializer.registerServiceWrappers() should only be called by
     * SystemServiceRegistry during boot up. Calling this API at any other time should throw an
     * exception.
     */
    @Test
    public void testRegisterServiceThrowsException() {
        assumeTrue(isHardwareSupported());
        assertThrows(
                IllegalStateException.class, HealthServicesInitializer::registerServiceWrappers);
    }

    /**
     * context.getSystemService(Context.HEALTHCONNECT_SERVICE) returns null on
     * unsupported devices.
     */
    @Test
    public void testHealthServiceRegisteredUnsupportedHardwareReturnsNull() {
        assumeFalse(isHardwareSupported());
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Object service = context.getSystemService(Context.HEALTHCONNECT_SERVICE);
        assertThat(service).isNull();
    }

    /**
     * context.getSystemService(Context.HEALTHCONNECT_SERVICE) returns the
     * service on supported (non-watch) devices.
     */
    @Test
    public void testHealthServiceRegisteredNonWatchSupportedHardwareReturnsNonNull() {
        assumeTrue(isHardwareSupported());
        assumeFalse(FeatureUtil.isWatch());
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Object service = context.getSystemService(Context.HEALTHCONNECT_SERVICE);
        assertThat(service).isNotNull();
    }

    /**
     * context.getSystemService(Context.HEALTHCONNECT_SERVICE) returns null on
     * watches when the package is not allowed (because it doesn't have the
     * MANAGE_HEALTH_PERMISSIONS permission).
     */
    @Test
    public void testHealthServiceRegisteredWatchUnsupportedPackageReturnsNull() {
        assumeTrue(isHardwareSupported());
        assumeTrue(FeatureUtil.isWatch());
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Object service = context.getSystemService(Context.HEALTHCONNECT_SERVICE);
        assertThat(service).isNull();
    }

    /**
     * context.getSystemService(Context.HEALTHCONNECT_SERVICE) returns the services on watches to
     * apps with the MANAGE_HEALTH_PERMISSIONS permission.
     */
    @Test
    public void testHealthServiceRegisteredWatchPackageWithPermissionReturnsNonNull()
            throws Exception {
        assumeTrue(isHardwareSupported());
        assumeTrue(FeatureUtil.isWatch());
        runWithShellPermissionIdentity(
                () -> {
                    Context context = InstrumentationRegistry.getInstrumentation().getContext();
                    Object service = context.getSystemService(Context.HEALTHCONNECT_SERVICE);
                    assertThat(service).isNotNull();
                },
                HealthPermissions.MANAGE_HEALTH_PERMISSIONS);
    }
}