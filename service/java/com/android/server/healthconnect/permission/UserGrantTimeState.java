/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.healthconnect.permission;

import android.annotation.Nullable;
import android.util.ArrayMap;

import com.android.internal.annotations.VisibleForTesting;

import java.time.Instant;
import java.util.Map;

/**
 * State of user health permissions first grant times. Used by {@link FirstGrantTimeDatastore}.
 *
 * @hide
 */
public class UserGrantTimeState {
    /** Special value for {@link #mVersion} to indicate that no version was read. */
    public static final int NO_VERSION = -1;

    /** The first grant times by packages. */
    private final Map<String, Instant> mPackagePermissions;

    /** The first grant time of shared users. */
    private final Map<String, Instant> mSharedUserPermissions;

    /** The version of the grant times state. */
    private final int mVersion;

    UserGrantTimeState(int version) {
        this(new ArrayMap<>(), new ArrayMap<>(), version);
    }

    @VisibleForTesting
    public UserGrantTimeState(
            Map<String, Instant> packagePermissions,
            Map<String, Instant> sharedUserPermissions,
            int version) {
        mPackagePermissions = packagePermissions;
        mSharedUserPermissions = sharedUserPermissions;
        mVersion = version;
    }

    Map<String, Instant> getPackageGrantTimes() {
        return mPackagePermissions;
    }

    Map<String, Instant> getSharedUserGrantTimes() {
        return mSharedUserPermissions;
    }

    void setPackageGrantTime(String packageName, @Nullable Instant time) {
        mPackagePermissions.put(packageName, time);
    }

    void setSharedUserGrantTime(String sharedUserId, @Nullable Instant time) {
        mSharedUserPermissions.put(sharedUserId, time);
    }

    boolean containsPackageGrantTime(String packageName) {
        return mPackagePermissions.containsKey(packageName);
    }

    boolean containsSharedUserGrantTime(String sharedUserId) {
        return mSharedUserPermissions.containsKey(sharedUserId);
    }

    /**
     * Get the version of the grant time.
     *
     * @return the version of the grant time
     */
    int getVersion() {
        return mVersion;
    }

    @Override
    public String toString() {
        return "GrantTimeState{version="
                + mVersion
                + ",packagePermissions="
                + mPackagePermissions.toString()
                + ",sharedUserPermissions="
                + mSharedUserPermissions.toString()
                + "}";
    }
}
