import fhirspec_pb2
from typing import Collection, Mapping

RESOURCE_TYPE_STRING_TO_HC_INT_MAPPING = {
    "Immunization": 1,
    "AllergyIntolerance": 2,
    "Observation": 3,
    "Condition": 4,
    "Procedure": 5,
    "Medication": 6,
    "MedicationRequest": 7,
    "MedicationStatement": 8,
    "Patient": 9,
    "Practitioner": 10,
    "PractitionerRole": 11,
    "Encounter": 12,
    "Location": 13,
    "Organization": 14,
}

HC_SUPPORTED_RESOURCE_SET = set(RESOURCE_TYPE_STRING_TO_HC_INT_MAPPING.keys())

FHIR_VERSION_R4 = "4.0.1"


class FhirSpecExtractor:
    """Extractor for getting information for HC FHIR validation from official FHIR spec json files.

    Typical usage example:
        extractor = new FhirSpecExtractor(profile_resources_json, {"Immunization", "Observation"})
        fhir_spec_message = extractor.generate_r4_fhir_spec_proto_message()
    """

    def __init__(self, profile_resources_json: Mapping, resource_names: set[str]):
        """Extracts StructureDefinitions for the requested resources from the provided fhir spec.

        Args:
            profile_resources_json: The contents of the profile-resources.json fhir spec file, which
            is in the structure of a https://hl7.org/fhir/Bundle.html, parsed to dict. The
            Bundle.entry will contain the list of https://hl7.org/fhir/StructureDefinition.html that
            we are interested in.
            resource_names: The set of FHIR resources to extract FHIR spec information for.

        Raises:
            ValueError: If a requested resource is not present in the spec, if it's not supported by
            Health Connect, or if any spec values are not as expected.
        """
        if not resource_names.issubset(HC_SUPPORTED_RESOURCE_SET):
            raise ValueError("Provided resource set was not a subset of supported resources")

        # A mapping from the resource name to the list of field definitions, which are in the
        # structure of https://hl7.org/fhir/ElementDefinition.html
        self._resource_to_element_definitions = (
            self._extract_element_definitions_by_resource_from_spec(
                profile_resources_json, resource_names))

    def generate_r4_fhir_spec_proto_message(self) -> fhirspec_pb2.FhirResourceSpec:
        """Generates a FhirResourceSpec message from the fhir json spec.

        Returns:
            The FhirResourceSpec message, with an entry for each requested resource.
        """
        # TODO: b/360091651 - Extract additional information such as field types, cardinality and
        #  structure of each type. Note that the field "Observation.component.referenceRange" will
        #  need special handling. It doesn't have a type, but a contentReference to
        #  "Observation.referenceRange" and should use that type's structure.

        r4_resource_spec = fhirspec_pb2.FhirResourceSpec()

        for resource, element_definitions in self._resource_to_element_definitions.items():
            resource_type_int = RESOURCE_TYPE_STRING_TO_HC_INT_MAPPING[resource]
            allowed_fields = self._extract_allowed_fields_from_element_definitions(
                element_definitions)

            resource_data_type_config = fhirspec_pb2.FhirDataTypeConfig(
                field_names=allowed_fields
            )
            (r4_resource_spec.resource_type_to_config[resource_type_int]
             .CopyFrom(resource_data_type_config))

        return r4_resource_spec

    def _extract_element_definitions_by_resource_from_spec(
            self, profile_resources_json: Mapping, resource_names: set[str]) -> Mapping:
        resource_to_element_definitions = {}
        # For each StructureDefinition that matches a resource in resource_names, we extract
        # the list of ElementDefinitions. Each ElementDefinition contains the spec for a path /
        # field of the resource.
        for entry in profile_resources_json["entry"]:
            fullUrl = entry["fullUrl"]
            if not (fullUrl.startswith("http://hl7.org/fhir/StructureDefinition/") and
                    fullUrl.split("/")[-1] in resource_names):
                continue

            resource_name = fullUrl.split("/")[-1]
            resource_structure_definition = entry["resource"]

            # Do some assertions on expected values
            if resource_structure_definition["fhirVersion"] != FHIR_VERSION_R4:
                raise ValueError("Unexpected fhir version found")
            if resource_structure_definition["kind"] != "resource":
                raise ValueError("Unexpected kind field in structure definition")
            if resource_structure_definition["type"] != resource_name:
                raise ValueError("Unexpected resource type in structure definition")

            # We select the list of elements in "snapshot" (as opposed to "differential"), as we
            # want the full definition of fields, including fields from any base definitions.
            resource_to_element_definitions[resource_name] = (
                resource_structure_definition)["snapshot"]["element"]

        if set(resource_to_element_definitions.keys()) != resource_names:
            raise ValueError("Did not find resource definitions for all requested resources.")

        return resource_to_element_definitions

    def _extract_allowed_fields_from_element_definitions(
            self, element_definitions: Collection[Mapping]) -> set[str]:
        allowed_fields = set()
        # Manually add resourceType field, as this is not present in the spec
        allowed_fields.add("resourceType")

        for element in element_definitions:
            field_id = element["id"]
            field_parts = field_id.split(".")
            field_parts_length = len(field_parts)

            if field_parts_length == 1:
                # This is the path to the element itself. For example for the Observation resource,
                # There will be an ElementDefinition with id "Observation"
                continue

            elif field_parts_length == 2:
                # This is a "regular" nested field, e.g. Immunization.status, so we add the second
                # part, "status" to allowed fields
                field_name = field_parts[1]

                # If the field name ends with "[x]" this means that this is a oneof field, where
                # one of several types can be set. An example is the field Immunization.occurrence,
                # which has types "string" and "dateTime" and therefore means the fields
                # "occurrenceString" and "occurrenceDateTime" are allowed.
                # We therefore expand the field name with each defined type.
                if field_name.endswith("[x]"):
                    for type in element["type"]:
                        type_code = type["code"]
                        field_with_type = field_name[:-3] + type_code[0].upper() + type_code[1:]
                        allowed_fields.add(field_with_type)

                else:
                    allowed_fields.add(field_name)

            elif field_parts_length > 2:
                # This means the field is part of a BackBoneElement. For an example see the
                # https://hl7.org/fhir/Immunization.html "reaction" field.
                # BackBoneElements need to be handled separately, as those fields don't have a type
                # defined, but have the BackBoneElement definition instead.
                # Note that the following field contains a double backbone element, which we need to
                # consider: "MedicationRequest.dispenseRequest.initialFill",

                # For now we are just recording the top level allowed field, which has its own
                # element definition, so is covered by the "elif field_parts_length == 2" above
                continue

            else:
                raise ValueError("This should not happen")

        return allowed_fields
