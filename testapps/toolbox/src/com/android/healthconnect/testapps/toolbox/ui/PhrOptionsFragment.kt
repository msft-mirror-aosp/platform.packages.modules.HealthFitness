/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.healthconnect.testapps.toolbox.ui

import android.content.Context
import android.health.connect.CreateMedicalDataSourceRequest
import android.health.connect.HealthConnectManager
import android.health.connect.MedicalIdFilter
import android.health.connect.datatypes.MedicalDataSource
import android.health.connect.datatypes.MedicalResource
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.asOutcomeReceiver
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.android.healthconnect.testapps.toolbox.Constants.MEDICAL_PERMISSIONS
import com.android.healthconnect.testapps.toolbox.Constants.READ_IMMUNIZATION
import com.android.healthconnect.testapps.toolbox.R
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils.Companion.requireSystemService
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils.Companion.showMessageDialog
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class PhrOptionsFragment : Fragment(R.layout.fragment_phr_options) {

    private lateinit var mRequestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private val healthConnectManager: HealthConnectManager by lazy {
        requireContext().requireSystemService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Starting API Level 30 If permission is denied more than once, user doesn't see the dialog
        // asking permissions again unless they grant the permission from settings.
        mRequestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                permissionMap: Map<String, Boolean> ->
                requestPermissionResultHandler(permissionMap)
            }
    }

    private fun requestPermissionResultHandler(permissionMap: Map<String, Boolean>) {
        var numberOfPermissionsMissing = MEDICAL_PERMISSIONS.size
        for (value in permissionMap.values) {
            if (value) {
                numberOfPermissionsMissing--
            }
        }

        if (numberOfPermissionsMissing == 0) {
            Toast.makeText(
                    this.requireContext(),
                    R.string.all_medical_permissions_success,
                    Toast.LENGTH_SHORT)
                .show()
        } else {
            Toast.makeText(
                    this.requireContext(),
                    getString(
                        R.string.number_of_medical_permissions_not_granted,
                        numberOfPermissionsMissing),
                    Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.requireViewById<Button>(R.id.phr_create_data_source_button).setOnClickListener {
            executeAndShowMessage {
                createMedicalDataSource("Hospital X", "example.fhir.com/R4/123")
            }
        }

        view.requireViewById<Button>(R.id.phr_insert_immunization_button).setOnClickListener {
            executeAndShowMessage { insertImmunization(view) }
        }

        view.requireViewById<Button>(R.id.phr_read_by_id_button).setOnClickListener {
            executeAndShowMessage { readMedicalResourceForIdFromTextbox(view) }
        }

        view.requireViewById<Button>(R.id.phr_seed_fhir_jsons_button).setOnClickListener {
            loadAllFhirJSONs()
        }

        view.requireViewById<Button>(R.id.phr_request_read_immunization_button).setOnClickListener {
            requestReadImmunizationPermission()
        }

        view
            .requireViewById<Button>(R.id.phr_request_read_and_write_medical_data_button)
            .setOnClickListener { requestMedicalPermissions() }
    }

    private fun executeAndShowMessage(block: suspend () -> String) {
        lifecycleScope.launch {
            val result =
                try {
                    block()
                } catch (e: Exception) {
                    e.toString()
                }

            requireContext().showMessageDialog(result)
        }
    }

    private suspend fun insertImmunization(view: View): String {
        val immunizationResource = loadJSONFromAsset(requireContext(), "immunization_1.json")
        Log.d("INSERT_MEDICAL_RESOURCE", "Writing immunization ${immunizationResource}")
        // TODO(b/343375877) Replace this with call to HC after insert API is implemented
        val insertedResourceId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        view.findViewById<EditText>(R.id.phr_immunization_id_text).setText(insertedResourceId)
        return insertedResourceId
    }

    private suspend fun createMedicalDataSource(displayName: String, fhirBaseUri: String): String {
        val dataSource =
            suspendCancellableCoroutine<MedicalDataSource> { continuation ->
                healthConnectManager.createMedicalDataSource(
                    CreateMedicalDataSourceRequest.Builder(displayName, fhirBaseUri).build(),
                    Runnable::run,
                    continuation.asOutcomeReceiver())
            }
        Log.d("CREATE_MEDICAL_DATA_SOURCE", "Created source: ${dataSource.toString()}")
        return dataSource.toString()
    }

    private suspend fun readMedicalResourceForIdFromTextbox(view: View): String {
        val resourceId =
            view.findViewById<EditText>(R.id.phr_immunization_id_text).getText().toString()
        return readMedicalResourcesById(listOf(resourceId))
            .joinToString(separator = "\n", transform = MedicalResource::toString)
    }

    private suspend fun readMedicalResourcesById(ids: List<String>): List<MedicalResource> {
        Log.d("READ_MEDICAL_RESOURCES", "Reading resource with ids ${ids.toString()}")
        // TODO(b/343455447): Update the fake empty list here to use the real
        // inserted MedicalResourceIds.
        val resources =
            suspendCancellableCoroutine<List<MedicalResource>> { continuation ->
                healthConnectManager.readMedicalResources(
                    listOf(),
                    Runnable::run,
                    continuation.asOutcomeReceiver())
            }
        Log.d("READ_MEDICAL_RESOURCES", "Read ${resources.size} resources")
        return resources
    }

    private fun loadAllFhirJSONs() {
        val jsonFiles = listFhirJSONFiles(requireContext())
        if (jsonFiles == null) {
            Log.e("loadAllFhirJSONs", "No JSON files were found.")
            Toast.makeText(context, "No JSON files were found.", Toast.LENGTH_SHORT).show()
            return
        }

        for (jsonFile in jsonFiles) {
            if (!jsonFile.endsWith(".json")) {
                continue
            }
            val jsonString = loadJSONFromAsset(requireContext(), jsonFile)
            if (jsonString != null) {
                Log.i("loadAllFhirJSONs", "$jsonFile: $jsonString")
            }
        }
        showLoadJSONDataDialog()
    }

    private fun listFhirJSONFiles(context: Context, path: String = ""): List<String>? {
        val assetManager = context.assets
        return try {
            assetManager.list(path)?.toList() ?: emptyList()
        } catch (e: IOException) {
            Log.e("listFhirJSONFiles", "Error listing assets in path $path: $e")
            Toast.makeText(
                    context, "Error listing JSON files: ${e.localizedMessage}", Toast.LENGTH_SHORT)
                .show()
            null
        }
    }

    fun loadJSONFromAsset(context: Context, fileName: String): String? {
        return try {
            val inputStream = context.assets.open(fileName)
            val buffer = ByteArray(inputStream.available())
            inputStream.read(buffer)
            inputStream.close()
            buffer.toString(Charsets.UTF_8)
        } catch (e: IOException) {
            Log.e("loadJSONFromAsset", "Error reading JSON file: $e")
            Toast.makeText(
                    context, "Error reading JSON file: ${e.localizedMessage}", Toast.LENGTH_SHORT)
                .show()
            null
        }
    }

    private fun showLoadJSONDataDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder.setTitle("FHIR JSON files loaded.")
        builder.setPositiveButton(android.R.string.ok) { _, _ -> }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }

    private fun requestReadImmunizationPermission() {
        mRequestPermissionLauncher.launch(arrayOf(READ_IMMUNIZATION))
    }

    private fun requestMedicalPermissions() {
        mRequestPermissionLauncher.launch(MEDICAL_PERMISSIONS)
    }
}
