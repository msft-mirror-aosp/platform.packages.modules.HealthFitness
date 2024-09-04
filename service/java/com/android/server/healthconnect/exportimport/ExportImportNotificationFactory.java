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

package com.android.server.healthconnect.exportimport;

import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR;
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_NOT_ENOUGH_SPACE;
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_COMPLETE;
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS;
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_GENERIC_ERROR;
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_INVALID_FILE;
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_NOT_ENOUGH_SPACE;
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_VERSION_MISMATCH;

import android.annotation.Nullable;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.os.Binder;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.migration.notification.HealthConnectResourcesContext;
import com.android.server.healthconnect.notifications.HealthConnectNotificationFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Objects;
import java.util.Optional;

/**
 * Export-Import specific implementation of the HealthConnectNotificationFactory for import status
 * notifications. s
 *
 * @hide
 */
public class ExportImportNotificationFactory implements HealthConnectNotificationFactory {

    private static final String TAG = "ExportImportNotificationFactory";

    private final Context mContext;
    private final HealthConnectResourcesContext mResContext;
    private final String mChannelId;

    private Optional<Icon> mAppIcon = Optional.empty();

    private static final String IMPORT_IN_PROGRESS_NOTIFICATION_TITLE =
            "import_notification_import_in_progress_title";

    private static final String IMPORT_COMPLETE_NOTIFICATION_TITLE =
            "import_notification_import_complete_title";

    private static final String IMPORT_UNSUCCESSFUL_MORE_SPACE_NEEDED_TITLE =
            "import_notification_error_more_space_needed_title";
    private static final String IMPORT_UNSUCCESSFUL_MORE_SPACE_NEEDED_TEXT =
            "import_notification_error_more_space_needed_body_text";

    private static final String IMPORT_UNSUCCESSFUL_GENERIC_ERROR_TITLE =
            "import_notification_error_generic_error_title";
    private static final String IMPORT_UNSUCCESSFUL_GENERIC_ERROR_TEXT =
            "import_notification_error_generic_error_body_text";
    private static final String IMPORT_UNSUCCESSFUL_INVALID_FILE_TEXT =
            "import_notification_error_invalid_file_body_text";
    private static final String IMPORT_UNSUCCESSFUL_VERSION_MISMATCH_TEXT =
            "import_notification_error_version_mismatch_body_text";

    private static final String IMPORT_NOTIFICATION_COMPLETE_INTENT_BUTTON =
            "import_notification_open_intent_button";
    private static final String IMPORT_NOTIFICATION_TRY_AGAIN_INTENT_BUTTON =
            "import_notification_try_again_intent_button";
    private static final String IMPORT_NOTIFICATION_CHOOSE_FILE_INTENT_BUTTON =
            "import_notification_choose_file_intent_button";
    private static final String IMPORT_NOTIFICATION_UPDATE_NOW_INTENT_BUTTON =
            "import_notification_update_now_intent_button";

    private static final String EXPORT_UNSUCCESSFUL_GENERIC_ERROR_TITLE =
            "export_notification_error_generic_error_title";
    private static final String EXPORT_UNSUCCESSFUL_GENERIC_ERROR_TEXT =
            "export_notification_error_generic_error_body_text";
    private static final String EXPORT_UNSUCCESSFUL_M0RE_SPACE_NEEDED_TITLE =
            "export_notification_error_more_space_needed_title";
    private static final String EXPORT_UNSUCCESSFUL_MORE_SPACE_NEEDED_TEXT =
            "export_notification_error_more_space_needed_body_text";

    private static final String EXPORT_NOTIFICATION_SET_UP_INTENT_BUTTON =
            "export_notification_set_up_intent_button";
    private static final String EXPORT_NOTIFICATION_FREE_UP_SPACE_INTENT_BUTTON =
            "export_notification_free_up_space_intent_button";

