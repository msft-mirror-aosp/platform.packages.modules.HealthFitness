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

package android.healthconnect.cts.phr.utils;

/**
 * A helper class that supports making FHIR Immunization data for tests.
 *
 * <p>The Default result will be a valid FHIR Immunization, but that is all that should be relied
 * upon. Anything else that is relied upon by a test should be set by one of the methods.
 */
public final class ImmunizationBuilder extends FhirResourceBuilder<ImmunizationBuilder> {
    private static final String DEFAULT_JSON =
            "{"
                    + "  \"resourceType\": \"Immunization\","
                    + "  \"id\": \"immunization-1\","
                    + "  \"status\": \"completed\","
                    + "  \"vaccineCode\": {"
                    + "    \"coding\": ["
                    + "      {"
                    + "        \"system\": \"http://hl7.org/fhir/sid/cvx\","
                    + "        \"code\": \"115\""
                    + "      },"
                    + "      {"
                    + "        \"system\": \"http://hl7.org/fhir/sid/ndc\","
                    + "        \"code\": \"58160-842-11\""
                    + "      }"
                    + "    ],"
                    + "    \"text\": \"Tdap\""
                    + "  },"
                    + "  \"patient\": {"
                    + "    \"reference\": \"Patient/patient_1\","
                    + "    \"display\": \"Example, Anne\""
                    + "  },"
                    + "  \"encounter\": {"
                    + "    \"reference\": \"Encounter/encounter_unk\","
                    + "    \"display\": \"GP Visit\""
                    + "  },"
                    + "  \"occurrenceDateTime\": \"2018-05-21\","
                    + "  \"primarySource\": true,"
                    + "  \"manufacturer\": {"
                    + "    \"display\": \"Sanofi Pasteur\""
                    + "  },"
                    + "  \"lotNumber\": \"1\","
                    + "  \"site\": {"
                    + "    \"coding\": ["
                    + "      {"
                    + "        \"system\": \"http://terminology.hl7.org/CodeSystem/v3-ActSite\","
                    + "        \"code\": \"LA\","
                    + "        \"display\": \"Left Arm\""
                    + "      }"
                    + "    ],"
                    + "    \"text\": \"Left Arm\""
                    + "  },"
                    + "  \"route\": {"
                    + "    \"coding\": ["
                    + "      {"
                    + "        \"system\":"
                    + "\"http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration\","
                    + "        \"code\": \"IM\","
                    + "        \"display\": \"Injection, intramuscular\""
                    + "      }"
                    + "    ],"
                    + "    \"text\": \"Injection, intramuscular\""
                    + "  },"
                    + "  \"doseQuantity\": {"
                    + "    \"value\": 0.5,"
                    + "    \"unit\": \"mL\""
                    + "  },"
                    + "  \"performer\": ["
                    + "    {"
                    + "      \"function\": {"
                    + "        \"coding\": ["
                    + "          {"
                    + "            \"system\": \"http://terminology.hl7.org/CodeSystem/v2-0443\","
                    + "            \"code\": \"AP\","
                    + "            \"display\": \"Administering Provider\""
                    + "          }"
                    + "        ],"
                    + "        \"text\": \"Administering Provider\""
                    + "      },"
                    + "      \"actor\": {"
                    + "        \"reference\": \"Practitioner/practitioner_1\","
                    + "        \"type\": \"Practitioner\","
                    + "        \"display\": \"Dr Maria Hernandez\""
                    + "      }"
                    + "    }"
                    + "  ]"
                    + "}";

    /**
     * Creates a default valid FHIR Immunization.
     *
     * <p>All that should be relied on is that the Immunization is valid. To rely on anything else
     * set it with the other methods.
     */
    public ImmunizationBuilder() {
        super(DEFAULT_JSON);
    }

    @Override
    protected ImmunizationBuilder returnThis() {
        return this;
    }
}
