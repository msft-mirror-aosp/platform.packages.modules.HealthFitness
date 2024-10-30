import fhirspec_pb2
import sys
import os
import json
from fhir_spec_extractor import FhirSpecExtractor, HC_SUPPORTED_RESOURCE_SET
from typing import List, Mapping

if __name__ == '__main__':
    output_file_name = sys.argv[1]
    resource_definitions_file_name = sys.argv[2]

    with open(resource_definitions_file_name, 'r') as f:
        profiles_resources_json = json.load(f)

        fhirSpecExtractor = FhirSpecExtractor(profiles_resources_json, HC_SUPPORTED_RESOURCE_SET)

        r4_resource_spec = fhirSpecExtractor.generate_r4_fhir_spec_proto_message()

    with open(output_file_name, 'wb') as f:
        f.write(r4_resource_spec.SerializeToString())
