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

package com.android.server.healthconnect.notifications;

import static com.android.healthfitness.flags.Flags.FLAG_EXPORT_IMPORT;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.UserHandle;
import android.util.Log;
import android.util.Slog;

import java.util.Objects;

/**
 * Abstraction for the Health Connect NotificationSender
 *
 * @hide
 */
@FlaggedApi(FLAG_EXPORT_IMPORT)
public final class HealthConnectNotificationSender {

    private static final String TAG = "HealthConnectNotificationSender";

    private final Context mContext;
    private final HealthConnectNotificationFactory mNotificationFactory;
    private final int mFixedNotificationId;
    private final String mNotificationTag;
    private final String mChannelId;
    private final String mChannelGroupId;
    private final String mChannelNameResource;
    private final boolean mIsEnabled;

    public HealthConnectNotificationSender(Builder builder) {
        if (builder.mContext == null
                || builder.mNotificationFactory == null
                || builder.mNotificationTag == null
                || builder.mChannelId == null
                || builder.mChannelGroupId == null
                || builder.mChannelNameResource == null) {
            throw new IllegalArgumentException("Values cannot be null");
        }
        this.mContext = builder.mContext;
        this.mNotificationFactory = builder.mNotificationFactory;
        this.mFixedNotificationId = builder.mFixedNotificationId;
        this.mNotificationTag = builder.mNotificationTag;
        this.mChannelId = builder.mChannelId;
        this.mChannelGroupId = builder.mChannelGroupId;
        this.mChannelNameResource = builder.mChannelNameResource;
        this.mIsEnabled = builder.mIsEnabled;
    }

    public static final class Builder {

        @Nullable private Context mContext;
        @Nullable private HealthConnectNotificationFactory mNotificationFactory;
        private int mFixedNotificationId;
        @Nullable private String mNotificationTag;
        @Nullable private String mChannelId;
        @Nullable private String mChannelGroupId;
        @Nullable private String mChannelNameResource;
        private boolean mIsEnabled;

        /** provide notification sender with context */
        public Builder setContext(@NonNull Context context) {
            this.mContext = context;
            return this;
        }

        /**
         * set the current status of the notification sender e.g. may want to only enable
         * notifications if a specific Flag is enabled
         */
        public Builder setIsEnabled(boolean isEnabled) {
            this.mIsEnabled = isEnabled;
            return this;
        }

        /** provide notification sender with notification factory */
        public Builder setNotificationFactory(
                @NonNull HealthConnectNotificationFactory notificationFactory) {
            this.mNotificationFactory = notificationFactory;
            return this;
        }

        /** set notification ID */
        public Builder setFixedNotificationId(int fixedNotificationId) {
            this.mFixedNotificationId = fixedNotificationId;
            return this;
        }

        /** set the identifying tag for notifications */
        public Builder setNotificationTag(@NonNull String notificationTag) {
            this.mNotificationTag = notificationTag;
            return this;
        }

        /** set the notification channel ID */
        public Builder setChannelId(@NonNull String channelId) {
            this.mChannelId = channelId;
            return this;
        }

        /** set the notification channel group ID */
        public Builder setChannelGroupId(@NonNull String channelGroupId) {
            this.mChannelGroupId = channelGroupId;
            return this;
        }

        /** set the name of the notification channel */
        public Builder setChannelNameResource(@NonNull String channelNameResource) {
            this.mChannelNameResource = channelNameResource;
            return this;
        }

        /** build the notification sender */
        @NonNull
        public HealthConnectNotificationSender build() {
            if (this.mChannelGroupId == null
                    || this.mChannelId == null
                    || this.mNotificationTag == null
                    || this.mNotificationFactory == null
                    || this.mContext == null
                    || this.mChannelNameResource == null) {
                throw new IllegalArgumentException("Cannot have null parameter.");
            }
            return new HealthConnectNotificationSender(this);
        }
    }

    /** Creates a notification determined by the passed-in type and displays it to the user. */
    public void sendNotificationAsUser(
            @HealthConnectNotificationType int notificationType, @NonNull UserHandle userHandle) {

        if (!mIsEnabled) {
            Slog.i(TAG, "Notifications have been disabled.");
            return;
        }

        createNotificationChannel(userHandle);
        Notification notification =
                mNotificationFactory.createNotification(notificationType, mChannelId);
        if (notification == null) return;
        NotificationManager notificationManager = getNotificationManagerForUser(userHandle);
        notifyFromSystem(notificationManager, notification);
    }

    /** Cancels all Health Connect notifications on this channel. */
    public void clearNotificationsAsUser(@NonNull UserHandle userHandle) {
        if (!mIsEnabled) return;
        NotificationManager notificationManager = getNotificationManagerForUser(userHandle);
        cancelFromSystem(notificationManager);
    }

    /** Returns a {@link NotificationManager} which will send notifications to the given user. */
    @NonNull
    private NotificationManager getNotificationManagerForUser(@NonNull UserHandle userHandle) {
        Context contextAsUser = mContext.createContextAsUser(userHandle, 0);
        return Objects.requireNonNull(contextAsUser.getSystemService(NotificationManager.class));
    }

    private void notifyFromSystem(
            @NonNull NotificationManager notificationManager, @NonNull Notification notification) {
        final long callingId = Binder.clearCallingIdentity();
        try {
            notificationManager.notify(mNotificationTag, mFixedNotificationId, notification);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to send system notification", e);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private void cancelFromSystem(@NonNull NotificationManager notificationManager) {
        final long callingId = Binder.clearCallingIdentity();
        try {
            notificationManager.cancel(mNotificationTag, mFixedNotificationId);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to cancel system notification", e);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private void createNotificationChannel(@NonNull UserHandle userHandle) {
        CharSequence channelName = mNotificationFactory.getStringResource(mChannelNameResource);

        NotificationChannelGroup group = new NotificationChannelGroup(mChannelGroupId, channelName);

        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel notificationChannel =
                new NotificationChannel(mChannelId, channelName, importance);
        notificationChannel.setGroup(mChannelGroupId);
        notificationChannel.setBlockable(false);

        final long callingId = Binder.clearCallingIdentity();

        NotificationManager notificationManager = getNotificationManagerForUser(userHandle);

        try {
            notificationManager.createNotificationChannelGroup(group);
            notificationManager.createNotificationChannel(notificationChannel);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to create notification channel", e);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /** @hide */
    @FlaggedApi(FLAG_EXPORT_IMPORT)
    public @interface HealthConnectNotificationType {}
}
