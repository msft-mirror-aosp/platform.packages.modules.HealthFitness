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

package healthconnect.storage.datatypehelpers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class AppInfoHelperTest {

    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final String TEST_APP_NAME = "testAppName";

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private Drawable mDrawable;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).build();

    @Before
    public void setup() {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mDrawable.getIntrinsicHeight()).thenReturn(200);
        when(mDrawable.getIntrinsicWidth()).thenReturn(200);
    }

    @Test
    public void testAddOrUpdateAppInfoIfNotInstalled_withoutIcon_getIconFromPackageName()
            throws PackageManager.NameNotFoundException {
        when(mPackageManager.getApplicationIcon(anyString())).thenReturn(mDrawable);
        AppInfoHelper appInfoHelper = AppInfoHelper.getInstance();

        appInfoHelper.addOrUpdateAppInfoIfNotInstalled(
                mContext, TEST_PACKAGE_NAME, TEST_APP_NAME, /* onlyReplace= */ false);

        verify(mPackageManager).getApplicationIcon(TEST_PACKAGE_NAME);
    }

    @Test
    public void testAddOrUpdateAppInfoIfNotInstalled_withoutIcon_getDefaultIconIfPackageIsNotFound()
            throws PackageManager.NameNotFoundException {
        when(mPackageManager.getApplicationIcon(anyString()))
                .thenThrow(new PackageManager.NameNotFoundException());
        when(mPackageManager.getDefaultActivityIcon()).thenReturn(mDrawable);
        AppInfoHelper appInfoHelper = AppInfoHelper.getInstance();

        appInfoHelper.addOrUpdateAppInfoIfNotInstalled(
                mContext, TEST_PACKAGE_NAME, TEST_APP_NAME, /* onlyReplace= */ false);

        verify(mPackageManager).getDefaultActivityIcon();
    }
}
