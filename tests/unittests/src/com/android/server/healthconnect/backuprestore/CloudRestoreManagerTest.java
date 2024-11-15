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

package healthconnect.backuprestore;

import static org.junit.Assert.assertThrows;

import android.health.connect.HealthConnectManager;
import android.health.connect.backuprestore.BackupSettings;
import android.os.Environment;
import android.platform.test.flag.junit.SetFlagsRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.backuprestore.CloudRestoreManager;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

/** Unit test for class {@link CloudRestoreManager}. */
@RunWith(AndroidJUnit4.class)
public class CloudRestoreManagerTest {

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(Environment.class)
                    .build();

    @Rule(order = 3)
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    private CloudRestoreManager mCloudRestoreManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mCloudRestoreManager = new CloudRestoreManager();
    }

    @Test
    public void whenPushSettingsForRestoreCalled_unsupportedOperationExceptionThrown() {
        BackupSettings backupSettings = new BackupSettings(0, new byte[0]);
        assertThrows(
                UnsupportedOperationException.class,
                () -> mCloudRestoreManager.pushSettingsForRestore(backupSettings));
    }
}
