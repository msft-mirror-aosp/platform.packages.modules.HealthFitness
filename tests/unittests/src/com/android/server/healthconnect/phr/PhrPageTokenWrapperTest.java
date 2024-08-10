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

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.ReadMedicalResourcesRequest;

import org.junit.Test;

import java.util.Base64;

public class PhrPageTokenWrapperTest {
    private static final int LAST_ROW_ID = 20;
    private static final String INVALID_PAGE_TOKEN_NON_BASE_64 = "2|3aw";
    private static final String INVALID_PAGE_TOKEN_WRONG_NUMBER_OF_TOKENS = "1,2,3";
    private static final String INVALID_PAGE_TOKEN_WITH_NON_INT_TOKENS = "a,2";

    @Test
    public void phrPageTokenWrapper_createUsingRequestAndLastRowId_success() {
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();

        PhrPageTokenWrapper pageTokenWrapper = PhrPageTokenWrapper.of(request, LAST_ROW_ID);

        assertThat(pageTokenWrapper.getRequest()).isEqualTo(request);
        assertThat(pageTokenWrapper.getLastRowId()).isEqualTo(LAST_ROW_ID);
    }

    @Test
    public void phrPageTokenWrapper_encodeAndDecode_success() {
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        PhrPageTokenWrapper expected = PhrPageTokenWrapper.of(request, LAST_ROW_ID);

        String pageToken = expected.encode();
        PhrPageTokenWrapper result = PhrPageTokenWrapper.from(pageToken);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void phrPageTokenWrapper_fromNonBase64PageToken_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PhrPageTokenWrapper.from(INVALID_PAGE_TOKEN_NON_BASE_64));
    }

    @Test
    public void phrPageTokenWrapper_pageTokenWithWrongNumberOfTokens_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhrPageTokenWrapper.from(
                                encodePageToken(INVALID_PAGE_TOKEN_WRONG_NUMBER_OF_TOKENS)));
    }

    @Test
    public void phrPageTokenWrapper_pageTokenWithNonIntTokens_throws() {
        assertThrows(
                NumberFormatException.class,
                () ->
                        PhrPageTokenWrapper.from(
                                encodePageToken(INVALID_PAGE_TOKEN_WITH_NON_INT_TOKENS)));
    }

    @Test
    public void phrPageTokenWrapper_pageTokenNull_throws() {
        assertThrows(IllegalArgumentException.class, () -> PhrPageTokenWrapper.from(null));
    }

    @Test
    public void phrPageTokenWrapper_pageTokenEmpty_throws() {
        assertThrows(IllegalArgumentException.class, () -> PhrPageTokenWrapper.from(""));
    }

    @Test
    public void phrPageTokenWrapper_fromInvalidRowId_throws() {
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        assertThrows(
                IllegalArgumentException.class,
                () -> PhrPageTokenWrapper.of(request, /* lastRowId= */ -1));
    }

    private String encodePageToken(String nonEncodedPageToken) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(nonEncodedPageToken.getBytes());
    }
}
