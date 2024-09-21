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

import org.json.JSONException;
import org.json.JSONObject;

public final class ProcedureBuilder {

    private static final String DEFAULT_JSON =
            "{"
                    + "  \"resourceType\" : \"Procedure\","
                    + "  \"id\" : \"f001\","
                    + "  \"status\" : \"completed\","
                    + "  \"code\" : {"
                    + "    \"coding\" : [{"
                    + "      \"system\" : \"http://snomed.info/sct\","
                    + "      \"code\" : \"34068001\","
                    + "      \"display\" : \"Heart valve replacement\""
                    + "    }]"
                    + "  },"
                    + "  \"subject\" : {"
                    + "    \"reference\" : \"Patient/f001\","
                    + "    \"display\" : \"A. Lincoln\""
                    + "  },"
                    + "  \"encounter\" : {"
                    + "    \"reference\" : \"Encounter/f001\""
                    + "  },"
                    + "  \"occurrencePeriod\" : {"
                    + "    \"start\" : \"2011-06-26\","
                    + "    \"end\" : \"2011-06-27\""
                    + "  },"
                    + "  \"performer\" : [{"
                    + "    \"function\" : {"
                    + "      \"coding\" : [{"
                    + "        \"system\" : \"urn:oid:2.16.840.1.113883.2.4.15.111\","
                    + "        \"code\" : \"01.000\","
                    + "        \"display\" : \"Arts\""
                    + "      }],"
                    + "      \"text\" : \"Care role\""
                    + "    },"
                    + "    \"actor\" : {"
                    + "      \"reference\" : \"Practitioner/f002\","
                    + "      \"display\" : \"G. Washington\""
                    + "    }"
                    + "  }],"
                    + "  \"reason\" : [{"
                    + "    \"concept\" : {"
                    + "      \"text\" : \"Heart valve disorder\""
                    + "    }"
                    + "  }],"
                    + "  \"bodySite\" : [{"
                    + "    \"coding\" : [{"
                    + "      \"system\" : \"http://snomed.info/sct\","
                    + "      \"code\" : \"17401000\","
                    + "      \"display\" : \"Heart valve structure\""
                    + "    }]"
                    + "  }],"
                    + "  \"outcome\" : {"
                    + "    \"text\" : \"improved blood circulation\""
                    + "  },"
                    + "  \"report\" : [{"
                    + "    \"reference\" : \"DiagnosticReport/f001\","
                    + "    \"display\" : \"Lab results blood test\""
                    + "  }],"
                    + "  \"followUp\" : [{"
                    + "    \"text\" : \"described in care plan\""
                    + "  }]"
                    + "}";
    private final JSONObject mFhir;

    /**
     * Creates a default valid FHIR Observation.
     *
     * <p>All that should be relied on is that the Observation is valid. To rely on anything else
     * set it with the other methods.
     */
    public ProcedureBuilder() {
        try {
            this.mFhir = new JSONObject(DEFAULT_JSON);
        } catch (JSONException e) {
            // Should never happen, but JSONException is declared, and is a checked exception.
            throw new IllegalStateException(e);
        }
    }

    /**
     * Set the FHIR id.
     *
     * @return this Builder.
     */
    public ProcedureBuilder setId(String id) {
        try {
            mFhir.put("id", id);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return this;
    }

    /** Returns the current state of this builder as a JSON FHIR string. */
    public String toJson() {
        try {
            return mFhir.toString(/* indentSpaces= */ 2);
        } catch (JSONException e) {
            // Should never happen, but JSONException is declared, and is a checked exception.
            throw new IllegalStateException(e);
        }
    }
}
