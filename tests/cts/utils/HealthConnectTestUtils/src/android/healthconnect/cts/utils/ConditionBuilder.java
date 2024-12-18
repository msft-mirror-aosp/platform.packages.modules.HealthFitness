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

/**
 * A helper class that supports making FHIR Condition data for tests.
 *
 * <p>The Default result will be a valid FHIR Condition, but that is all that should be relied upon.
 * Anything else that is relied upon by a test should be set by one of the methods.
 */
public class ConditionBuilder {
    private static final String DEFAULT_JSON =
            "{  \"resourceType\" : \"Condition\",  \"id\" : \"f201\",  \"identifier\" : [{   "
                + " \"value\" : \"12345\"  }],  \"clinicalStatus\" : {    \"coding\" : [{     "
                + " \"system\" : \"http://terminology.hl7.org/CodeSystem/condition-clinical\",     "
                + " \"code\" : \"resolved\"    }]  },  \"verificationStatus\" : {    \"coding\" :"
                + " [{      \"system\" :"
                + " \"http://terminology.hl7.org/CodeSystem/condition-ver-status\",      \"code\" :"
                + " \"confirmed\"    }]  },  \"category\" : [{    \"coding\" : [{      \"system\" :"
                + " \"http://snomed.info/sct\",      \"code\" : \"55607006\",      \"display\" :"
                + " \"Problem\"    },    {      \"system\" :"
                + " \"http://terminology.hl7.org/CodeSystem/condition-category\",      \"code\" :"
                + " \"problem-list-item\"    }]  }],  \"severity\" : {    \"coding\" : [{     "
                + " \"system\" : \"http://snomed.info/sct\",      \"code\" : \"255604002\",     "
                + " \"display\" : \"Mild\"    }]  },  \"code\" : {    \"coding\" : [{     "
                + " \"system\" : \"http://snomed.info/sct\",      \"code\" : \"386661006\",     "
                + " \"display\" : \"Fever\"    }]  },  \"bodySite\" : [{    \"coding\" : [{     "
                + " \"system\" : \"http://snomed.info/sct\",      \"code\" : \"38266002\",     "
                + " \"display\" : \"Entire body as a whole\"    }]  }],  \"subject\" : {   "
                + " \"reference\" : \"Patient/f201\",    \"display\" : \"Roel\"  },  \"encounter\""
                + " : {    \"reference\" : \"Encounter/f201\"  },  \"onsetDateTime\" :"
                + " \"2013-04-02\",  \"abatementString\" : \"around April 9, 2013\", "
                + " \"recordedDate\" : \"2013-04-04\",  \"participant\" : [{    \"function\" : {   "
                + "   \"coding\" : [{        \"system\" :"
                + " \"http://terminology.hl7.org/CodeSystem/provenance-participant-type\",       "
                + " \"code\" : \"enterer\",        \"display\" : \"Enterer\"      }]    },   "
                + " \"actor\" : {      \"reference\" : \"Practitioner/f201\"    }  },  {   "
                + " \"function\" : {      \"coding\" : [{        \"system\" :"
                + " \"http://terminology.hl7.org/CodeSystem/provenance-participant-type\",       "
                + " \"code\" : \"verifier\",        \"display\" : \"Verifier\"      }]    },   "
                + " \"actor\" : {      \"reference\" : \"Practitioner/f201\"    }  }], "
                + " \"evidence\" : [{    \"concept\" : {      \"coding\" : [{        \"system\" :"
                + " \"http://snomed.info/sct\",        \"code\" : \"258710007\",        \"display\""
                + " : \"degrees C\"      }]    },    \"reference\" : {      \"reference\" :"
                + " \"Observation/f202\",      \"display\" : \"Temperature\"    }  }]}";
    private final JSONObject mFhir;

    /**
     * Creates a default valid FHIR Observation.
     *
     * <p>All that should be relied on is that the Observation is valid. To rely on anything else
     * set it with the other methods.
     */
    public ConditionBuilder() {
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
    public ConditionBuilder setId(String id) {
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
