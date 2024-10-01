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
package com.android.healthfitness.flags;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A {@link TestRule} that should be used together with {@link
 * android.platform.test.flag.junit.SetFlagsRule} when writing unit tests in which the test subject
 * calls to {@link AconfigFlagHelper}.
 *
 * <p>This rule must have a smaller value for {@link Rule#order() order} than {@link
 * android.platform.test.flag.junit.SetFlagsRule}'s so this rule is executed first.
 */
public final class AconfigFlagHelperTestRule implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // Clearing out the map so its cache will be reinitialized with new values set by
                // @EnableFlags and @DisableFlags. See b/370447278#comment2
                AconfigFlagHelper.DB_VERSION_TO_DB_FLAG_MAP.clear();

                base.evaluate();
            }
        };
    }
}
