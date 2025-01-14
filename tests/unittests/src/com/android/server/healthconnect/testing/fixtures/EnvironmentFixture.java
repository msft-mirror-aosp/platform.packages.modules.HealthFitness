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

package com.android.server.healthconnect.testing.fixtures;

import static org.mockito.Mockito.when;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.testing.StaticMockFixture;

import java.io.File;

/**
 * A fixture for use with {@link com.android.modules.utils.testing.ExtendedMockitoRule} that mocks
 * {@link Environment#getDataDirectory()} to return an empty directory that is deleted at the end of
 * the test.
 */
public class EnvironmentFixture implements StaticMockFixture {

    private static final String TAG = "EnvironmentFixture";

    private final String mName;
    private final int mFileMode;
    private File mDataDirectory;

    public EnvironmentFixture() {
        this("test_data", Context.MODE_PRIVATE);
    }

    public EnvironmentFixture(String name, int fileMode) {
        mName = name;
        mFileMode = fileMode;
    }

    @Override
    public StaticMockitoSessionBuilder setUpMockedClasses(
            StaticMockitoSessionBuilder sessionBuilder) {
        return sessionBuilder.mockStatic(Environment.class);
    }

    @Override
    public void setUpMockBehaviors() {
        Context context = ApplicationProvider.getApplicationContext();
        mDataDirectory = context.getDir(mName, mFileMode);
        // Ensure directory doesn't contain anything left over from previous tests.
        for (File file : mDataDirectory.listFiles()) {
            delete(file);
        }
        when(Environment.getDataDirectory()).thenReturn(mDataDirectory);
    }

    @Override
    public void tearDown() {
        delete(mDataDirectory);
    }

    public File getDataDirectory() {
        return requireNonNull(mDataDirectory);
    }

    private void delete(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                delete(f);
            }
        }
        if (!file.delete()) {
            throw new IllegalStateException("Failed to delete " + file.getName());
        }
        Log.v(TAG, "Deleted " + file.getName());
    }
}
