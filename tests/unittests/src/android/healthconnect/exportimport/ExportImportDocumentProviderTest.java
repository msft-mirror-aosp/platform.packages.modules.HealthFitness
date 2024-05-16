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

package android.healthconnect.exportimport;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.exportimport.ExportImportDocumentProvider;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ExportImportDocumentProviderTest {
    private static final String TEST_TITLE = "Document Provider";
    private static final String TEST_SUMMARY = "user@documentprovider.com";
    private static final int TEST_ICON_RESOURCE_ID = 1178;
    private static final Uri TEST_ROOT_URI = Uri.parse("root://documentprovider.com/");

    @Test
    public void testDeserailize() {
        ExportImportDocumentProvider documentProvider =
                new ExportImportDocumentProvider(
                        TEST_TITLE, TEST_SUMMARY, TEST_ICON_RESOURCE_ID, TEST_ROOT_URI);

        Parcel documentProviderParcel = writeToParcel(documentProvider);
        documentProviderParcel.setDataPosition(0);
        ExportImportDocumentProvider deserializedDocumentProvider =
                documentProviderParcel.readTypedObject(ExportImportDocumentProvider.CREATOR);

        assertThat(deserializedDocumentProvider.getTitle()).isEqualTo(TEST_TITLE);
        assertThat(deserializedDocumentProvider.getSummary()).isEqualTo(TEST_SUMMARY);
        assertThat(deserializedDocumentProvider.getIconResource()).isEqualTo(TEST_ICON_RESOURCE_ID);
        assertThat(deserializedDocumentProvider.getRootUri()).isEqualTo(TEST_ROOT_URI);
    }

    private static Parcel writeToParcel(ExportImportDocumentProvider documentProvider) {
        Parcel documentProviderParcel = Parcel.obtain();
        documentProviderParcel.writeTypedObject(documentProvider, 0);
        return documentProviderParcel;
    }
}
