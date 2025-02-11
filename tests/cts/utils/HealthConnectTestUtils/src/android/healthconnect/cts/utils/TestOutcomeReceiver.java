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

import static com.google.common.truth.Truth.assertThat;

import android.os.OutcomeReceiver;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link OutcomeReceiver} that is useful in tests for verifying results or
 * errors.
 *
 * @param <T> The type of the result being sent
 * @param <E> The type of the throwable for any error
 */
public class TestOutcomeReceiver<T, E extends RuntimeException> implements OutcomeReceiver<T, E> {
    private static final String TAG = "HCTestOutcomeReceiver";
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final AtomicReference<T> mResponse = new AtomicReference<>();
    private final AtomicReference<E> mException = new AtomicReference<>();

    /**
     * Returns the resppnse received. Fails if no response received within the default timeout.
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
        Log.e(TAG, error.getMessage());
        mLatch.countDown();
    }
}
