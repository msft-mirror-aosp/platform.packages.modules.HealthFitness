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

package android.health.connect;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.health.connect.aidl.IHealthConnectService;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

@RunWith(JUnit4.class)
public class HealthConnectManagerTest {

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock IHealthConnectService mService;

    @Test
    public void testHealthConnectManager_getNoGrantedHealthPermissions_succeeds() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager healthConnectManager = newHealthConnectManager(context, mService);

        List<String> grantedHealthPermissions =
                healthConnectManager.getGrantedHealthPermissions("com.foo.bar");

        assertThat(grantedHealthPermissions).isEmpty();
    }

    @Test
    public void testHealthConnectManager_getSomeGrantedHealthPermissions_succeeds()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager healthConnectManager = newHealthConnectManager(context, mService);
        when(mService.getGrantedHealthPermissions(any(), any()))
                .thenReturn(
                        ImmutableList.of(
                                "android.permission.health.READ_HEART_RATE",
                                "android.permission.health.WRITE_HEART_RATE"));

        List<String> grantedHealthPermissions =
                healthConnectManager.getGrantedHealthPermissions("com.foo.bar");

        assertThat(grantedHealthPermissions)
                .containsExactly(
                        "android.permission.health.READ_HEART_RATE",
                        "android.permission.health.WRITE_HEART_RATE");
    }

    @Test
    public void testHealthConnectManager_getGrantedHealthPermissionsException_rethrows()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager healthConnectManager = newHealthConnectManager(context, mService);
        when(mService.getGrantedHealthPermissions(any(), any()))
                .thenThrow(new RemoteException("message"));

        assertThrows(
                RuntimeException.class,
                () -> healthConnectManager.getGrantedHealthPermissions("com.foo.bar"));
    }

    /**
     * Constructs a {@link HealthConnectManager} using reflection to access the constructor.
     *
     * <p>Unfortunately the {@link HealthConnectManager} loads from a different classloader to the
     * unit test, so even though they are in the same package name, technically the packages are
     * different. This leads to calling the constructor giving an {@link IllegalAccessError}. By
     * using reflection we can avoid this error in test cases, but this code should not be used
     * outside of testing.
     */
    private static HealthConnectManager newHealthConnectManager(
            Context context, IHealthConnectService service)
            throws InstantiationException,
                    IllegalAccessException,
                    InvocationTargetException,
                    NoSuchMethodException {
        return HealthConnectManager.class
                .getDeclaredConstructor(Context.class, IHealthConnectService.class)
                .newInstance(context, service);
    }
}
