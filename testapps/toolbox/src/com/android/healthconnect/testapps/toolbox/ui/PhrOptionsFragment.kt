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
import android.health.connect.HealthConnectManager
import android.health.connect.MedicalIdFilter
import android.health.connect.datatypes.MedicalResource
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.asOutcomeReceiver
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.android.healthconnect.testapps.toolbox.R
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils.Companion.requireSystemService
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils.Companion.showMessageDialog
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class PhrOptionsFragment : Fragment(R.layout.fragment_phr_options) {

    private val healthConnectManager: HealthConnectManager by lazy {
        requireContext().requireSystemService()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.requireViewById<Button>(R.id.phr_read_immunizations_button).setOnClickListener {
            executeAndShowMessage { readAllMedicalResources() }
        }

        view.requireViewById<Button>(R.id.phr_seed_fhir_jsons_button).setOnClickListener {
            loadAllFhirJSONs()
        }
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

    private suspend inline fun readAllMedicalResources(): String =
        readMedicalResources().joinToString(separator = "\n", transform = MedicalResource::toString)

    private suspend fun readMedicalResources(): List<MedicalResource> {
        val resources =
            suspendCancellableCoroutine<List<MedicalResource>> { continuation ->
                healthConnectManager.readMedicalResources(
                    listOf(MedicalIdFilter.fromId("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")),
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
            Toast.makeText(
                context, "No JSON files were found.", Toast.LENGTH_SHORT)
                    .show()
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
}
