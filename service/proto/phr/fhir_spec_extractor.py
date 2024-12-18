import fhirspec_pb2
from typing import Collection, Mapping

# LINT.IfChange(fhir_resource_type_mapping)
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
# LINT.ThenChange(/framework/java/android/health/connect/datatypes/FhirResource.java)

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

            resource_data_type_config = (
                self._generate_fhir_data_type_config_from_element_definitions(element_definitions))

            r4_resource_spec.resource_type_to_config[
                resource_type_int].CopyFrom(resource_data_type_config)

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

    def _generate_fhir_data_type_config_from_element_definitions(
            self, element_definitions: Collection[Mapping]) -> fhirspec_pb2.FhirDataTypeConfig:
        required_fields = set()

        multi_type_configs = []

        field_configs_by_name = {}
        # Manually add resourceType field, as this is not present in the spec
        field_configs_by_name["resourceType"] = fhirspec_pb2.FhirFieldConfig(
            is_array=False,
            r4_type=fhirspec_pb2.R4FhirType.R4_FHIR_TYPE_STRING,
            kind=fhirspec_pb2.Kind.KIND_PRIMITIVE_TYPE
        )

        for element in element_definitions:
            field_id = element["id"]
            if field_id != element["path"]:
                raise ValueError("Expected id and path field to be the same")
            field_parts = field_id.split(".")
            field_parts_length = len(field_parts)

            if field_parts_length == 1:
                # This is the path to the element itself. For example for the Observation resource,
                # There will be an ElementDefinition with id "Observation"
                continue

            elif field_parts_length == 2:
                # This is a "regular" nested field, e.g. Immunization.status, so we extract the
                # field configs
                field_name = field_parts[1]
                field_configs_to_add, multi_type_config = (
                    self._generate_field_configs_and_multi_type_config_from_field_element(
                    element, field_name))
                for name in field_configs_to_add:
                    if name in field_configs_by_name: raise ValueError("Field name already exists")

                field_configs_by_name.update(field_configs_to_add)
                if self.field_name_is_multi_type_field(field_name):
                    multi_type_configs.append(multi_type_config)
                elif self._field_is_required(element):
                    required_fields.add(field_name)

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

        return fhirspec_pb2.FhirDataTypeConfig(
            allowed_field_names_to_config=field_configs_by_name,
            # Sort the list of required fields alphabetically, as the output of this script is part
            # of the build, which needs to be deterministic. The required_fields come from a set,
            # which does not have ordering guarantees.
            required_fields=sorted(required_fields),
            multi_type_fields=multi_type_configs
        )

    def _generate_field_configs_and_multi_type_config_from_field_element(
            self, element_definition, field_name) -> (Mapping[str, fhirspec_pb2.FhirFieldConfig],
                                                      list[fhirspec_pb2.MultiTypeFieldConfig]):
        field_is_array = self._field_is_array(element_definition)

        field_configs_by_name = {}

        multi_type_config = None

        # If the field is a multi type field, it means one of several types can be set. An example
        # is the field Immunization.occurrence, which has types "string" and "dateTime" and
        # therefore means the fields "occurrenceString" and "occurrenceDateTime" are allowed. We
        # therefore expand the field name with each defined type.
        if self.field_name_is_multi_type_field(field_name):
            if field_is_array:
                raise ValueError(
                    "Unexpected cardinality for type choice field. Did not expect array.")

            multi_type_fields = []
            for data_type in element_definition["type"]:
                field_with_type = self._get_multi_type_name_for_type(field_name, data_type["code"])
                type_enum, kind_enum = self._get_type_and_kind_enum_from_type(data_type["code"])
                field_configs_by_name[field_with_type] = fhirspec_pb2.FhirFieldConfig(
                    is_array=False,
                    r4_type=type_enum,
                    kind=kind_enum
                )
                multi_type_fields.append(field_with_type)

            multi_type_config = fhirspec_pb2.MultiTypeFieldConfig(
                name=field_name,
                typed_field_names=multi_type_fields,
                is_required=self._field_is_required(element_definition)
            )

        else:
            if len(element_definition["type"]) != 1:
                raise ValueError("Expected exactly one type")
            type_code = element_definition["type"][0]["code"]
            type_enum, kind_enum = self._get_type_and_kind_enum_from_type(type_code)
            field_configs_by_name[field_name] = fhirspec_pb2.FhirFieldConfig(
                is_array=field_is_array,
                r4_type=type_enum,
                kind=kind_enum
            )

        return field_configs_by_name, multi_type_config

    def field_name_is_multi_type_field(self, field_name) -> bool:
        """Returns true if the field is a oneof / type choice field, which can be contains several
        data types.

        This is the case if the field name ends with "[x]" and means that one of several types can
        be set.
        """

        return field_name.endswith("[x]")

    def _get_multi_type_name_for_type(self, field_name, type_code) -> bool:
        """Returns the one of field name for a specific type.

        For example for the field name "occurrence[x]" and type "dateTime" this will return
        "occurrenceDateTime".
        """

        return field_name[:-3] + type_code[0].upper() + type_code[1:]

    def _field_is_required(self, element_definition) -> bool:
        """Returns true if the field is required

        FHIR fields can have the following cardinalities:
        - 0..1, meaning the field is optional
        - 1..1, meaning the field is required
        - 0..*, meaning the field is an optional array
        - 1..*, meaning the field is a required array
        """

        min = element_definition["min"]

        if min not in [0, 1]:
            raise ValueError("Unexpected min cardinality value: " + min)

        return min

    def _field_is_array(self, element_definition) -> bool:
        """Returns true if the field should be an array

        FHIR fields can have the following cardinalities:
        - 0..1, meaning the field is optional
        - 1..1, meaning the field is required
        - 0..*, meaning the field is an optional array
        - 1..*, meaning the field is a required array
        """

        max = element_definition["max"]

        if max == "1":
            return False
        elif max == "*":
            return True
        else:
            raise ValueError("Unexpected max cardinality value: " + max)

    def _get_type_and_kind_enum_from_type(self, type_code: str):
        # "id" fields usually have a type containing the following type code and extension
        # https://hl7.org/fhir/extensions/StructureDefinition-structuredefinition-fhir-type.html
        if type_code == "http://hl7.org/fhirpath/System.String":
            return (fhirspec_pb2.R4FhirType.R4_FHIR_TYPE_ID,
                    fhirspec_pb2.Kind.KIND_PRIMITIVE_TYPE)

        data_type = fhirspec_pb2.R4FhirType.Value(
            self._convert_type_string_to_enum_string(type_code))
        kind = fhirspec_pb2.Kind.KIND_PRIMITIVE_TYPE \
            if self._is_primitive_type(type_code) else fhirspec_pb2.Kind.KIND_COMPLEX_TYPE

        return data_type, kind

    def _convert_type_string_to_enum_string(self, type_string: str) -> str:
        if not type_string.isalpha():
            raise ValueError("Unexpected characters found in type_string: " + type_string)

        # TODO: b/361775175 - Extract all fhir types individually instead of combining non-primitive
        #  types to COMPLEX enum value.
        if not self._is_primitive_type(type_string):
            return "R4_FHIR_TYPE_COMPLEX"

        snake_case_type_string = type_string[0].upper() + "".join(
            [c if c.islower() else "_" + c for c in type_string[1:]])

        return "R4_FHIR_TYPE_" + snake_case_type_string.upper()

    def _is_primitive_type(self, type_string: str) -> bool:
        # See https://hl7.org/fhir/R4/datatypes.html for possible types.
        # TODO: b/361775175 - Read this from the type definitions file instead of inferring from the
        #  name
        return type_string[0].islower() and type_string != "xhtml"
