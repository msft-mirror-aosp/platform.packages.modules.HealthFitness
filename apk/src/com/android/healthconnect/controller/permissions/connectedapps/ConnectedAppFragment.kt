package com.android.healthconnect.controller.permissions.connectedapps

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreference
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionStrings
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import dagger.hilt.android.AndroidEntryPoint

/** Fragment for connected app screen. */
@AndroidEntryPoint(PreferenceFragmentCompat::class)
class ConnectedAppFragment : Hilt_ConnectedAppFragment() {

    companion object {
        private const val READ_CATEGORY = "read_permission_category"
        private const val WRITE_CATEGORY = "write_permission_category"
        @JvmStatic
        fun newInstance(packageName: String) =
            ConnectedAppFragment().apply { mPackageName = packageName }
    }

    private var mPackageName: String = ""
    private val viewModel: AppPermissionViewModel by viewModels()

    private val mReadPermissionCategory: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(READ_CATEGORY)
    }

    private val mWritePermissionCategory: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(WRITE_CATEGORY)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.connected_app_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (mPackageName.isEmpty()) {
            mPackageName = arguments?.get("packageName") as String
        }
        viewModel.loadForPackage(mPackageName)
        viewModel.grantedPermissions.observe(viewLifecycleOwner) { permissions ->
            updatePermissions(permissions)
        }
    }

    private fun updatePermissions(permissions: List<HealthPermission>) {
        mReadPermissionCategory?.removeAll()
        mWritePermissionCategory?.removeAll()

        permissions.forEach { permission ->
            if (permission.permissionsAccessType.equals(PermissionsAccessType.READ)) {
                mReadPermissionCategory?.addPreference(
                    SwitchPreference(requireContext()).also {
                        it.setTitle(
                            HealthPermissionStrings.fromPermissionType(
                                    permission.healthPermissionType)
                                .label)
                    })
            }
        }
        permissions.forEach { permission ->
            if (permission.permissionsAccessType.equals(PermissionsAccessType.WRITE)) {
                mWritePermissionCategory?.addPreference(
                    SwitchPreference(requireContext()).also {
                        it.setTitle(
                            HealthPermissionStrings.fromPermissionType(
                                    permission.healthPermissionType)
                                .label)
                    })
            }
        }
    }
}