    private static final String HEALTH_CONNECT_HOME_ACTION =
            "android.health.connect.action.HEALTH_HOME_SETTINGS";
    private static final String HEALTH_CONNECT_RESTART_IMPORT_ACTION =
            "android.health.connect.action.START_IMPORT_FLOW";
    private static final String HEALTH_CONNECT_RESTART_EXPORT_SETUP =
            "android.health.connect.action.START_EXPORT_SETUP";
    private static final String HEALTH_CONNECT_UPDATE_ACTION =
            "android.settings.SYSTEM_UPDATE_SETTINGS";
    private static final Intent FALLBACK_INTENT = new Intent(HEALTH_CONNECT_HOME_ACTION);

    @VisibleForTesting static final String APP_ICON_DRAWABLE_NAME = "health_connect_logo";

    public ExportImportNotificationFactory(@NonNull Context context, @NonNull String channelId) {
        mContext = context;
        mResContext = new HealthConnectResourcesContext(mContext);
        mChannelId = channelId;
    }

    @Nullable
    @Override
    @ExportImportNotificationSender.ExportImportNotificationType
    public Notification createNotification(int notificationType) {
        return switch (notificationType) {
            case NOTIFICATION_TYPE_IMPORT_IN_PROGRESS -> {
                Slog.i(TAG, "Creating 'import in progress' notification");
                yield getImportInProgressNotification();
            }
            case NOTIFICATION_TYPE_IMPORT_COMPLETE -> {
                Slog.i(TAG, "Creating 'import complete' notification");
                yield getImportCompleteNotification();
            }
            case NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_GENERIC_ERROR -> {
                Slog.i(TAG, "Creating 'generic error' notification");
                yield getImportUnsuccessfulGenericErrorNotification();
            }
            case NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_INVALID_FILE -> {
                Slog.i(TAG, "Creating 'invalid file error' notification");
                yield getImportUnsuccessfulInvalidFileNotification();
            }
            case NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_NOT_ENOUGH_SPACE -> {
                Slog.i(TAG, "Creating 'more space needed error' notification");
                yield getImportUnsuccessfulMoreSpaceNeededNotification();
            }
            case NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_VERSION_MISMATCH -> {
                Slog.i(TAG, "Creating 'version mismatch error' notification");
                yield getImportUnsuccessfulVersionMismatchNotification();
            }
            case NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR -> {
                Slog.i(TAG, "Creating 'generic error' export notification");
                yield getExportUnsuccessfulGenericErrorNotification();
            }
            case NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_NOT_ENOUGH_SPACE -> {
                Slog.i(TAG, "Creating 'more space needed error' export notification");
                yield getExportUnsuccessfulMoreSpaceNeededNotification();
            }
            default -> null;
        };
    }

    @NonNull
    @Override
    public String getStringResource(@NonNull String name) {
        return Objects.requireNonNull(mResContext.getStringByName(name));
    }

    @NonNull
    private String getStringResource(@NonNull String name, @NonNull Object formatArg) {
        return Objects.requireNonNull(mResContext.getStringByNameWithArgs(name, formatArg));
    }

    @NonNull
    private Notification getImportInProgressNotification() {
        String notificationTitle = getStringResource(IMPORT_IN_PROGRESS_NOTIFICATION_TITLE);

        return createNotificationOnlyTitle(notificationTitle).setProgress(0, 0, true).build();
    }

    @NonNull
    private Notification getImportCompleteNotification() {
        PendingIntent pendingIntent = getImportCompletePendingIntent();
        String notificationTitle = getStringResource(IMPORT_COMPLETE_NOTIFICATION_TITLE);

        Notification.Action openAction =
                new Notification.Action.Builder(
                                getAppIcon().get(),
                                getStringResource(IMPORT_NOTIFICATION_COMPLETE_INTENT_BUTTON),
                                pendingIntent)
                        .build();

        return createNotificationOnlyTitle(notificationTitle).setActions(openAction).build();
    }

    @NonNull
    private Notification getImportUnsuccessfulInvalidFileNotification() {
        PendingIntent pendingIntent = getRestartImportFlowPendingIntent();
        String notificationTitle = getStringResource(IMPORT_UNSUCCESSFUL_GENERIC_ERROR_TITLE);
        String notificationTextBody = getStringResource(IMPORT_UNSUCCESSFUL_INVALID_FILE_TEXT);

        Notification.Action restartAction =
                new Notification.Action.Builder(
                                getAppIcon().get(),
                                getStringResource(IMPORT_NOTIFICATION_CHOOSE_FILE_INTENT_BUTTON),
                                pendingIntent)
                        .build();

        return createNotificationTitleAndBodyText(notificationTitle, notificationTextBody)
                .setActions(restartAction)
                .build();
    }

