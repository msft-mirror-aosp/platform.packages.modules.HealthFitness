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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.permission.PackageInfoUtils;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;

import java.util.Objects;

/**
 * Injector implementation of HealthConnectInjector containing dependencies to be used in production
 * version of the module.
 *
 * @hide
 */
public class HealthConnectInjectorImpl extends HealthConnectInjector {

    private final PackageInfoUtils mPackageInfoUtils;
    private final TransactionManager mTransactionManager;
    private final HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;

    public HealthConnectInjectorImpl(Context context) {
        this(new Builder(context));
    }

    private HealthConnectInjectorImpl(Builder builder) {
        mPackageInfoUtils =
                builder.mPackageInfoUtils == null
                        ? PackageInfoUtils.getInstance()
                        : builder.mPackageInfoUtils;
        mTransactionManager =
                builder.mTransactionManager == null
                        ? TransactionManager.initializeInstance(builder.mHealthConnectUserContext)
                        : builder.mTransactionManager;
        mHealthDataCategoryPriorityHelper =
                builder.mHealthDataCategoryPriorityHelper == null
                        ? HealthDataCategoryPriorityHelper.getInstance()
                        : builder.mHealthDataCategoryPriorityHelper;
    }

    @NonNull
    @Override
    public PackageInfoUtils getPackageInfoUtils() {
        return mPackageInfoUtils;
    }

    @NonNull
    @Override
    public TransactionManager getTransactionManager() {
        return mTransactionManager;
    }

    @NonNull
    @Override
    public HealthDataCategoryPriorityHelper getHealthDataCategoryPriorityHelper() {
        return mHealthDataCategoryPriorityHelper;
    }

    /**
     * Returns a new Builder of Health Connect Injector
     *
     * <p>USE ONLY DURING TESTING.
     */
    public static Builder newBuilderForTest(Context context) {
        return new Builder(context);
    }

    /**
     * Used to build injector.
     *
     * <p>The setters are used only when we need a custom implementation of any dependency which is
     * ONLY for testing. Do not use setters if we need default implementation of a dependency.
     */
    public static class Builder {

        private final HealthConnectUserContext mHealthConnectUserContext;

        @Nullable private PackageInfoUtils mPackageInfoUtils;
        @Nullable private TransactionManager mTransactionManager;
        @Nullable private HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;

        private Builder(Context context) {
            mHealthConnectUserContext = new HealthConnectUserContext(context, context.getUser());
        }

        /** Set fake or custom PackageInfoUtils */
        public Builder setPackageInfoUtils(PackageInfoUtils packageInfoUtils) {
            Objects.requireNonNull(packageInfoUtils);
            mPackageInfoUtils = packageInfoUtils;
            return this;
        }

        /** Set fake or custom TransactionManager */
        public Builder setTransactionManager(TransactionManager transactionManager) {
            Objects.requireNonNull(transactionManager);
            mTransactionManager = transactionManager;
            return this;
        }

        /** Set fake or custom HealthDataCategoryPriorityHelper */
        public Builder setHealthDataCategoryPriorityHelper(
                HealthDataCategoryPriorityHelper healthDataCategoryPriorityHelper) {
            Objects.requireNonNull(healthDataCategoryPriorityHelper);
            mHealthDataCategoryPriorityHelper = healthDataCategoryPriorityHelper;
            return this;
        }

        /** Build HealthConnectInjector */
        public HealthConnectInjector build() {
            return new HealthConnectInjectorImpl(this);
        }
    }
}
