import unittest
import fhirspec_pb2
import generate_fhir_spec


class GenerateFhirSpecTest(unittest.TestCase):

    def test_generate_spec_contains_immunization_fields(self):
        r4_resource_spec: fhirspec_pb2.FhirResourceSpec = (
            generate_fhir_spec.generate_r4_fhir_spec_proto_message())

        self.assertTrue(generate_fhir_spec.IMMUNIZATION_RESOURCE_TYPE_INT
                        in r4_resource_spec.resource_type_to_config)
        self.assertTrue(r4_resource_spec.resource_type_to_config[
                            generate_fhir_spec.IMMUNIZATION_RESOURCE_TYPE_INT].field_names)


if __name__ == '__main__':
    unittest.main()