    @NonNull
    private Notification getImportUnsuccessfulMoreSpaceNeededNotification() {
        String notificationTitle = getStringResource(IMPORT_UNSUCCESSFUL_MORE_SPACE_NEEDED_TITLE);
        String notificationTextBody = getStringResource(IMPORT_UNSUCCESSFUL_MORE_SPACE_NEEDED_TEXT);

        return createNotificationTitleAndBodyText(notificationTitle, notificationTextBody).build();
    }

    @NonNull
    private Notification getImportUnsuccessfulGenericErrorNotification() {
        PendingIntent pendingIntent = getRestartImportFlowPendingIntent();
        String notificationTitle = getStringResource(IMPORT_UNSUCCESSFUL_GENERIC_ERROR_TITLE);
        String notificationTextBody = getStringResource(IMPORT_UNSUCCESSFUL_GENERIC_ERROR_TEXT);

        Notification.Action restartAction =
                new Notification.Action.Builder(
                                getAppIcon().get(),
                                getStringResource(IMPORT_NOTIFICATION_TRY_AGAIN_INTENT_BUTTON),
                                pendingIntent)
                        .build();

        return createNotificationTitleAndBodyText(notificationTitle, notificationTextBody)
                .setActions(restartAction)
                .build();
    }

    @NonNull
    private Notification getImportUnsuccessfulVersionMismatchNotification() {
        PendingIntent pendingIntent = getUpgradeVersionPendingIntent();
        String notificationTitle = getStringResource(IMPORT_UNSUCCESSFUL_GENERIC_ERROR_TITLE);
        String notificationTextBody = getStringResource(IMPORT_UNSUCCESSFUL_VERSION_MISMATCH_TEXT);

        Notification.Action updateNowAction =
                new Notification.Action.Builder(
                                getAppIcon().get(),
                                getStringResource(IMPORT_NOTIFICATION_UPDATE_NOW_INTENT_BUTTON),
                                pendingIntent)
                        .build();

        return createNotificationTitleAndBodyText(notificationTitle, notificationTextBody)
                .setActions(updateNowAction)
                .build();
    }

    @NonNull
    private Notification getExportUnsuccessfulGenericErrorNotification() {
        PendingIntent pendingIntent = getRestartExportSetupPendingIntent();
        String formattedExportDate =
                DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                        .format(LocalDate.now(ZoneId.systemDefault()));
        String notificationTitle = getStringResource(EXPORT_UNSUCCESSFUL_GENERIC_ERROR_TITLE);
        String notificationTextBody =
                getStringResource(EXPORT_UNSUCCESSFUL_GENERIC_ERROR_TEXT, formattedExportDate);

        Notification.Action restartExportSetupAction =
                new Notification.Action.Builder(
                                getAppIcon().get(),
                                getStringResource(EXPORT_NOTIFICATION_SET_UP_INTENT_BUTTON),
                                pendingIntent)
                        .build();

        return createNotificationTitleAndBodyText(notificationTitle, notificationTextBody)
                .setActions(restartExportSetupAction)
                .build();
    }

    @NonNull
    private Notification getExportUnsuccessfulMoreSpaceNeededNotification() {
        String notificationTitle = getStringResource(EXPORT_UNSUCCESSFUL_M0RE_SPACE_NEEDED_TITLE);

        Notification.Builder notificationBuilder = createNotificationOnlyTitle(notificationTitle);

        return notificationBuilder.build();
    }

