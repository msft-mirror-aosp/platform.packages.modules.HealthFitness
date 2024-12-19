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

import static java.util.Collections.emptyList;

import android.health.connect.HealthConnectManager;
import android.health.connect.backuprestore.BackupSettings;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.EnvironmentFixture;
import com.android.server.healthconnect.SQLiteDatabaseFixture;
import com.android.server.healthconnect.backuprestore.CloudRestoreManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for class {@link CloudRestoreManager}. */
@RunWith(AndroidJUnit4.class)
public class CloudRestoreManagerTest {

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .addStaticMockFixtures(EnvironmentFixture::new, SQLiteDatabaseFixture::new)
                    .build();

    private CloudRestoreManager mCloudRestoreManager;

    @Before
    public void setUp() {
        mCloudRestoreManager = new CloudRestoreManager();
    }

    @Test
    public void whenPushSettingsForRestoreCalled_unsupportedOperationExceptionThrown() {
        BackupSettings backupSettings = new BackupSettings(0, new byte[0]);
        assertThrows(
                UnsupportedOperationException.class,
                () -> mCloudRestoreManager.pushSettingsForRestore(backupSettings));
    }

    @Test
    public void canRestore_unsupportedOperationExceptionThrown() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> mCloudRestoreManager.canRestore(/* dataVersion= */ 1));
    }

    @Test
    public void pushChangesForRestore_unsupportedOperationExceptionThrown() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> mCloudRestoreManager.pushChangesForRestore(emptyList()));
    }
}
