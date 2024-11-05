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
 * A helper class that supports making <a href="https://www.hl7.org/fhir/patient.html">FHIR
 * Patient</a> data for tests.
 *
 * <p>The Default result will be a valid FHIR Patient, but that is all that should be relied upon.
 * Anything else that is relied upon by a test should be set by one of the methods.
 */
public final class PatientBuilder extends FhirResourceBuilder<PatientBuilder> {
    private static final String DEFAULT_JSON =
            "{"
                    + "  \"resourceType\": \"Patient\","
                    + "  \"id\": \"example\","
                    + "  \"identifier\": ["
                    + "    {"
                    + "      \"use\": \"usual\","
                    + "      \"type\": {"
                    + "        \"coding\": ["
                    + "          {"
                    + "            \"system\": \"http://terminology.hl7.org/CodeSystem/v2-0203\","
                    + "            \"code\": \"MR\""
                    + "          }"
                    + "        ]"
                    + "      },"
                    + "      \"system\": \"urn:oid:1.2.36.146.595.217.0.1\","
                    + "      \"value\": \"12345\","
                    + "      \"period\": {"
                    + "        \"start\": \"2001-05-06\""
                    + "      },"
                    + "      \"assigner\": {"
                    + "        \"display\": \"Acme Healthcare\""
                    + "      }"
                    + "    }"
                    + "  ],"
                    + "  \"active\": true,"
                    + "  \"name\": ["
                    + "    {"
                    + "      \"use\": \"official\","
                    + "      \"family\": \"Chalmers\","
                    + "      \"given\": ["
                    + "        \"Peter\","
                    + "        \"James\""
                    + "      ]"
                    + "    },"
                    + "    {"
                    + "      \"use\": \"usual\","
                    + "      \"given\": ["
                    + "        \"Jim\""
                    + "      ]"
                    + "    },"
                    + "    {"
                    + "      \"use\": \"maiden\","
                    + "      \"family\": \"Windsor\","
                    + "      \"given\": ["
                    + "        \"Peter\","
                    + "        \"James\""
                    + "      ],"
                    + "      \"period\": {"
                    + "        \"end\": \"2002\""
                    + "      }"
                    + "    }"
                    + "  ],"
                    + "  \"telecom\": ["
                    + "    {"
                    + "      \"use\": \"home\""
                    + "    },"
                    + "    {"
                    + "      \"system\": \"phone\","
                    + "      \"value\": \"(03) 5555 6473\","
                    + "      \"use\": \"work\","
                    + "      \"rank\": 1"
                    + "    },"
                    + "    {"
                    + "      \"system\": \"phone\","
                    + "      \"value\": \"(03) 3410 5613\","
                    + "      \"use\": \"mobile\","
                    + "      \"rank\": 2"
                    + "    },"
                    + "    {"
                    + "      \"system\": \"phone\","
                    + "      \"value\": \"(03) 5555 8834\","
                    + "      \"use\": \"old\","
                    + "      \"period\": {"
                    + "        \"end\": \"2014\""
                    + "      }"
                    + "    }"
                    + "  ],"
                    + "  \"gender\": \"male\","
                    + "  \"birthDate\": \"1974-12-25\","
                    + "  \"_birthDate\": {"
                    + "    \"extension\": ["
                    + "      {"
                    + "        \"url\":"
                    + "\"http://hl7.org/fhir/StructureDefinition/patient-birthTime\","
                    + "        \"valueDateTime\": \"1974-12-25T14:35:45-05:00\""
                    + "      }"
                    + "    ]"
                    + "  },"
                    + "  \"deceasedBoolean\": false,"
                    + "  \"address\": ["
                    + "    {"
                    + "      \"use\": \"home\","
                    + "      \"type\": \"both\","
                    + "      \"text\": \"534 Erewhon St PeasantVille, Rainbow, Vic  3999\","
                    + "      \"line\": ["
                    + "        \"534 Erewhon St\""
                    + "      ],"
                    + "      \"city\": \"PleasantVille\","
                    + "      \"district\": \"Rainbow\","
                    + "      \"state\": \"Vic\","
                    + "      \"postalCode\": \"3999\","
                    + "      \"period\": {"
                    + "        \"start\": \"1974-12-25\""
                    + "      }"
                    + "    }"
                    + "  ],"
                    + "  \"contact\": ["
                    + "    {"
                    + "      \"relationship\": ["
                    + "        {"
                    + "          \"coding\": ["
                    + "            {"
                    + "              \"system\": \"http://terminology.hl7.org/CodeSystem/v2-0131\","
                    + "              \"code\": \"N\""
                    + "            }"
                    + "          ]"
                    + "        }"
                    + "      ],"
                    + "      \"name\": {"
                    + "        \"family\": \"du Marché\","
                    + "        \"_family\": {"
                    + "          \"extension\": ["
                    + "            {"
                    + "              \"url\":"
                    + "\"http://hl7.org/fhir/StructureDefinition/humanname-own-prefix\","
                    + "              \"valueString\": \"VV\""
                    + "            }"
                    + "          ]"
                    + "        },"
                    + "        \"given\": ["
                    + "          \"Bénédicte\""
                    + "        ]"
                    + "      },"
                    + "      \"telecom\": ["
                    + "        {"
                    + "          \"system\": \"phone\","
                    + "          \"value\": \"+33 (237) 998327\""
                    + "        }"
                    + "      ],"
                    + "      \"address\": {"
                    + "        \"use\": \"home\","
                    + "        \"type\": \"both\","
                    + "        \"line\": ["
                    + "          \"534 Erewhon St\""
                    + "        ],"
                    + "        \"city\": \"PleasantVille\","
                    + "        \"district\": \"Rainbow\","
                    + "        \"state\": \"Vic\","
                    + "        \"postalCode\": \"3999\","
                    + "        \"period\": {"
                    + "          \"start\": \"1974-12-25\""
                    + "        }"
                    + "      },"
                    + "      \"gender\": \"female\","
                    + "      \"period\": {"
                    + "        \"start\": \"2012\""
                    + "      }"
                    + "    }"
                    + "  ],"
                    + "  \"managingOrganization\": {"
                    + "    \"reference\": \"Organization/1\""
                    + "  }"
                    + "}";

    /**
     * Creates a default valid FHIR Patient.
     *
     * <p>All that should be relied on is that the Patient is valid. To rely on anything else set it
     * with the other methods.
     */
    public PatientBuilder() {
        super(DEFAULT_JSON);
    }

    @Override
    protected PatientBuilder returnThis() {
        return this;
    }
}
