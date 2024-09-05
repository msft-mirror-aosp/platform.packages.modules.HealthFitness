/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.data.entries

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.data.entries.api.ILoadDataAggregationsUseCase
import com.android.healthconnect.controller.data.entries.api.ILoadDataEntriesUseCase
import com.android.healthconnect.controller.data.entries.api.ILoadMedicalEntriesUseCase
import com.android.healthconnect.controller.data.entries.api.ILoadMenstruationDataUseCase
import com.android.healthconnect.controller.data.entries.api.LoadAggregationInput
import com.android.healthconnect.controller.data.entries.api.LoadDataEntriesInput
import com.android.healthconnect.controller.data.entries.api.LoadMedicalEntriesInput
import com.android.healthconnect.controller.data.entries.api.LoadMenstruationDataInput
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType.DISTANCE
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType.STEPS
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType.TOTAL_CALORIES_BURNED
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for [AppEntriesFragment] and [AllEntriesFragment]. */
@HiltViewModel
class EntriesViewModel
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val loadDataEntriesUseCase: ILoadDataEntriesUseCase,
    private val loadMenstruationDataUseCase: ILoadMenstruationDataUseCase,
    private val loadDataAggregationsUseCase: ILoadDataAggregationsUseCase,
    private val loadMedicalEntriesUseCase: ILoadMedicalEntriesUseCase,
) : ViewModel() {

    companion object {
        private const val TAG = "EntriesViewModel"
        private val AGGREGATE_HEADER_DATA_TYPES = listOf(STEPS, DISTANCE, TOTAL_CALORIES_BURNED)
    }

    private val _entries = MutableLiveData<EntriesFragmentState>()
    val entries: LiveData<EntriesFragmentState>
        get() = _entries

    val currentSelectedDate = MutableLiveData<Instant>()
    val period = MutableLiveData<DateNavigationPeriod>()

    private val _appInfo = MutableLiveData<AppMetadata>()
    val appInfo: LiveData<AppMetadata>
        get() = _appInfo

    private val _setOfEntriesToBeDeleted = MutableLiveData<Set<String>>()

    val setOfEntriesToBeDeleted: LiveData<Set<String>>
        get() = _setOfEntriesToBeDeleted

    private var dataType: DataType? = null

    private val _isDeletionState = MutableLiveData<Boolean>(false)
    val isDeletionState:LiveData<Boolean>
        get() = _isDeletionState

    private var dateNavigationText: String? = null

    fun loadEntries(
        permissionType: HealthPermissionType,
        selectedDate: Instant,
        period: DateNavigationPeriod
    ) {
        when (permissionType) {
            is FitnessPermissionType ->
                loadData(
                    permissionType, packageName = null, selectedDate, period, showDataOrigin = true)
            is MedicalPermissionType ->
                loadData(permissionType, packageName = null, showDataOrigin = true)
        }
    }

    fun loadEntries(
        permissionType: HealthPermissionType,
        packageName: String,
        selectedDate: Instant,
        period: DateNavigationPeriod
    ) {
        when (permissionType) {
            is FitnessPermissionType ->
                loadData(permissionType, packageName, selectedDate, period, showDataOrigin = false)
            is MedicalPermissionType ->
                loadData(permissionType, packageName, showDataOrigin = false)
        }
    }

    private fun loadData(
        permissionType: FitnessPermissionType,
        packageName: String?,
        selectedDate: Instant,
        period: DateNavigationPeriod,
        showDataOrigin: Boolean
    ) {
        _entries.postValue(EntriesFragmentState.Loading)
        currentSelectedDate.postValue(selectedDate)
        this.period.postValue(period)

        viewModelScope.launch {
            val list = ArrayList<FormattedEntry>()
            val entriesResults =
                when (permissionType) {
                    // Special-casing Menstruation as it spans multiple days
                    FitnessPermissionType.MENSTRUATION -> {
                        loadMenstruation(packageName, selectedDate, period, showDataOrigin)
                    }
                    else -> {
                        loadAppEntries(
                            permissionType, packageName, selectedDate, period, showDataOrigin)
                    }
                }
            when (entriesResults) {
                is UseCaseResults.Success -> {
                    list.addAll(entriesResults.data)
                    if (list.isEmpty()) {
                        _entries.postValue(EntriesFragmentState.Empty)
                    } else {
                        addAggregation(
                            permissionType, packageName, selectedDate, period, list, showDataOrigin)
                        _entries.postValue(EntriesFragmentState.With(list))
                    }
                }
                is UseCaseResults.Failed -> {
                    Log.e(TAG, "Loading error ", entriesResults.exception)
                    _entries.postValue(EntriesFragmentState.LoadingFailed)
                }
            }
        }
    }

    private fun loadData(
        permissionType: MedicalPermissionType,
        packageName: String?,
        showDataOrigin: Boolean
    ) {
        _entries.postValue(EntriesFragmentState.Loading)

        viewModelScope.launch {
            val entriesResults = loadAppEntries(permissionType, packageName, showDataOrigin)
            when (entriesResults) {
                is UseCaseResults.Success -> {
                    val list = entriesResults.data
                    if (list.isEmpty()) {
                        _entries.postValue(EntriesFragmentState.Empty)
                    } else {
                        _entries.postValue(EntriesFragmentState.With(list))
                    }
                }
                is UseCaseResults.Failed -> {
                    Log.e(TAG, "Loading error ", entriesResults.exception)
                    _entries.postValue(EntriesFragmentState.LoadingFailed)
                }
            }
        }
    }

    private suspend fun loadAppEntries(
        permissionType: FitnessPermissionType,
        packageName: String?,
        selectedDate: Instant,
        period: DateNavigationPeriod,
        showDataOrigin: Boolean
    ): UseCaseResults<List<FormattedEntry>> {
        val input =
            LoadDataEntriesInput(permissionType, packageName, selectedDate, period, showDataOrigin)
        return loadDataEntriesUseCase.invoke(input)
    }

    private suspend fun loadAppEntries(
        permissionType: MedicalPermissionType,
        packageName: String?,
        showDataOrigin: Boolean
    ): UseCaseResults<List<FormattedEntry>> {
        val input = LoadMedicalEntriesInput(permissionType, packageName, showDataOrigin)
        return loadMedicalEntriesUseCase.invoke(input)
    }

    private suspend fun loadMenstruation(
        packageName: String?,
        selectedDate: Instant,
        period: DateNavigationPeriod,
        showDataOrigin: Boolean
    ): UseCaseResults<List<FormattedEntry>> {
        val input = LoadMenstruationDataInput(packageName, selectedDate, period, showDataOrigin)
        return loadMenstruationDataUseCase.invoke(input)
    }

    private suspend fun loadAggregation(
        permissionType: FitnessPermissionType,
        packageName: String?,
        selectedDate: Instant,
        period: DateNavigationPeriod,
        showDataOrigin: Boolean
    ): UseCaseResults<FormattedEntry.FormattedAggregation> {
        val input =
            LoadAggregationInput.PeriodAggregation(
                permissionType, packageName, selectedDate, period, showDataOrigin)
        return loadDataAggregationsUseCase.invoke(input)
    }

    fun loadAppInfo(packageName: String) {
        viewModelScope.launch { _appInfo.postValue(appInfoReader.getAppMetadata(packageName)) }
    }

    private suspend fun addAggregation(
        permissionType: FitnessPermissionType,
        packageName: String?,
        selectedDate: Instant,
        period: DateNavigationPeriod,
        list: ArrayList<FormattedEntry>,
        showDataOrigin: Boolean
    ) {
        if (permissionType in AGGREGATE_HEADER_DATA_TYPES) {
            when (val aggregationResult =
                loadAggregation(
                    permissionType, packageName, selectedDate, period, showDataOrigin)) {
                is UseCaseResults.Success -> {
                    list.add(0, aggregationResult.data)
                }
                is UseCaseResults.Failed -> {
                    Log.e(TAG, "Failed to load aggregation!", aggregationResult.exception)
                }
            }
        }
    }

    fun addToDeleteSet(entryID: String){
        val deleteSet = _setOfEntriesToBeDeleted.value.orEmpty().toMutableSet()
        deleteSet.add(entryID)
        _setOfEntriesToBeDeleted.value = deleteSet.toSet()
    }

    fun removeFromDeleteSet(entryID: String){
        val deleteSet = _setOfEntriesToBeDeleted.value.orEmpty().toMutableSet()
        deleteSet.remove(entryID)
        _setOfEntriesToBeDeleted.value = deleteSet.toSet()
    }

    private fun resetDeleteSet() {
        _setOfEntriesToBeDeleted.value = emptySet()
    }

    fun setIsDeletionState(isDeletionState: Boolean){
        _isDeletionState.value = isDeletionState
        if(_isDeletionState.value == false){
            resetDeleteSet()
        }
    }

    fun setDataType(dataType: DataType){
        this.dataType = dataType
    }

    fun getDataType(): DataType? {
        return dataType
    }

    fun setDateNavigationText(text: String){
        this.dateNavigationText = text
    }

    fun getDateNavigationText(): String? {
        return dateNavigationText
    }

    sealed class EntriesFragmentState {
        object Loading : EntriesFragmentState()

        object Empty : EntriesFragmentState()

        object LoadingFailed : EntriesFragmentState()

        data class With(val entries: List<FormattedEntry>) : EntriesFragmentState()
    }
}
