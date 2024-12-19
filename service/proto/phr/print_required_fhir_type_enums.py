"""This file is not used in production and is intended to make the maintenance of the proto
definition easier. This will print the required fhir type enums which can be copy and pasted to
the fhirspec.proto file.

Example usage: /main/packages/modules/HealthFitness$
python3 -B service/proto/phr/print_required_fhir_type_enums.py \
../../../external/fhir/spec/r4/json_definitions/profiles-resources.json \
../../../external/fhir/spec/r4/json_definitions/profiles-types.json
"""

import sys
import json
from fhir_spec_utils import *


def get_all_types_from_resource_definitions(profiles_resources_json, resources_set):
    fhir_type_strings = set()

    for resource, structure_definition in extract_type_to_structure_definitions_from_spec(
            profiles_resources_json, resources_set).items():
        for element_definition in extract_element_definitions_from_structure_def(
                structure_definition):
            if element_definition["id"] in [resource, "Observation.component.referenceRange"]:
                continue
            else:
                for data_type in element_definition["type"]:
                    type_code = data_type["code"]
                    if type_code != "http://hl7.org/fhirpath/System.String":
                        fhir_type_strings.add(type_code)

    return fhir_type_strings


def extract_and_print_type_enums(profiles_resources_json, resources_set):
    # Add primitive types that are not yet used manually as they are supported in primitive type
    # validation already
    types = {
        "base64Binary",
        "decimal",
        "markdown",
        "oid",
        "positiveInt",
        "unsignedInt",
        "url",
        "uuid",
    }
    # Add id type manually as this Resource ids use System.String in the spec
    types.add("id")
    types.update(get_all_types_from_resource_definitions(
        profiles_resources_json, resources_set))

    for i, data_type in enumerate(sorted(types, key=str.casefold)):
        print("R4_FHIR_TYPE_" + to_upper_snake_case(data_type) + " = " + str(i + 1) + ";")


if __name__ == '__main__':
    resource_definitions_file_name = sys.argv[1]

    with open(resource_definitions_file_name, 'r') as resources_file:
        resource_definitions = json.load(resources_file)

        extract_and_print_type_enums(resource_definitions, HC_SUPPORTED_RESOURCE_SET)
