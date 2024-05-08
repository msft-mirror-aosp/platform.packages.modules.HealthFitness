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

import android.health.connect.HealthConnectManager
import android.health.connect.MedicalIdFilter
import android.health.connect.datatypes.MedicalResource
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.core.os.asOutcomeReceiver
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.android.healthconnect.testapps.toolbox.R
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils.Companion.requireSystemService
import com.android.healthconnect.testapps.toolbox.utils.GeneralUtils.Companion.showMessageDialog
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
        readMedicalResources().joinToString(separator = "\n", transform = MedicalResource::getData)

    private suspend fun readMedicalResources(): List<MedicalResource> {
        val resources =
            suspendCancellableCoroutine<List<MedicalResource>> { continuation ->
                healthConnectManager.readMedicalResources(
                    listOf(MedicalIdFilter.fromId("1")),
                    Runnable::run,
                    continuation.asOutcomeReceiver())
            }
        Log.d("READ_MEDICAL_RESOURCES", "Read ${resources.size} resources")
        return resources
    }
}
