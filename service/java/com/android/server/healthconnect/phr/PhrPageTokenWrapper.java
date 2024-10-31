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

import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.datatypes.MedicalDataSource.validateMedicalDataSourceIds;
import static android.health.connect.datatypes.MedicalResource.validateMedicalResourceType;

import static java.util.Objects.hash;
import static java.util.stream.Collectors.toSet;

import android.health.connect.ReadMedicalResourcesInitialRequest;
import android.health.connect.aidl.ReadMedicalResourcesRequestParcel;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

/**
 * Wrapper class for generating a PHR pageToken.
 *
 * @hide
 */
public class PhrPageTokenWrapper {
    private static final String DELIMITER = ",";
    private static final String INNER_DELIMITER = ";";
    // We currently encode mLastRowId, mRequest.getMedicalResourceType(), and
    // mRequest.getDataSourceIds(). As we add more filters and need to update the encoding logic, we
    // need to update this as well.
    private static final int NUM_OF_ENCODED_FIELDS = 3;
    // These are the indices at which we store and retrieve each field used for creating the
    // pageToken string.
    private static final int LAST_ROW_ID_INDEX = 0;
    private static final int MEDICAL_RESOURCE_TYPE_INDEX = 1;
    private static final int MEDICAL_DATA_SOURCE_IDS_INDEX = 2;

    private final ReadMedicalResourcesInitialRequest mRequest;
    private long mLastRowId = DEFAULT_LONG;

    /**
     * Creates a {@link PhrPageTokenWrapper} from the given {@link
     * ReadMedicalResourcesRequestParcel}.
     *
     * <p>If {@code pageToken} in the request is {@code null}, the default {@code mLastRowId} is
     * {@link DEFAULT_LONG}, meaning it's the initial request.
     *
     * @throws IllegalArgumentException if {@code pageToken} is empty, or not in valid Base64
     *     scheme; or if the decoded {@code lastRowId} is negative; or if the decoded {@code
     *     medicalResourceType} is not supported; or there are invalid IDs in the decoded {@code
     *     dataSourceId}s.
     * @throws NumberFormatException if the decoded {@code pageToken} does not contain a parsable
     *     integer.
     */
    public static PhrPageTokenWrapper from(ReadMedicalResourcesRequestParcel request) {
        if (request.getPageToken() == null) {
            // We create a new request and only populate the read filters. The pageSize will be
            // unset as we don't use it for encoding/decoding.
            ReadMedicalResourcesInitialRequest requestWithFiltersOnly =
                    new ReadMedicalResourcesInitialRequest.Builder(request.getMedicalResourceType())
                            .addDataSourceIds(request.getDataSourceIds())
                            .build();
            return new PhrPageTokenWrapper(requestWithFiltersOnly);
        }
        return from(request.getPageToken());
    }

    /** Creates a {@link PhrPageTokenWrapper} from the given {@code pageToken}. */
    @VisibleForTesting
    static PhrPageTokenWrapper from(String pageToken) {
        if (pageToken == null || pageToken.isEmpty()) {
            throw new IllegalArgumentException("pageToken can not be empty");
        }

        Base64.Decoder decoder = Base64.getDecoder();
        String decodedPageToken = new String(decoder.decode(pageToken));
        String[] pageTokenSplit = decodedPageToken.split(DELIMITER, /* limit= */ -1);

        if (pageTokenSplit.length != NUM_OF_ENCODED_FIELDS) {
            throw new IllegalArgumentException("Invalid pageToken");
        }

        int lastRowId = Integer.parseInt(pageTokenSplit[LAST_ROW_ID_INDEX]);
        if (lastRowId < 0) {
            throw new IllegalArgumentException("Invalid pageToken");
        }

        int medicalResourceType = Integer.parseInt(pageTokenSplit[MEDICAL_RESOURCE_TYPE_INDEX]);
        try {
            validateMedicalResourceType(medicalResourceType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid pageToken");
        }

        // We create a new request and only populate the read filters. The pageSize will be unset as
        // we don't use it for encoding/decoding.
        ReadMedicalResourcesInitialRequest.Builder requestWithFiltersOnly =
                new ReadMedicalResourcesInitialRequest.Builder(medicalResourceType);

        String medicalDataSourceIdsString = pageTokenSplit[MEDICAL_DATA_SOURCE_IDS_INDEX];
        if (medicalDataSourceIdsString.isEmpty()) {
            return new PhrPageTokenWrapper(requestWithFiltersOnly.build(), lastRowId);
        }

        Set<String> medicalDataSourceIds =
                Arrays.stream(medicalDataSourceIdsString.split(INNER_DELIMITER)).collect(toSet());
        try {
            validateMedicalDataSourceIds(medicalDataSourceIds);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid pageToken");
        }

        return new PhrPageTokenWrapper(
                requestWithFiltersOnly.addDataSourceIds(medicalDataSourceIds).build(), lastRowId);
    }

    /**
     * Returns a pageToken string encoded from this {@link PhrPageTokenWrapper}.
     *
     * @throws IllegalStateException if {@code mLastRowId} is negative.
     */
    public String encode() {
        if (mLastRowId < 0) {
            throw new IllegalStateException("cannot encode when mLastRowId is negative");
        }
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(toReadableTokenString().getBytes());
    }

    /**
     * Converts this token to a readable string which will be used in {@link
     * PhrPageTokenWrapper#encode()}.
     */
    private String toReadableTokenString() {
        return String.join(
                DELIMITER,
                String.valueOf(mLastRowId),
                String.valueOf(mRequest.getMedicalResourceType()),
                String.join(INNER_DELIMITER, mRequest.getDataSourceIds()));
    }

    /** Creates a String representation of this {@link PhrPageTokenWrapper}. */
    public String toString() {
        return toReadableTokenString();
    }

    /**
     * Returns the last read row_id for the current {@code mRequest}. Default is {@link
     * DEFAULT_LONG} for the initial request.
     */
    public long getLastRowId() {
        return mLastRowId;
    }

    /**
     * Sets the last row id.
     *
     * @throws IllegalStateException if {@code lastRowId} is negative.
     */
    public PhrPageTokenWrapper cloneWithNewLastRowId(long lastRowId) {
        if (lastRowId < 0) {
            throw new IllegalStateException("cannot set mLastRowId to negative");
        }
        return new PhrPageTokenWrapper(mRequest, lastRowId);
    }

    /** Returns the initial request from which the {@link PhrPageTokenWrapper} is created from. */
    public ReadMedicalResourcesInitialRequest getRequest() {
        return mRequest;
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhrPageTokenWrapper that)) return false;
        return mLastRowId == that.mLastRowId && mRequest.equals(that.mRequest);
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getLastRowId(), getRequest());
    }

    private PhrPageTokenWrapper(ReadMedicalResourcesInitialRequest request) {
        this.mRequest = request;
    }

    private PhrPageTokenWrapper(ReadMedicalResourcesInitialRequest request, long lastRowId) {
        this.mRequest = request;
        this.mLastRowId = lastRowId;
    }
}
