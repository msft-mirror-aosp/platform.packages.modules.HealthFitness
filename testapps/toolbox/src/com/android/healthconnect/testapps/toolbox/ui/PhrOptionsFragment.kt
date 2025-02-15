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
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.ReadMedicalResourcesInitialRequest
import android.health.connect.ReadMedicalResourcesResponse
import android.health.connect.UpsertMedicalResourceRequest
import android.health.connect.datatypes.FhirVersion
import android.health.connect.datatypes.MedicalDataSource
import android.health.connect.datatypes.MedicalResource
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_VACCINES
import android.net.Uri
import android.os.Bundle
import android.os.OutcomeReceiver
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.asOutcomeReceiver
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.android.healthconnect.testapps.toolbox.Constants.MEDICAL_PERMISSIONS
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
                    Toast.LENGTH_SHORT,
                )
                .show()
        } else {
            Toast.makeText(
                    this.requireContext(),
                    getString(
                        R.string.number_of_medical_permissions_not_granted,
                        numberOfPermissionsMissing,
                    ),
                    Toast.LENGTH_SHORT,
                )
                .show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.requireViewById<Button>(R.id.phr_create_data_source_button).setOnClickListener {
            executeAndShowMessage {
                createMedicalDataSource(
                    view,
                    Uri.parse("https://example.fhir.com/R4/123"),
                    "My Hospital " + (0..1000).random(),
                    FhirVersion.parseFhirVersion("4.0.1"),
                )
            }
        }

        view.requireViewById<Button>(R.id.phr_read_by_id_button).setOnClickListener {
            executeAndShowMessage { readImmunization(view) }
        }

        view.requireViewById<Button>(R.id.phr_seed_fhir_jsons_button).setOnClickListener {
            executeAndShowMessage { insertAllFhirResources(view) }
        }

        view.requireViewById<Button>(R.id.phr_insert_resource_button).setOnClickListener {
            executeAndShowMessage { insertPastedResource(view) }
        }

        view
            .requireViewById<Button>(R.id.phr_request_read_and_write_medical_data_button)
            .setOnClickListener { requestMedicalPermissions() }

        setUpFhirResourceFromSpinner(view)
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

    private suspend fun insertPastedResource(view: View): String {
        val pastedResource =
            view.findViewById<EditText>(R.id.phr_pasted_resource_text).getText().toString()
        Log.d("INSERT_RESOURCE", "Writing resource $pastedResource")
        return insertResource(view, pastedResource)
    }

    private fun setUpFhirResourceFromSpinner(rootView: View) {
        val jsonFiles = getJsonFilesFromAssets(requireContext())
        val spinnerOptions = listOf(getString(R.string.spinner_default_message)) + jsonFiles
        val spinnerAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val spinner = rootView.findViewById<Spinner>(R.id.phr_spinner)
        spinner.adapter = spinnerAdapter

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View,
                    position: Int,
                    id: Long,
                ) {
                    if (position == 0) { // Ignore "Select resource" default message
                        return
                    }

                    val selectedFile = spinnerOptions[position]
                    val selectedResource =
                        loadJSONFromAsset(requireContext(), selectedFile) ?: return

                    rootView.findViewById<EditText>(R.id.phr_pasted_resource_text).setText(
                        selectedResource)
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // No-op.
                }
            }
    }

    private suspend fun insertResource(view: View, resource: String): String {
        val insertedDataSourceId =
            view.findViewById<EditText>(R.id.phr_data_source_id_text).getText().toString()
        val insertedResources =
            upsertMedicalResources(
                listOf(
                    UpsertMedicalResourceRequest.Builder(
                            insertedDataSourceId,
                            FhirVersion.parseFhirVersion("4.0.1"),
                            resource,
                        )
                        .build()
                )
            )
        return insertedResources.joinToString(
            separator = "\n",
            transform = MedicalResource::toString,
        )
    }

    private suspend fun insertAllFhirResources(view: View): String {
        val allResources = loadAllFhirJSONs()
        Log.d("INSERT_ALL", "Writing all FHIR resources")
        val insertedDataSourceId =
            view.findViewById<EditText>(R.id.phr_data_source_id_text).getText().toString()
        val insertedResources =
            upsertMedicalResources(
                allResources.map {
                    UpsertMedicalResourceRequest.Builder(
                            insertedDataSourceId,
                            FhirVersion.parseFhirVersion("4.0.1"),
                            it,
                        )
                        .build()
                }
            )
        return "SUCCESSFUL DATA UPSERT \n\nUpserted data:\n" +
            insertedResources.joinToString(separator = "\n", transform = MedicalResource::toString)
    }

    private fun getJsonFilesFromAssets(context: Context): List<String> {
        return context.assets.list("")?.filter { it.endsWith(".json") } ?: emptyList()
    }

    private suspend fun upsertMedicalResources(
        requests: List<UpsertMedicalResourceRequest>
    ): List<MedicalResource> {
        Log.d("UPSERT_RESOURCES", "Writing ${requests.size} resources")
        val resources =
            suspendCancellableCoroutine<List<MedicalResource>> { continuation ->
                healthConnectManager.upsertMedicalResources(
                    requests,
                    Runnable::run,
                    continuation.asOutcomeReceiver(),
                )
            }
        Log.d("UPSERT_RESOURCES", "Wrote ${resources.size} resources")
        return resources
    }

    private suspend fun createMedicalDataSource(
        view: View,
        fhirBaseUri: Uri,
        displayName: String,
        fhirVersion: FhirVersion,
    ): String {
        val dataSource =
            suspendCancellableCoroutine<MedicalDataSource> { continuation ->
                healthConnectManager.createMedicalDataSource(
                    CreateMedicalDataSourceRequest.Builder(fhirBaseUri, displayName, fhirVersion)
                        .build(),
                    Runnable::run,
                    continuation.asOutcomeReceiver(),
                )
            }
        view.findViewById<EditText>(R.id.phr_data_source_id_text).setText(dataSource.id)
        Log.d("CREATE_DATA_SOURCE", "Created source: $dataSource")
        return "Created data source: $displayName"
    }

    private suspend fun readImmunization(view: View): String {
        return readImmunization()
            .joinToString(separator = "\n", transform = MedicalResource::toString)
    }

    private suspend fun readImmunization(): List<MedicalResource> {

        var receiver: OutcomeReceiver<ReadMedicalResourcesResponse, HealthConnectException>
        val request: ReadMedicalResourcesInitialRequest =
            ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_VACCINES).build()
        val resources =
            suspendCancellableCoroutine<ReadMedicalResourcesResponse> { continuation ->
                    receiver = continuation.asOutcomeReceiver()
                    healthConnectManager.readMedicalResources(request, Runnable::run, receiver)
                }
                .medicalResources
        Log.d("READ_MEDICAL_RESOURCES", "Read ${resources.size} resources")
        return resources
    }

    private fun loadAllFhirJSONs(): List<String> {
        val jsonFiles = listFhirJSONFiles(requireContext())
        if (jsonFiles == null) {
            Log.e("loadAllFhirJSONs", "No JSON files were found.")
            Toast.makeText(context, "No JSON files were found.", Toast.LENGTH_SHORT).show()
            return emptyList()
        }

        return jsonFiles
            .filter { it.endsWith(".json") }
            .mapNotNull {
                val jsonString = loadJSONFromAsset(requireContext(), it)
                Log.i("loadAllFhirJSONs", "$it: $jsonString")
                jsonString
            }
    }

    private fun listFhirJSONFiles(context: Context, path: String = ""): List<String>? {
        val assetManager = context.assets
        return try {
            assetManager.list(path)?.toList() ?: emptyList()
        } catch (e: IOException) {
            Log.e("listFhirJSONFiles", "Error listing assets in path $path: $e")
            Toast.makeText(
                    context,
                    "Error listing JSON files: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT,
                )
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
                    context,
                    "Error reading JSON file: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT,
                )
                .show()
            null
        }
    }

    private fun requestMedicalPermissions() {
        mRequestPermissionLauncher.launch(MEDICAL_PERMISSIONS)
    }
}
