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

package healthconnect.storage.utils;

import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceId;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceType;

import static com.android.server.healthconnect.storage.utils.StorageUtils.UUID_BYTE_SIZE;
import static com.android.server.healthconnect.storage.utils.StorageUtils.bytesToUuids;
import static com.android.server.healthconnect.storage.utils.StorageUtils.generateMedicalResourceUUID;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getSingleByteArray;

import static com.google.common.truth.Truth.assertThat;

import org.json.JSONException;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

public class StorageUtilsTest {
    @Test
    public void uuidToBytesAndBack_emptyList() {
        byte[] bytes = getSingleByteArray(List.of());
        assertThat(bytes.length).isEqualTo(0);
        assertThat(bytesToUuids(bytes)).isEmpty();
    }

    @Test
    public void uuidToBytesAndBack_oneUuid() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        byte[] bytes = getSingleByteArray(List.of(uuid1, uuid2));
        assertThat(bytes.length).isEqualTo(UUID_BYTE_SIZE * 2);
        assertThat(bytesToUuids(bytes)).containsExactly(uuid1, uuid2);
    }

    @Test
    public void generateMedicalResourceUuid_correctResult() throws JSONException {
        byte[] resourceIdBytes = getFhirResourceId(FHIR_DATA_IMMUNIZATION).getBytes();
        byte[] resourceTypeBytes = getFhirResourceType(FHIR_DATA_IMMUNIZATION).getBytes();
        byte[] dataSourceIdBytes = DATA_SOURCE_ID.getBytes();
        byte[] bytes =
                ByteBuffer.allocate(
                                resourceIdBytes.length
                                        + resourceTypeBytes.length
                                        + dataSourceIdBytes.length)
                        .put(resourceIdBytes)
                        .put(resourceTypeBytes)
                        .put(dataSourceIdBytes)
                        .array();
        UUID expected = UUID.nameUUIDFromBytes(bytes);

        UUID result =
                generateMedicalResourceUUID(
                        getFhirResourceId(FHIR_DATA_IMMUNIZATION),
                        getFhirResourceType(FHIR_DATA_IMMUNIZATION),
                        DATA_SOURCE_ID);

        assertThat(result).isEqualTo(expected);
    }
}
