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

package healthconnect.exportimport;

import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_COMPLETE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Notification;
import android.content.Context;
import android.graphics.drawable.Icon;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.healthconnect.exportimport.ExportImportNotificationFactory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Optional;

public class ExportImportNotificationFactoryTest {

    @Mock private Context mContext;

    private static final String NOTIFICATION_CHANNEL_ID = "healthconnect-channel";

    private ExportImportNotificationFactory mFactory;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mFactory = new ExportImportNotificationFactory(mContext);
    }

    @Test
    public void testAllNotificationStringsExist() {
        String[] expectedStrings = mFactory.getNotificationStringResources();
        for (String s : expectedStrings) {
            String fetched = mFactory.getStringResource(s);
            String failMessage = "String resource with name " + s + " cannot be found.";
            assertWithMessage(failMessage).that(fetched).isNotNull();
        }
    }

    @Test
    public void testAppIconDrawableExists() {
        Optional<Icon> fetchedAppIcon = mFactory.getAppIcon();
        String failMessage = "Drawable resource with name 'health_connect_logo' cannot be found.";
        assertWithMessage(failMessage).that(fetchedAppIcon).isNotNull();
    }

    @Test
    public void importCompletesSuccessfully_notificationDisplayedCorrectly() {
        Notification result =
                mFactory.createNotification(
                        NOTIFICATION_TYPE_IMPORT_COMPLETE, NOTIFICATION_CHANNEL_ID);
        String failMessage = "Notification could not be created";
        assertWithMessage(failMessage).that(result).isNotNull();
        assertThat(result.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(result.extras.getString(Notification.EXTRA_TITLE)).isNotNull();
    }
}
