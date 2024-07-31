package android.health.connect.aidl;

import android.health.connect.MedicalResourceTypeInfoResponse;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link IHealthConnectService#queryAllMedicalResourceTypesInfo}.
 *
 * {@hide}
 */
interface IMedicalResourceTypesInfoResponseCallback {
    // Called on a successful operation
    oneway void onResult(in List<MedicalResourceTypeInfoResponse> responses);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}
