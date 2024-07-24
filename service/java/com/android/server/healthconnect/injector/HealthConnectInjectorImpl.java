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

package com.android.server.healthconnect.injector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.healthconnect.permission.PackageInfoUtils;

import java.util.Objects;

/**
 * Injector implementation of HealthConnectInjector containing dependencies to be used in production
 * version of the module.
 *
 * @hide
 */
public class HealthConnectInjectorImpl implements HealthConnectInjector {

    private final PackageInfoUtils mPackageInfoUtils;

    public HealthConnectInjectorImpl() {
        this(new Builder());
    }

    private HealthConnectInjectorImpl(Builder builder) {
        mPackageInfoUtils =
                builder.mFakePackageInfoUtils == null
                        ? PackageInfoUtils.getInstance()
                        : builder.mFakePackageInfoUtils;
    }

    @NonNull
    @Override
    public PackageInfoUtils getPackageInfoUtils() {
        return mPackageInfoUtils;
    }

    /**
     * Returns a new Builder of Health Connect Injector
     *
     * <p>USE ONLY DURING TESTING.
     */
    public static Builder newBuilderForTest() {
        return new Builder();
    }

    /**
     * Used to build injector.
     *
     * <p>The setters are used only when we need a custom implementation of any dependency which is
     * ONLY for testing. Do not use setters if we need default implementation of a dependency.
     */
    public static class Builder {

        @Nullable private PackageInfoUtils mFakePackageInfoUtils;

        private Builder() {}

        /** Set fake or custom PackageInfoUtils */
        public Builder setPackageInfoUtils(PackageInfoUtils fakePackageInfoUtils) {
            Objects.requireNonNull(fakePackageInfoUtils);
            mFakePackageInfoUtils = fakePackageInfoUtils;
            return this;
        }

        /** Build HealthConnectInjector */
        public HealthConnectInjector build() {
            return new HealthConnectInjectorImpl(this);
        }
    }
}
