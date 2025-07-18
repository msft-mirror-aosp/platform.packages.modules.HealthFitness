import unittest
import json
import fhirspec_pb2
from fhir_spec_extractor import FhirSpecExtractor
from google.protobuf import text_format


class FhirSpecExtractorTest(unittest.TestCase):
    BUNDLE_WITH_IMMUNIZATION_AND_PATIENT_STRUCTURE_DEFINITION = json.loads("""
    {
        "resourceType" : "Bundle",
        "id" : "resources",
        "meta" : {
          "lastUpdated" : "2019-11-01T09:29:23.356+11:00"
        },
        "type" : "collection",
        "entry" : [
          {
            "fullUrl" : "http://hl7.org/fhir/CompartmentDefinition/relatedPerson"
          },
          {
            "fullUrl" : "http://hl7.org/fhir/StructureDefinition/Immunization",
            "resource" : {
              "resourceType" : "StructureDefinition",
              "id" : "Immunization",
              "meta" : {
                "lastUpdated" : "2019-11-01T09:29:23.356+11:00"
              },
              "fhirVersion" : "4.0.1",
              "kind" : "resource",
              "type" : "Immunization",
              "baseDefinition" : "http://hl7.org/fhir/StructureDefinition/DomainResource",
              "snapshot" : {
                "element" : [{
                  "id" : "Immunization",
                  "path" : "Immunization",
                  "min" : 0,
                  "max" : "*",
                  "base" : {
                    "path" : "Immunization",
                    "min" : 0,
                    "max" : "*"
                  }
                },
                {
                  "id" : "Immunization.id",
                  "path" : "Immunization.id",
                  "min" : 0,
                  "max" : "1",
                  "base" : {
                    "path" : "Resource.id",
                    "min" : 0,
                    "max" : "1"
                  },
                  "type" : [{
                    "extension" : [{
                      "url" : "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type",
                      "valueUrl" : "string"
                    }],
                    "code" : "http://hl7.org/fhirpath/System.String"
                  }]
                },
                {
                  "id" : "Immunization.status",
                  "path" : "Immunization.status",
                  "min" : 1,
                  "max" : "1",
                  "base" : {
                    "path" : "Immunization.status",
                    "min" : 1,
                    "max" : "1"
                  },
                  "type" : [{
                    "code" : "code"
                  }]
                },
                {
                  "id" : "Immunization.vaccineCode",
                  "path" : "Immunization.vaccineCode",
                  "min" : 1,
                  "max" : "1",
                  "base" : {
                    "path" : "Immunization.vaccineCode",
                    "min" : 1,
                    "max" : "1"
                  },
                  "type" : [{
                    "code" : "CodeableConcept"
                  }]
                },
                {
                  "id" : "Immunization.exampleFieldToTestOneToMany",
                  "path" : "Immunization.exampleFieldToTestOneToMany",
                  "min" : 1,
                  "max" : "*",
                  "base" : {
                    "path" : "Immunization.exampleFieldToTestOneToMany",
                    "min" : 1,
                    "max" : "*"
                  },
                  "type" : [{
                    "code" : "CodeableConcept"
                  }]
                },
                {
                  "id" : "Immunization.occurrence[x]",
                  "path" : "Immunization.occurrence[x]",
                  "min" : 1,
                  "max" : "1",
                  "base" : {
                    "path" : "Immunization.occurrence[x]",
                    "min" : 1,
                    "max" : "1"
                  },
                  "type" : [{
                    "code" : "dateTime"
                  },
                  {
                    "code" : "string"
                  }]
                },
                {
                  "id" : "Immunization.performer",
                  "path" : "Immunization.performer",
                  "min" : 0,
                  "max" : "*",
                  "base" : {
                    "path" : "Immunization.performer",
                    "min" : 0,
                    "max" : "*"
                  },
                  "type" : [{
                    "code" : "BackboneElement"
                  }]
                },
                {
                  "id" : "Immunization.performer.id",
                  "path" : "Immunization.performer.id",
                  "min" : 0,
                  "max" : "1",
                  "base" : {
                    "path" : "Element.id",
                    "min" : 0,
                    "max" : "1"
                  },
                  "type" : [{
                    "extension" : [{
                      "url" : "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type",
                      "valueUrl" : "string"
                    }],
                    "code" : "http://hl7.org/fhirpath/System.String"
                  }]
                },
                {
                  "id" : "Immunization.performer.extension",
                  "path" : "Immunization.performer.extension",
                  "min" : 0,
                  "max" : "*",
                  "base" : {
                    "path" : "Element.extension",
                    "min" : 0,
                    "max" : "*"
                  },
                  "type" : [{
                    "code" : "Extension"
                  }]
                }
              ]
              }
            }
          },
          {
            "fullUrl" : "http://hl7.org/fhir/StructureDefinition/Patient",
            "resource" : {
              "resourceType" : "StructureDefinition",
              "id" : "Patient",
              "meta" : {
                "lastUpdated" : "2019-11-01T09:29:23.356+11:00"
              },
              "url" : "http://hl7.org/fhir/StructureDefinition/Patient",
              "fhirVersion" : "4.0.1",
              "kind" : "resource",
              "type" : "Patient",
              "snapshot" : {
                "element" : [{
                  "id" : "Patient.id",
                  "path" : "Patient.id",
                  "min" : 0,
                  "max" : "1",
                  "base" : {
                    "path" : "Resource.id",
                    "min" : 0,
                    "max" : "1"
                  },
                  "type" : [{
                    "extension" : [{
                      "url" : "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type",
                      "valueUrl" : "string"
                    }],
                    "code" : "http://hl7.org/fhirpath/System.String"
                  }]
                }]
              }
            }
          }
        ]
    }
    """)

    IMMUNIZATION_RESOURCE_TYPE_INT = 1

    PATIENT_RESOURCE_TYPE_INT = 9

    def test_fhir_spec_extractor_immunization_resource_produces_expected(self):
        fhir_spec_extractor = FhirSpecExtractor(
            self.BUNDLE_WITH_IMMUNIZATION_AND_PATIENT_STRUCTURE_DEFINITION,
            {"Immunization"})
        # we expect each top level field to be present, and one ofs that are represented in the spec
        # as e.g. occurrence[x] should be expanded into the individual fields such as
        # occurrenceDateTime and occurrenceString. Fields with a cardinality of 0..* or 1..* should
        # have is_array = true set in their config.
        expected_required_fields = {"status", "vaccineCode", "exampleFieldToTestOneToMany"}
        expected_multi_type_config = fhirspec_pb2.MultiTypeFieldConfig(
            name="occurrence[x]",
            typed_field_names=["occurrenceDateTime", "occurrenceString"],
            is_required=True
        )
        expected_field_names_to_config = {
            "resourceType": fhirspec_pb2.FhirFieldConfig(
                is_array=False,
                r4_type=fhirspec_pb2.R4FhirType.R4_FHIR_TYPE_STRING,
                kind=fhirspec_pb2.Kind.KIND_PRIMITIVE_TYPE
            ),
            "id": fhirspec_pb2.FhirFieldConfig(
                is_array=False,
                r4_type=fhirspec_pb2.R4FhirType.R4_FHIR_TYPE_SYSTEM_STRING,
                kind=fhirspec_pb2.Kind.KIND_PRIMITIVE_TYPE
            ),
            "status": fhirspec_pb2.FhirFieldConfig(
                is_array=False,
                r4_type=fhirspec_pb2.R4FhirType.R4_FHIR_TYPE_CODE,
                kind=fhirspec_pb2.Kind.KIND_PRIMITIVE_TYPE
            ),
            "vaccineCode": fhirspec_pb2.FhirFieldConfig(
                is_array=False,
                r4_type=fhirspec_pb2.R4FhirType.R4_FHIR_TYPE_COMPLEX,
                kind=fhirspec_pb2.Kind.KIND_COMPLEX_TYPE
            ),
            "exampleFieldToTestOneToMany": fhirspec_pb2.FhirFieldConfig(
                is_array=True,
                r4_type=fhirspec_pb2.R4FhirType.R4_FHIR_TYPE_COMPLEX,
                kind=fhirspec_pb2.Kind.KIND_COMPLEX_TYPE
            ),
            "occurrenceDateTime": fhirspec_pb2.FhirFieldConfig(
                is_array=False,
                r4_type=fhirspec_pb2.R4FhirType.R4_FHIR_TYPE_DATE_TIME,
                kind=fhirspec_pb2.Kind.KIND_PRIMITIVE_TYPE
            ),
            "occurrenceString": fhirspec_pb2.FhirFieldConfig(
                is_array=False,
                r4_type=fhirspec_pb2.R4FhirType.R4_FHIR_TYPE_STRING,
                kind=fhirspec_pb2.Kind.KIND_PRIMITIVE_TYPE
            ),
            "performer": fhirspec_pb2.FhirFieldConfig(
                is_array=True,
                r4_type=fhirspec_pb2.R4FhirType.R4_FHIR_TYPE_COMPLEX,
                kind=fhirspec_pb2.Kind.KIND_COMPLEX_TYPE
            ),
        }

        generated_spec = fhir_spec_extractor.generate_r4_fhir_spec_proto_message()

        # Check that exactly one Immunization config is present
        self.assertEqual(len(generated_spec.resource_type_to_config.keys()), 1)
        immunization_config = (
            generated_spec.resource_type_to_config[self.IMMUNIZATION_RESOURCE_TYPE_INT])
        # Check that the list of required fields is as expected
        self.assertEqual(set(immunization_config.required_fields), expected_required_fields)
        # Check that the list of multi type configs is as expected
        self.assertEqual(len(immunization_config.multi_type_fields), 1)
        received_multi_type_config = immunization_config.multi_type_fields[0]
        self.assertEqual(received_multi_type_config.name, expected_multi_type_config.name)
        self.assertEqual(received_multi_type_config.typed_field_names,
                         expected_multi_type_config.typed_field_names)
        self.assertEqual(received_multi_type_config.is_required,
                         expected_multi_type_config.is_required)
        # Check that the field names to config map is as expected
        self.assertEqual(set(expected_field_names_to_config.keys()),
                         set(immunization_config.allowed_field_names_to_config.keys()))
        for expected_field, expected_config in expected_field_names_to_config.items():
            self.assertEqual(
                immunization_config.allowed_field_names_to_config[expected_field],
                expected_config,
                "Mismatching config for field " + expected_field
            )

    def test_fhir_spec_extractor_immunization_and_patient_contains_two_entries(self):
        fhir_spec_extractor = FhirSpecExtractor(
            self.BUNDLE_WITH_IMMUNIZATION_AND_PATIENT_STRUCTURE_DEFINITION,
            {"Immunization", "Patient"})

        generated_spec = fhir_spec_extractor.generate_r4_fhir_spec_proto_message()

        self.assertEqual(len(generated_spec.resource_type_to_config), 2)

    def test_fhir_spec_extractor_unsupported_resource_raises_exception(self):
        with self.assertRaises(ValueError):
            FhirSpecExtractor(
                self.BUNDLE_WITH_IMMUNIZATION_AND_PATIENT_STRUCTURE_DEFINITION,
                {"UnsupportedResource"})

    def test_fhir_spec_extractor_missing_resource_raises_exception(self):
        with self.assertRaises(ValueError):
            FhirSpecExtractor(
                self.BUNDLE_WITH_IMMUNIZATION_AND_PATIENT_STRUCTURE_DEFINITION,
                {"Observation"})


if __name__ == '__main__':
    unittest.main()