    @NonNull
    private PendingIntent getPendingIntent(@NonNull Intent intent) {
        final long callingId = Binder.clearCallingIdentity();
        try {
            return PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @NonNull
    private PendingIntent getImportCompletePendingIntent() {
        Intent intent = new Intent(HEALTH_CONNECT_HOME_ACTION);
        ResolveInfo result = mContext.getPackageManager().resolveActivity(intent, 0);
        return result == null ? getPendingIntent(FALLBACK_INTENT) : getPendingIntent(intent);
    }

    @NonNull
    private PendingIntent getRestartImportFlowPendingIntent() {
        Intent intent = new Intent(HEALTH_CONNECT_RESTART_IMPORT_ACTION);
        ResolveInfo result = mContext.getPackageManager().resolveActivity(intent, 0);
        return result == null ? getPendingIntent(FALLBACK_INTENT) : getPendingIntent(intent);
    }

    @NonNull
    private PendingIntent getUpgradeVersionPendingIntent() {
        Intent intent = new Intent(HEALTH_CONNECT_UPDATE_ACTION);
        ResolveInfo result = mContext.getPackageManager().resolveActivity(intent, 0);
        return result == null ? getPendingIntent(FALLBACK_INTENT) : getPendingIntent(intent);
    }

    @NonNull
    private PendingIntent getRestartExportSetupPendingIntent() {
        Intent intent = new Intent(HEALTH_CONNECT_RESTART_EXPORT_SETUP);
        ResolveInfo result = mContext.getPackageManager().resolveActivity(intent, 0);
        return result == null ? getPendingIntent(FALLBACK_INTENT) : getPendingIntent(intent);
    }

    @NonNull
    @Override
    public Optional<Icon> getAppIcon() {
        if (mAppIcon.isEmpty()) {
            Icon maybeIcon = mResContext.getIconByDrawableName(APP_ICON_DRAWABLE_NAME);
            mAppIcon = maybeIcon == null ? Optional.empty() : Optional.of(maybeIcon);
        }
        return mAppIcon;
    }

    @NonNull
    @Override
    public String[] getNotificationStringResources() {
        return new String[] {
            IMPORT_IN_PROGRESS_NOTIFICATION_TITLE,
            IMPORT_COMPLETE_NOTIFICATION_TITLE,
            IMPORT_UNSUCCESSFUL_GENERIC_ERROR_TITLE,
            IMPORT_UNSUCCESSFUL_GENERIC_ERROR_TEXT,
            IMPORT_UNSUCCESSFUL_INVALID_FILE_TEXT,
            IMPORT_UNSUCCESSFUL_MORE_SPACE_NEEDED_TITLE,
            IMPORT_UNSUCCESSFUL_MORE_SPACE_NEEDED_TEXT,
            IMPORT_UNSUCCESSFUL_VERSION_MISMATCH_TEXT,
            IMPORT_NOTIFICATION_COMPLETE_INTENT_BUTTON,
            IMPORT_NOTIFICATION_CHOOSE_FILE_INTENT_BUTTON,
            IMPORT_NOTIFICATION_TRY_AGAIN_INTENT_BUTTON,
            IMPORT_NOTIFICATION_UPDATE_NOW_INTENT_BUTTON,
            EXPORT_UNSUCCESSFUL_GENERIC_ERROR_TITLE,
            EXPORT_UNSUCCESSFUL_M0RE_SPACE_NEEDED_TITLE,
            EXPORT_UNSUCCESSFUL_GENERIC_ERROR_TEXT,
            EXPORT_UNSUCCESSFUL_MORE_SPACE_NEEDED_TEXT,
            EXPORT_NOTIFICATION_SET_UP_INTENT_BUTTON,
            EXPORT_NOTIFICATION_FREE_UP_SPACE_INTENT_BUTTON
        };
    }

    private Notification.Builder createNotificationOnlyTitle(String notificationTitle) {
        Notification.Builder notificationBuilder =
                new Notification.Builder(mContext, mChannelId)
                        .setContentTitle(notificationTitle)
                        .setAutoCancel(true);
        if (getAppIcon().isPresent()) {
            notificationBuilder.setSmallIcon(getAppIcon().get());
        }
        return notificationBuilder;
    }

    private Notification.Builder createNotificationTitleAndBodyText(
            String notificationTitle, String notificationTextBody) {
        Notification.Builder notificationBuilder = createNotificationOnlyTitle(notificationTitle);
        notificationBuilder.setStyle(new Notification.BigTextStyle().bigText(notificationTextBody));
        return notificationBuilder;
    }
}
