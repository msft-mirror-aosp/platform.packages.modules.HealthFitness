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

package android.healthconnect.tests.exportimport;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.os.OutcomeReceiver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link OutcomeReceiver} that is useful in tests for verifying results or
 * errors. This implementation should be used with OutcomeReceiver that sends a void result, i.e.
 * {@code OutcomeReceiver<Void, E extends Throwable>}.
 *
 * @param <T> The type of the result being sent
 * @param <E> The type of the throwable for any error
 */
public class TestOutcomeReceiver<T, E extends RuntimeException> implements OutcomeReceiver<T, E> {
    private static final String TAG = "HCTestOutcomeReceiver";
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private static final Executor sExecutor =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread t = Executors.defaultThreadFactory().newThread(runnable);
                        t.setName("HealthConnectReceiver");
                        return t;
                    });

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final AtomicReference<T> mResponse = new AtomicReference<>();
    private final AtomicReference<E> mException = new AtomicReference<>();

    /**
     * Returns the response received. Fails if no response received within the default timeout.
     *
     * @throws InterruptedException if this is interrupted before any response received
     */
    public T getResponse() throws InterruptedException {
        verifyNoExceptionOrThrow();
        return mResponse.get();
    }

    /**
     * Returns the exception received. Fails if no response received within the default timeout.
     *
     * @throws InterruptedException if this is interrupted before any response received
     */
    public E assertAndGetException() throws InterruptedException {
        assertThat(mLatch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(mResponse.get()).isNull();
        return mException.get();
    }

    /**
     * Asserts that no exception is received within the default timeout. If an exception is received
     * it is rethrown by this method.
     */
    public void verifyNoExceptionOrThrow() throws InterruptedException {
        verifyNoExceptionOrThrow(DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Asserts that no exception is received within the given timeout. If an exception is received
     * it is rethrown by this method.
     */
    public void verifyNoExceptionOrThrow(int timeoutSeconds) throws InterruptedException {
        assertThat(mLatch.await(timeoutSeconds, TimeUnit.SECONDS)).isTrue();
        if (mException.get() != null) {
            throw mException.get();
        }
    }

    @Override
    public void onResult(T result) {
        mResponse.set(result);
        mLatch.countDown();
    }

    @Override
    public void onError(@NonNull E error) {
        mException.set(error);
        Log.e(TAG, "onError", error);
        mLatch.countDown();
    }

    /** Returns an {@code Executor} that can be used for delivering an outcome to a receiver. */
    public static Executor outcomeExecutor() {
        return sExecutor;
    }

    /**
     * Wraps an API call that returns its response via an {@link OutcomeReceiver}.
     *
     * @see #callAndGetResponse(Class, CallableForOutcome)
     */
    public interface CallableForOutcome<R, E extends RuntimeException> {
        /** Calls the API method with the specified {@code executor} and {@code receiver}. */
        void call(Executor executor, TestOutcomeReceiver<R, E> receiver);
    }

    /**
     * Helper for calling an API method that returns its response via an {@link OutcomeReceiver}.
     */
    public static <E extends RuntimeException> Void callAndGetResponse(
            Class<E> exceptionType, CallableForOutcome<Void, E> callable)
            throws InterruptedException {
        TestOutcomeReceiver<Void, E> receiver = new TestOutcomeReceiver<>();
        callable.call(sExecutor, receiver);
        return receiver.getResponse();
    }

    /**
     * Helper for calling an API method that returns its response via an {@link OutcomeReceiver}
     * while holding permissions via {@link UiAutomation#adoptShellPermissionIdentity(String...)}.
     */
    public static <E extends RuntimeException> Void callAndGetResponseWithShellPermissionIdentity(
            Class<E> exceptionType, CallableForOutcome<Void, E> callable, String... permissions)
            throws InterruptedException {
        // Don't use SystemUtil.runWithShellPermissionIdentity as that wraps all thrown exceptions
        // in RuntimeException, which is unnecessary for our APIs which don't throw any
        // checked exceptions.
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.adoptShellPermissionIdentity(permissions);
        try {
            return callAndGetResponse(exceptionType, callable);
        } finally {
            automation.dropShellPermissionIdentity();
        }
    }
}
