/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.healthconnect.datatypes;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.healthconnect.datatypes.units.Length;

import java.time.Instant;
import java.util.Objects;

/**
 * Captures the time of a lap within exercise session. Part of {@link ExerciseSessionRecord}.
 *
 * <p>Each record contains the start and end time and optional {@link Length} of the lap (e.g. pool
 * length while swimming or a track lap while running). There may or may not be direct correlation
 * with {@link ExerciseSegment} start and end times, e.g. {@link ExerciseSessionRecord} of type
 * running without any segments can be divided as laps of different lengths.
 */
public final class ExerciseLap {
    private static final int MAX_LAP_LENGTH_METRES = 10000000;

    private final TimeInterval mInterval;
    private final Length mLength;

    private ExerciseLap(@NonNull TimeInterval interval, @Nullable Length length) {
        Objects.requireNonNull(interval);
        ValidationUtils.requireInRangeIfExists(
                length, Length.fromMeters(0.0), Length.fromMeters(1000000.0), "length");
        mInterval = interval;
        mLength = length;
    }

    /*
     * Returns Length of the lap.
     */
    @Nullable
    public Length getLength() {
        return mLength;
    }

    /*
     * Returns start time of the lap.
     */
    @NonNull
    public Instant getStartTime() {
        return mInterval.getStartTime();
    }

    /*
     * Returns end time of the lap.
     */
    @NonNull
    public Instant getEndTime() {
        return mInterval.getEndTime();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExerciseLap)) return false;
        ExerciseLap that = (ExerciseLap) o;
        return Objects.equals(mInterval, that.mInterval)
                && Objects.equals(getLength(), that.getLength());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mInterval, getLength());
    }

    /** Builder class for {@link ExerciseLap} */
    public static final class Builder {
        private final TimeInterval mInterval;
        private Length mLength;

        public Builder(@NonNull Instant startTime, @NonNull Instant endTime) {
            mInterval = new TimeInterval(startTime, endTime);
        }

        /**
         * Sets the length of this lap
         *
         * @param length Length of the lap, in {@link Length} unit. Optional field. Valid range:
         *     0-1000000 meters.
         */
        @NonNull
        public ExerciseLap.Builder setLength(@NonNull Length length) {
            Objects.requireNonNull(length);
            if (length.getInMeters() < 0 || length.getInMeters() > MAX_LAP_LENGTH_METRES) {
                throw new IllegalArgumentException("Length must be between 0-1000000 metres");
            }
            mLength = length;
            return this;
        }

        /** Builds {@link ExerciseLap} instance. */
        @NonNull
        public ExerciseLap build() {
            return new ExerciseLap(mInterval, mLength);
        }
    }
}
