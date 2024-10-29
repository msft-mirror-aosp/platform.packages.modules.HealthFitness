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
 * Test helper class for making practitioner related FHIR data, including <a
 * href="https://www.hl7.org/fhir/practitioner.html">Practitioner</a> and <a
 * href="https://www.hl7.org/fhir/practitionerrole.html">PractitionerRole</a> - see {@link #role()}.
 */
public final class PractitionerBuilder extends FhirResourceBuilder<PractitionerBuilder> {

    private static final String DEFAULT_PRACTITIONER_JSON =
            "{\n"
                    + "  \"resourceType\": \"Practitioner\",\n"
                    + "  \"id\": \"f001\",\n"
                    + "  \"identifier\": [\n"
                    + "    {\n"
                    + "      \"use\": \"official\",\n"
                    + "      \"system\": \"urn:oid:2.16.528.1.1007.3.1\",\n"
                    + "      \"value\": \"938273695\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"use\": \"usual\",\n"
                    + "      \"system\": \"urn:oid:2.16.840.1.113883.2.4.6.3\",\n"
                    + "      \"value\": \"129IDH4OP733\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"name\": [\n"
                    + "    {\n"
                    + "      \"use\": \"official\",\n"
                    + "      \"family\": \"van den broek\",\n"
                    + "      \"given\": [\n"
                    + "        \"Eric\"\n"
                    + "      ],\n"
                    + "      \"suffix\": [\n"
                    + "        \"MD\"\n"
                    + "      ]\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"telecom\": [\n"
                    + "    {\n"
                    + "      \"system\": \"phone\",\n"
                    + "      \"value\": \"0205568263\",\n"
                    + "      \"use\": \"work\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"system\": \"email\",\n"
                    + "      \"value\": \"E.M.vandenbroek@bmc.nl\",\n"
                    + "      \"use\": \"work\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"system\": \"fax\",\n"
                    + "      \"value\": \"0205664440\",\n"
                    + "      \"use\": \"work\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"address\": [\n"
                    + "    {\n"
                    + "      \"use\": \"work\",\n"
                    + "      \"line\": [\n"
                    + "        \"Galapagosweg 91\"\n"
                    + "      ],\n"
                    + "      \"city\": \"Den Burg\",\n"
                    + "      \"postalCode\": \"9105 PZ\",\n"
                    + "      \"country\": \"NLD\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"gender\": \"male\",\n"
                    + "  \"birthDate\": \"1975-12-07\"\n"
                    + "}";
    private static final String DEFAULT_PRACTITIONER_ROLE_JSON =
            "{\n"
                    + "  \"resourceType\": \"PractitionerRole\",\n"
                    + "  \"id\": \"example\",\n"
                    + "  \"identifier\": [\n"
                    + "    {\n"
                    + "      \"system\": \"http://www.acme.org/practitioners\",\n"
                    + "      \"value\": \"23\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"active\": true,\n"
                    + "  \"period\": {\n"
                    + "    \"start\": \"2012-01-01\",\n"
                    + "    \"end\": \"2012-03-31\"\n"
                    + "  },\n"
                    + "  \"practitioner\": {\n"
                    + "    \"reference\": \"Practitioner/example\",\n"
                    + "    \"display\": \"Dr Adam Careful\"\n"
                    + "  },\n"
                    + "  \"organization\": {\n"
                    + "    \"reference\": \"Organization/f001\"\n"
                    + "  },\n"
                    + "  \"code\": [\n"
                    + "    {\n"
                    + "      \"coding\": [\n"
                    + "        {\n"
                    + "          \"system\": \"http://terminology.hl7.org/CodeSystem/v2-0286\",\n"
                    + "          \"code\": \"RP\"\n"
                    + "        }\n"
                    + "      ]\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"specialty\": [\n"
                    + "    {\n"
                    + "      \"coding\": [\n"
                    + "        {\n"
                    + "          \"system\": \"http://snomed.info/sct\",\n"
                    + "          \"code\": \"408443003\",\n"
                    + "          \"display\": \"General medical practice\"\n"
                    + "        }\n"
                    + "      ]\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"location\": [\n"
                    + "    {\n"
                    + "      \"reference\": \"Location/1\",\n"
                    + "      \"display\": \"South Wing, second floor\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"healthcareService\": [\n"
                    + "    {\n"
                    + "      \"reference\": \"HealthcareService/example\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"telecom\": [\n"
                    + "    {\n"
                    + "      \"system\": \"phone\",\n"
                    + "      \"value\": \"(03) 5555 6473\",\n"
                    + "      \"use\": \"work\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"system\": \"email\",\n"
                    + "      \"value\": \"adam.southern@example.org\",\n"
                    + "      \"use\": \"work\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"availableTime\": [\n"
                    + "    {\n"
                    + "      \"daysOfWeek\": [\n"
                    + "        \"mon\",\n"
                    + "        \"tue\",\n"
                    + "        \"wed\"\n"
                    + "      ],\n"
                    + "      \"availableStartTime\": \"09:00:00\",\n"
                    + "      \"availableEndTime\": \"16:30:00\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"daysOfWeek\": [\n"
                    + "        \"thu\",\n"
                    + "        \"fri\"\n"
                    + "      ],\n"
                    + "      \"availableStartTime\": \"09:00:00\",\n"
                    + "      \"availableEndTime\": \"12:00:00\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"notAvailable\": [\n"
                    + "    {\n"
                    + "      \"description\": \"Adam will be on extended leave during May 2017\",\n"
                    + "      \"during\": {\n"
                    + "        \"start\": \"2017-05-01\",\n"
                    + "        \"end\": \"2017-05-20\"\n"
                    + "      }\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"availabilityExceptions\": "
                    + "\"Adam is generally unavailable on public holidays and during the "
                    + "Christmas/New Year break\",\n"
                    + "  \"endpoint\": [\n"
                    + "    {\n"
                    + "      \"reference\": \"Endpoint/example\"\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}";

    public static class PractitionerRoleBuilder
            extends FhirResourceBuilder<PractitionerRoleBuilder> {

        private PractitionerRoleBuilder() {
            super(DEFAULT_PRACTITIONER_ROLE_JSON);
        }

        @Override
        protected PractitionerRoleBuilder returnThis() {
            return this;
        }
    }

    /**
     * Returns a helper class that can build FHIR test data for <a
     * href="https://www.hl7.org/fhir/practitionerrole.html">PractitionerRole</a> resources.
     */
    public static PractitionerRoleBuilder role() {
        return new PractitionerRoleBuilder();
    }

    public PractitionerBuilder() {
        super(DEFAULT_PRACTITIONER_JSON);
    }

    @Override
    protected PractitionerBuilder returnThis() {
        return this;
    }
}
