import fhirspec_pb2
import sys

# These resource type ints need to match the resource types in FhirResource.java
IMMUNIZATION_RESOURCE_TYPE_INT = 1


def generate_r4_fhir_spec_proto_message() -> fhirspec_pb2.FhirResourceSpec:
    # TODO: b/374937372 - Read from fhir resource spec json files when they are available in third
    # party instead of this placeholder list.
    immunization_fields = [
        "id",
        "resourceType",
        "identifier",
        "status",
        "statusReason",
        "vaccineCode",
        "patient",
        "encounter",
        "occurrenceDateTime",
        "occurrenceString",
        "recorded",
        "primarySource",
        "reportOrigin",
        "location",
        "manufacturer",
        "lotNumber",
        "expirationDate",
        "site",
        "route",
        "doseQuantity",
        "performer",
        "note",
        "reasonCode",
        "reasonReference",
        "isSubPotent",
        "subpotentReason",
        "education",
        "programEligibility",
        "fundingSource",
        "reaction",
        "protocolApplied",
        "fundingSource"
    ]

    immunization_data_type_config = fhirspec_pb2.FhirDataTypeConfig(
        field_names=immunization_fields
    )

    r4_resource_spec = fhirspec_pb2.FhirResourceSpec()
    (r4_resource_spec.resource_type_to_config[IMMUNIZATION_RESOURCE_TYPE_INT]
     .CopyFrom(immunization_data_type_config))

    return r4_resource_spec


if __name__ == '__main__':
    output_file_name = sys.argv[1]

    r4_resource_spec = generate_r4_fhir_spec_proto_message()

    with open(output_file_name, 'wb') as f:
        f.write(r4_resource_spec.SerializeToString())
