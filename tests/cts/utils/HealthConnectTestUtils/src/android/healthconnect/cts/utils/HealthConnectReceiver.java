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

package android.healthconnect.cts.utils;

import android.app.UiAutomation;
import android.health.connect.HealthConnectException;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A specialization of {@link TestOutcomeReceiver} for the case when {@link HealthConnectException}
 * may be thrown.
 *
 * @param <T> the type of object being received,
 */
public final class HealthConnectReceiver<T> extends TestOutcomeReceiver<T, HealthConnectException> {

    private static final Executor sExecutor =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread t = Executors.defaultThreadFactory().newThread(runnable);
                        t.setName("HealthConnectReceiver");
                        return t;
                    });

    /** Returns an {@code Executor} that can be used for delivering an outcome to a receiver. */
    public static Executor outcomeExecutor() {
        return sExecutor;
    }

    /**
     * Wraps an API call that returns its response via an {@link android.os.OutcomeReceiver}.
     *
     * @see #callAndGetResponse(CallableForOutcome)
     */
    public interface CallableForOutcome<R> {
        /** Calls the API method with the specified {@code executor} and {@code receiver}. */
        void call(Executor executor, HealthConnectReceiver<R> receiver);
    }

    /**
     * Helper for calling an API method that returns its response via an {@link
     * android.os.OutcomeReceiver}.
     */
    public static <R> R callAndGetResponse(CallableForOutcome<R> callable)
            throws InterruptedException {
        HealthConnectReceiver<R> receiver = new HealthConnectReceiver<>();
        callable.call(sExecutor, receiver);
        return receiver.getResponse();
    }

    /**
     * Helper for calling an API method that returns its response via an {@link
     * android.os.OutcomeReceiver} while holding permissions via {@link
     * UiAutomation#adoptShellPermissionIdentity(String...)}.
     */
    public static <R> R callAndGetResponseWithShellPermissionIdentity(
            CallableForOutcome<R> callable, String... permissions) throws InterruptedException {
        // Don't use SystemUtil.runWithShellPermissionIdentity as that wraps all thrown exceptions
        // in RuntimeException, which is unnecessary for our APIs which don't throw any
        // checked exceptions.
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.adoptShellPermissionIdentity(permissions);
        try {
            return callAndGetResponse(callable);
        } finally {
            automation.dropShellPermissionIdentity();
        }
    }
}
