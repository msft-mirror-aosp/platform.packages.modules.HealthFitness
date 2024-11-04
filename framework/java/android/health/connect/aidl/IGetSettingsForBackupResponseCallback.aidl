package android.health.connect.aidl;

import android.health.connect.backuprestore.GetSettingsForBackupResponse;
import android.health.connect.aidl.HealthConnectExceptionParcel;

/**
 * Callback for {@link HealthConnectManager#getSettingsForBackup}
 * {@hide}
 */
interface IGetSettingsForBackupResponseCallback {
    // Called on a successful operation
    oneway void onResult(in GetSettingsForBackupResponse parcel);
    // Called when an error is hit
    oneway void onError(in HealthConnectExceptionParcel exception);
}
