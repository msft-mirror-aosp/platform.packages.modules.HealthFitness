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
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_GENERIC_ERROR;
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_INVALID_FILE;
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_VERSION_MISMATCH;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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

    private static final String HEALTH_CONNECT_HOME_ACTION =
            "android.health.connect.action.HEALTH_HOME_SETTINGS";
    private static final String HEALTH_CONNECT_RESTART_IMPORT_ACTION =
            "android.health.connect.action.START_IMPORT_FLOW";
    private static final String HEALTH_CONNECT_UPDATE_ACTION =
            "android.settings.SYSTEM_UPDATE_SETTINGS";

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
        Intent expectedIntent = new Intent(HEALTH_CONNECT_HOME_ACTION);
        PendingIntent expectedPendingIntent =
                PendingIntent.getActivity(
                        mContext, 0, expectedIntent, PendingIntent.FLAG_IMMUTABLE);

        String failMessage = "Notification could not be created";
        assertWithMessage(failMessage).that(result).isNotNull();

        assertThat(result.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(result.extras.getString(Notification.EXTRA_TITLE)).isNotNull();

        assertThat(result.actions).hasLength(1);

        Notification.Action action = result.actions[0];
        assertThat(action.title.toString()).isEqualTo("Open");

        PendingIntent pendingIntent = action.actionIntent;
        assertThat(pendingIntent.getCreatorPackage())
                .isEqualTo(expectedPendingIntent.getCreatorPackage());
    }

    @Test
    public void importCompletesUnsuccessfully_invalidFile_notificationDisplayedCorrectly() {
        Notification result =
                mFactory.createNotification(
                        NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_INVALID_FILE,
                        NOTIFICATION_CHANNEL_ID);
        Intent expectedIntent = new Intent(HEALTH_CONNECT_RESTART_IMPORT_ACTION);
        PendingIntent expectedPendingIntent =
                PendingIntent.getActivity(
                        mContext, 0, expectedIntent, PendingIntent.FLAG_IMMUTABLE);

        String failMessage = "Notification could not be created";
        assertWithMessage(failMessage).that(result).isNotNull();

        assertThat(result.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(result.extras.getString(Notification.EXTRA_TITLE)).isNotNull();

        assertThat(result.actions).hasLength(1);

        Notification.Action action = result.actions[0];
        assertThat(action.title.toString()).isEqualTo("Choose file");

        PendingIntent pendingIntent = action.actionIntent;
        assertThat(pendingIntent.getCreatorPackage())
                .isEqualTo(expectedPendingIntent.getCreatorPackage());
    }

    @Test
    public void importCompletesUnsuccessfully_versionMismatch_notificationDisplayedCorrectly() {
        Notification result =
                mFactory.createNotification(
                        NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_VERSION_MISMATCH,
                        NOTIFICATION_CHANNEL_ID);
        Intent expectedIntent = new Intent(HEALTH_CONNECT_UPDATE_ACTION);
        PendingIntent expectedPendingIntent =
                PendingIntent.getActivity(
                        mContext, 0, expectedIntent, PendingIntent.FLAG_IMMUTABLE);

        String failMessage = "Notification could not be created";
        assertWithMessage(failMessage).that(result).isNotNull();

        assertThat(result.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(result.extras.getString(Notification.EXTRA_TITLE)).isNotNull();

        assertThat(result.actions).hasLength(1);

        Notification.Action action = result.actions[0];
        assertThat(action.title.toString()).isEqualTo("Update now");

        PendingIntent pendingIntent = action.actionIntent;
        assertThat(pendingIntent.getCreatorPackage())
                .isEqualTo(expectedPendingIntent.getCreatorPackage());
    }

    @Test
    public void importCompletesUnsuccessfully_unknownError_notificationDisplayedCorrectly() {
        Notification result =
                mFactory.createNotification(
                        NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_GENERIC_ERROR,
                        NOTIFICATION_CHANNEL_ID);
        Intent expectedIntent = new Intent(HEALTH_CONNECT_RESTART_IMPORT_ACTION);
        PendingIntent expectedPendingIntent =
                PendingIntent.getActivity(
                        mContext, 0, expectedIntent, PendingIntent.FLAG_IMMUTABLE);

        String failMessage = "Notification could not be created";
        assertWithMessage(failMessage).that(result).isNotNull();

        assertThat(result.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(result.extras.getString(Notification.EXTRA_TITLE)).isNotNull();

        assertThat(result.actions).hasLength(1);

        Notification.Action action = result.actions[0];
        assertThat(action.title.toString()).isEqualTo("Try again");

        PendingIntent pendingIntent = action.actionIntent;

        assertThat(pendingIntent.getCreatorPackage())
                .isEqualTo(expectedPendingIntent.getCreatorPackage());
    }
}
