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

package com.android.server.healthconnect.phr;

import static java.util.Objects.hash;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.health.connect.ReadMedicalResourcesRequest;

import java.util.Base64;
import java.util.Objects;

/**
 * Wrapper class for generating a PHR pageToken.
 *
 * @hide
 */
public class PhrPageTokenWrapper {
    private static final String DELIMITER = ",";
    // We currently encode mLastRowId and mRequest.getMedicalResourceType().
    // As we add more filters and need to update the encoding logic, we need to update this as well.
    private static final int NUM_OF_ENCODED_FIELDS = 2;
    // These are the indices at which we store and retrieve each field used for creating the
    // pageToken string.
    private static final int LAST_ROW_ID_INDEX = 0;
    private static final int MEDICAL_RESOURCE_TYPE_INDEX = 1;

    private final ReadMedicalResourcesRequest mRequest;
    private final long mLastRowId;

    /**
     * Creates a {@link PhrPageTokenWrapper} from the given {@link ReadMedicalResourcesRequest} and
     * {@code lastRowId}.
     */
    public static PhrPageTokenWrapper of(
            @NonNull ReadMedicalResourcesRequest request, long lastRowId) {
        if (lastRowId < 0) {
            throw new IllegalArgumentException("lastRowId can not be negative");
        }
        // We create a new request and only populate the read filters. Other fields such as
        // pageSize and pageToken will be unset as we don't use them for encoding/decoding.
        ReadMedicalResourcesRequest requestWithFiltersOnly =
                new ReadMedicalResourcesRequest.Builder(request.getMedicalResourceType()).build();
        return new PhrPageTokenWrapper(requestWithFiltersOnly, lastRowId);
    }

    /**
     * Creates a {@link PhrPageTokenWrapper} from the given {@code pageToken}.
     *
     * @throws IllegalArgumentException if {@code pageToken} is not in valid Base64 scheme or if the
     *     {@code pageToken} is null or empty.
     * @throws NumberFormatException if the decoded {@code pageToken} does not contain a parsable
     *     integer.
     */
    public static PhrPageTokenWrapper from(@NonNull String pageToken) {
        if (pageToken == null || pageToken.isEmpty()) {
            throw new IllegalArgumentException("pageToken can not be null or empty");
        }

        Base64.Decoder decoder = Base64.getDecoder();
        String decodedPageToken = new String(decoder.decode(pageToken));
        String[] pageTokenSplit = decodedPageToken.split(DELIMITER);

        if (pageTokenSplit.length != NUM_OF_ENCODED_FIELDS) {
            throw new IllegalArgumentException("Invalid pageToken");
        }

        int lastRowId = Integer.parseInt(pageTokenSplit[LAST_ROW_ID_INDEX]);
        int medicalResourceType = Integer.parseInt(pageTokenSplit[MEDICAL_RESOURCE_TYPE_INDEX]);
        return of(new ReadMedicalResourcesRequest.Builder(medicalResourceType).build(), lastRowId);
    }

    /** Returns a pageToken string encoded from this {@link PhrPageTokenWrapper}. */
    @Nullable
    public String encode() {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(toReadableTokenString().getBytes());
    }

    /**
     * Converts this token to a readable string which will be used in {@link
     * PhrPageTokenWrapper#encode()}.
     */
    @NonNull
    private String toReadableTokenString() {
        return String.join(
                DELIMITER,
                String.valueOf(mLastRowId),
                String.valueOf(mRequest.getMedicalResourceType()));
    }

    /** Creates a String representation of this {@link PhrPageTokenWrapper}. */
    @NonNull
    public String toString() {
        return toReadableTokenString();
    }

    /** Returns the last read row_id for the current {@code mRequest}. */
    public long getLastRowId() {
        return mLastRowId;
    }

    /** Returns the request from which the {@link PhrPageTokenWrapper} is created from. */
    public ReadMedicalResourcesRequest getRequest() {
        return mRequest;
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhrPageTokenWrapper that)) return false;
        return mLastRowId == that.mLastRowId && Objects.equals(mRequest, that.mRequest);
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getLastRowId(), getRequest());
    }

    private PhrPageTokenWrapper(@NonNull ReadMedicalResourcesRequest request, long lastRowId) {
        this.mLastRowId = lastRowId;
        this.mRequest = request;
    }
}
