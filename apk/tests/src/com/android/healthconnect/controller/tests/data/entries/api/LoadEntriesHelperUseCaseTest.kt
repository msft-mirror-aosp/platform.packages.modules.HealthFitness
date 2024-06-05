/**
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.data.entries.api

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord
import android.health.connect.datatypes.BodyTemperatureMeasurementLocation
import android.health.connect.datatypes.BodyTemperatureRecord
import android.health.connect.datatypes.BodyWaterMassRecord
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.FloorsClimbedRecord
import android.health.connect.datatypes.HydrationRecord
import android.health.connect.datatypes.IntermenstrualBleedingRecord
import android.health.connect.datatypes.OxygenSaturationRecord
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.TotalCaloriesBurnedRecord
import android.health.connect.datatypes.WeightRecord
import android.health.connect.datatypes.units.Temperature
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.api.LoadDataEntriesInput
import com.android.healthconnect.controller.data.entries.api.LoadEntriesHelper
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.dataentries.formatters.shared.HealthDataEntryFormatter
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.tests.utils.BODYTEMPERATURE_MONTH
import com.android.healthconnect.controller.tests.utils.BODYWATERMASS_WEEK
import com.android.healthconnect.controller.tests.utils.DISTANCE_STARTDATE_1500
import com.android.healthconnect.controller.tests.utils.HYDRATION_MONTH
import com.android.healthconnect.controller.tests.utils.HYDRATION_MONTH2
import com.android.healthconnect.controller.tests.utils.HYDRATION_MONTH3
import com.android.healthconnect.controller.tests.utils.INSTANT_DAY
import com.android.healthconnect.controller.tests.utils.INSTANT_MONTH3
import com.android.healthconnect.controller.tests.utils.INSTANT_WEEK
import com.android.healthconnect.controller.tests.utils.INTERMENSTRUAL_BLEEDING_DAY
import com.android.healthconnect.controller.tests.utils.OXYGENSATURATION_DAY
import com.android.healthconnect.controller.tests.utils.OXYGENSATURATION_DAY2
import com.android.healthconnect.controller.tests.utils.SLEEP_DAY_0H20
import com.android.healthconnect.controller.tests.utils.SLEEP_DAY_1H45
import com.android.healthconnect.controller.tests.utils.SLEEP_DAY_9H15
import com.android.healthconnect.controller.tests.utils.SLEEP_MONTH_81H15
import com.android.healthconnect.controller.tests.utils.SLEEP_WEEK_33H15
import com.android.healthconnect.controller.tests.utils.SLEEP_WEEK_9H15
import com.android.healthconnect.controller.tests.utils.START_TIME
import com.android.healthconnect.controller.tests.utils.TestTimeSource
import com.android.healthconnect.controller.tests.utils.WEIGHT_DAY_100
import com.android.healthconnect.controller.tests.utils.WEIGHT_MONTH_100
import com.android.healthconnect.controller.tests.utils.WEIGHT_STARTDATE_100
import com.android.healthconnect.controller.tests.utils.WEIGHT_WEEK_100
import com.android.healthconnect.controller.tests.utils.getMixedRecordsAcrossThreeDays
import com.android.healthconnect.controller.tests.utils.getMixedRecordsAcrossTwoDays
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.verifyBodyWaterMassListsEqual
import com.android.healthconnect.controller.tests.utils.verifyHydrationListsEqual
import com.android.healthconnect.controller.tests.utils.verifyOxygenSaturationListsEqual
import com.android.healthconnect.controller.tests.utils.verifySleepSessionListsEqual
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.android.healthconnect.controller.utils.atStartOfDay
import com.android.healthconnect.controller.utils.toInstant
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Stubber

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class LoadEntriesHelperUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @BindValue @JvmField val timeSource = TestTimeSource

    private val defaultStartTime: Instant = START_TIME

    private val healthConnectManager: HealthConnectManager =
        Mockito.mock(HealthConnectManager::class.java)

    @Inject lateinit var healthDataEntryFormatter: HealthDataEntryFormatter

    private lateinit var context: Context
    private lateinit var loadEntriesHelper: LoadEntriesHelper

    @Captor
    lateinit var menstruationRequestCaptor:
        ArgumentCaptor<ReadRecordsRequestUsingFilters<IntermenstrualBleedingRecord>>
    @Captor
    lateinit var sleepSessionRequestCaptor:
        ArgumentCaptor<ReadRecordsRequestUsingFilters<SleepSessionRecord>>
    @Captor
    lateinit var stepsRequestCaptor: ArgumentCaptor<ReadRecordsRequestUsingFilters<StepsRecord>>
    @Captor
    lateinit var weightRequestCaptor: ArgumentCaptor<ReadRecordsRequestUsingFilters<WeightRecord>>
    @Captor
    lateinit var bodyTempRequestCaptor:
        ArgumentCaptor<ReadRecordsRequestUsingFilters<BodyTemperatureRecord>>
    @Captor
    lateinit var oxygenSaturationRequestCaptor:
        ArgumentCaptor<ReadRecordsRequestUsingFilters<OxygenSaturationRecord>>
    @Captor
    lateinit var hydrationRequestCaptor:
        ArgumentCaptor<ReadRecordsRequestUsingFilters<HydrationRecord>>
    @Captor
    lateinit var bodyWaterMassRequestCaptor:
        ArgumentCaptor<ReadRecordsRequestUsingFilters<BodyWaterMassRecord>>

    @Before
    fun setup() {
        hiltRule.inject()
        MockitoAnnotations.initMocks(this)
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        loadEntriesHelper =
            LoadEntriesHelper(context, healthDataEntryFormatter, healthConnectManager, timeSource)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
    }

    @After
    fun teardown() {
        timeSource.reset()
    }

    @Test
    fun loadSleepData_withinDay_returnsListOfRecords_sortedByDescendingStartTime() = runTest {
        val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
            setupReadRecordTest(DateNavigationPeriod.PERIOD_DAY, HealthPermissionType.SLEEP)

        val actual = loadEntriesHelper.readRecords(input)

        val expected = listOf(SLEEP_DAY_9H15, SLEEP_DAY_0H20, SLEEP_DAY_1H45)

        assertArgumentRequestCaptorValidity(
            sleepSessionRequestCaptor, timeRangeFilter, SleepSessionRecord::class.java)
        verifySleepSessionListsEqual(actual, expected)
    }

    @Test
    fun loadSleepDataUseCase_withinWeek_returnsListOfRecords_sortedByDescendingStartTime() =
        runTest {
            val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
                setupReadRecordTest(DateNavigationPeriod.PERIOD_WEEK, HealthPermissionType.SLEEP)

            val actual = loadEntriesHelper.readRecords(input)
            val expected =
                listOf(
                    SLEEP_WEEK_9H15,
                    SLEEP_DAY_9H15,
                    SLEEP_DAY_0H20,
                    SLEEP_DAY_1H45,
                    SLEEP_WEEK_33H15)

            assertArgumentRequestCaptorValidity(
                sleepSessionRequestCaptor, timeRangeFilter, SleepSessionRecord::class.java)
            verifySleepSessionListsEqual(actual, expected)
        }

    @Test
    fun loadSleepDataUseCase_withinMonth_returnsListOfRecords_sortedByDescendingStartTime() =
        runTest {
            val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
                setupReadRecordTest(DateNavigationPeriod.PERIOD_MONTH, HealthPermissionType.SLEEP)

            val actual = loadEntriesHelper.readRecords(input)
            val expected =
                listOf(
                    SLEEP_MONTH_81H15,
                    SLEEP_WEEK_9H15,
                    SLEEP_DAY_9H15,
                    SLEEP_DAY_0H20,
                    SLEEP_DAY_1H45,
                    SLEEP_WEEK_33H15)

            assertArgumentRequestCaptorValidity(
                sleepSessionRequestCaptor, timeRangeFilter, SleepSessionRecord::class.java)
            verifySleepSessionListsEqual(actual, expected)
        }

    @Test
    fun loadHydrationUseCase_withinWeek_returnsListOfRecords_sortedByDescendingStartTime() =
        runTest {
            val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
                setupReadRecordTest(
                    DateNavigationPeriod.PERIOD_WEEK, HealthPermissionType.HYDRATION)

            val actual = loadEntriesHelper.readRecords(input)
            val expected = listOf(HYDRATION_MONTH3, HYDRATION_MONTH2, HYDRATION_MONTH)

            assertArgumentRequestCaptorValidity(
                hydrationRequestCaptor, timeRangeFilter, HydrationRecord::class.java)
            verifyHydrationListsEqual(actual, expected)
        }

    @Test
    fun loadOxygenSaturationUseCase_withinDay_returnsListOfRecords_sortedByDescendingStartTime() =
        runTest {
            val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
                setupReadRecordTest(
                    DateNavigationPeriod.PERIOD_DAY, HealthPermissionType.OXYGEN_SATURATION)

            val actual = loadEntriesHelper.readRecords(input)
            val expected = listOf(OXYGENSATURATION_DAY2, OXYGENSATURATION_DAY)

            assertArgumentRequestCaptorValidity(
                oxygenSaturationRequestCaptor, timeRangeFilter, OxygenSaturationRecord::class.java)
            verifyOxygenSaturationListsEqual(actual, expected)
        }

    @Test
    fun loadFloorsClimbedUseCase_withinMonth_returnsEmptyListOfRecords() = runTest {
        val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
            setupReadRecordTest(
                DateNavigationPeriod.PERIOD_MONTH, HealthPermissionType.FLOORS_CLIMBED)

        val actual = loadEntriesHelper.readRecords(input)

        assertThat(actual.size).isEqualTo(0)
        assertArgumentRequestCaptorValidity(
            stepsRequestCaptor, timeRangeFilter, FloorsClimbedRecord::class.java)
    }

    @Test
    fun loadBodyWaterMass_withinWeek_singleRecord_lastRecordAndGetRecordsReturnsSame() = runTest {
        val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
            setupReadRecordTest(
                DateNavigationPeriod.PERIOD_WEEK, HealthPermissionType.BODY_WATER_MASS)

        val expectedGetRecords = loadEntriesHelper.readRecords(input)
        val expectedGetLastRecord = loadEntriesHelper.readLastRecord(input)
        val actual = listOf(BODYWATERMASS_WEEK)

        assertArgumentRequestCaptorValidity(
            bodyWaterMassRequestCaptor, timeRangeFilter, BodyWaterMassRecord::class.java, 2)
        assertThat(expectedGetLastRecord.size).isEqualTo(expectedGetRecords.size)
        assertThat(bodyWaterMassRequestCaptor.value.pageSize).isEqualTo(1)
        assertThat(bodyWaterMassRequestCaptor.value.isAscending).isFalse()
        verifyBodyWaterMassListsEqual(expectedGetRecords, actual)
        verifyBodyWaterMassListsEqual(expectedGetLastRecord, actual)
        verifyBodyWaterMassListsEqual(expectedGetLastRecord, expectedGetRecords)
    }

    @Test
    fun readLastRecord_forBodyTemperature_returnsListOfOneRecord() = runTest {
        val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
            setupReadRecordTest(
                DateNavigationPeriod.PERIOD_MONTH, HealthPermissionType.BODY_TEMPERATURE)

        val expected = loadEntriesHelper.readLastRecord(input)
        val actual = listOf(BODYTEMPERATURE_MONTH)

        assertArgumentRequestCaptorValidity(
            bodyTempRequestCaptor, timeRangeFilter, BodyTemperatureRecord::class.java)
        assertThat(actual.size).isEqualTo(expected.size)
        assertThat(bodyTempRequestCaptor.value.pageSize).isEqualTo(1)
        assertThat(bodyTempRequestCaptor.value.isAscending).isFalse()
        assertThat(actual[0].time).isEqualTo(INSTANT_MONTH3)
        assertThat(actual[0].measurementLocation)
            .isEqualTo(BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_MOUTH)
        assertThat(actual[0].temperature).isEqualTo(Temperature.fromCelsius(100.0))
    }

    @Test
    fun readLastRecord_forDistance_returnsListOfOneRecord() = runTest {
        val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
            setupReadRecordTest(DateNavigationPeriod.PERIOD_MONTH, HealthPermissionType.DISTANCE)

        val actual = loadEntriesHelper.readLastRecord(input)

        val expected = listOf(DISTANCE_STARTDATE_1500)

        assertArgumentRequestCaptorValidity(
            stepsRequestCaptor, timeRangeFilter, DistanceRecord::class.java)
        assertThat(actual.size).isEqualTo(expected.size)
        assertThat((actual[0] as DistanceRecord).distance).isEqualTo(expected[0].distance)
        assertThat((actual[0] as DistanceRecord).startTime).isEqualTo(defaultStartTime)
        assertThat((actual[0] as DistanceRecord).endTime).isEqualTo(expected[0].endTime)
        assertThat(stepsRequestCaptor.value.pageSize).isEqualTo(1)
        assertThat(stepsRequestCaptor.value.isAscending).isFalse()
    }

    @Test
    fun readLastRecord_forIntermenstrualBleeding_returnsListOfOneRecord() = runTest {
        val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
            setupReadRecordTest(
                DateNavigationPeriod.PERIOD_DAY, HealthPermissionType.INTERMENSTRUAL_BLEEDING)

        val actual = loadEntriesHelper.readLastRecord(input)
        val expected = listOf(INTERMENSTRUAL_BLEEDING_DAY)

        assertArgumentRequestCaptorValidity(
            menstruationRequestCaptor, timeRangeFilter, IntermenstrualBleedingRecord::class.java)
        assertThat(actual.size).isEqualTo(expected.size)
        assertThat((actual[0] as IntermenstrualBleedingRecord).time).isEqualTo(INSTANT_DAY)
        assertThat(menstruationRequestCaptor.value.pageSize).isEqualTo(1)
        assertThat(menstruationRequestCaptor.value.isAscending).isFalse()
    }

    @Test
    fun readLastRecord_forWeight_returnsListOfOneRecord() = runTest {
        val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
            setupReadRecordTest(DateNavigationPeriod.PERIOD_WEEK, HealthPermissionType.WEIGHT)

        val actual = loadEntriesHelper.readLastRecord(input)
        val expected = listOf(WEIGHT_WEEK_100)

        assertArgumentRequestCaptorValidity(
            weightRequestCaptor, timeRangeFilter, WeightRecord::class.java)
        assertThat(actual.size).isEqualTo(expected.size)
        assertThat((actual[0] as WeightRecord).weight).isEqualTo(expected[0].weight)
        assertThat((actual[0] as WeightRecord).time).isEqualTo(INSTANT_WEEK)
        assertThat(weightRequestCaptor.value.pageSize).isEqualTo(1)
        assertThat(weightRequestCaptor.value.isAscending).isFalse()
    }

    @Test
    fun readLastRecord_forTotalCaloriesBurned_whenNoData_returnsEmptyList() = runTest {
        val (input: LoadDataEntriesInput, timeRangeFilter: TimeInstantRangeFilter) =
            setupReadRecordTest(
                DateNavigationPeriod.PERIOD_MONTH, HealthPermissionType.ACTIVE_CALORIES_BURNED)

        val actual = loadEntriesHelper.readLastRecord(input)

        assertThat(actual.size).isEqualTo(0)
        assertArgumentRequestCaptorValidity(
            stepsRequestCaptor, timeRangeFilter, ActiveCaloriesBurnedRecord::class.java)
        assertThat(stepsRequestCaptor.value.pageSize).isEqualTo(1)
        assertThat(stepsRequestCaptor.value.isAscending).isFalse()
    }

    @Test
    fun readRecordsFromDifferentDays_twoSequentialDays_sectionHeadersInserted() = runTest {
        timeSource.setNow(START_TIME)

        val recordList: List<Record> = getMixedRecordsAcrossTwoDays(timeSource)

        val formattedEntry: List<FormattedEntry> =
            loadEntriesHelper.maybeAddDateSectionHeaders(
                recordList, DateNavigationPeriod.PERIOD_WEEK, true)

        val potentialHeaderToday = formattedEntry[0]
        val potentialHeaderYesterday = formattedEntry[3]
        assertThat(formattedEntry.size).isEqualTo(6)
        assertThat(potentialHeaderToday)
            .isInstanceOf(FormattedEntry.EntryDateSectionHeader::class.java)
        assertThat((potentialHeaderToday as FormattedEntry.EntryDateSectionHeader).date)
            .isEqualTo("Today")
        assertThat(potentialHeaderYesterday)
            .isInstanceOf(FormattedEntry.EntryDateSectionHeader::class.java)
        assertThat((potentialHeaderYesterday as FormattedEntry.EntryDateSectionHeader).date)
            .isEqualTo("Yesterday")
    }

    @Test
    fun readRecordsFromDifferentDays_threeSequentialDays_sectionHeadersInserted() = runTest {
        timeSource.setNow(START_TIME)
        val recordList: List<Record> = getMixedRecordsAcrossThreeDays(timeSource)

        val formattedEntry: List<FormattedEntry> =
            loadEntriesHelper.maybeAddDateSectionHeaders(
                recordList, DateNavigationPeriod.PERIOD_WEEK, true)

        val potentialHeaderToday = formattedEntry[0]
        val potentialHeaderYesterday = formattedEntry[3]
        val potentialHeaderTwoDaysAgo = formattedEntry[6]
        assertThat(formattedEntry.size).isEqualTo(9)
        assertThat(potentialHeaderToday)
            .isInstanceOf(FormattedEntry.EntryDateSectionHeader::class.java)
        assertThat((potentialHeaderToday as FormattedEntry.EntryDateSectionHeader).date)
            .isEqualTo("Today")
        assertThat(potentialHeaderYesterday)
            .isInstanceOf(FormattedEntry.EntryDateSectionHeader::class.java)
        assertThat((potentialHeaderYesterday as FormattedEntry.EntryDateSectionHeader).date)
            .isEqualTo("Yesterday")
        assertThat(potentialHeaderTwoDaysAgo)
            .isInstanceOf(FormattedEntry.EntryDateSectionHeader::class.java)
        assertThat((potentialHeaderTwoDaysAgo as FormattedEntry.EntryDateSectionHeader).date)
            .isEqualTo(
                LocalDateTimeFormatter(context)
                    .formatLongDate(timeSource.currentLocalDateTime().minusDays(2).toInstant()))
    }

    private fun prepareDistanceAnswer(): (InvocationOnMock) -> ReadRecordsResponse<DistanceRecord> {
        return { args: InvocationOnMock ->
            val receiver =
                args.arguments[2] as OutcomeReceiver<ReadRecordsResponse<DistanceRecord>, *>
            receiver.onResult(getMonthDistanceRecords())
            getMonthDistanceRecords()
        }
    }

    private fun prepareWeightAnswer(
        dateNavigationPeriod: DateNavigationPeriod? = null
    ): (InvocationOnMock) -> ReadRecordsResponse<WeightRecord> {
        return { args: InvocationOnMock ->
            val receiver =
                args.arguments[2] as OutcomeReceiver<ReadRecordsResponse<WeightRecord>, *>
            when (dateNavigationPeriod) {
                DateNavigationPeriod.PERIOD_DAY -> {
                    receiver.onResult(getDayWeightRecords())
                    getDayWeightRecords()
                }
                DateNavigationPeriod.PERIOD_WEEK -> {
                    receiver.onResult(getWeekWeightRecords())
                    getWeekWeightRecords()
                }
                DateNavigationPeriod.PERIOD_MONTH -> {
                    receiver.onResult(getMonthWeightRecords())
                    getMonthWeightRecords()
                }
                else -> {
                    receiver.onResult(getStartDateWeightRecords())
                    getStartDateWeightRecords()
                }
            }
        }
    }

    private fun prepareMenstruationPeriodAnswer():
        (InvocationOnMock) -> ReadRecordsResponse<IntermenstrualBleedingRecord> {
        return { args: InvocationOnMock ->
            val receiver =
                args.arguments[2]
                    as OutcomeReceiver<ReadRecordsResponse<IntermenstrualBleedingRecord>, *>
            receiver.onResult(getMenstruationPeriodRecords())
            getMenstruationPeriodRecords()
        }
    }

    private fun prepareEmptyCaloriesAnswer():
        (InvocationOnMock) -> ReadRecordsResponse<TotalCaloriesBurnedRecord> {
        return { args: InvocationOnMock ->
            val receiver =
                args.arguments[2]
                    as OutcomeReceiver<ReadRecordsResponse<TotalCaloriesBurnedRecord>, *>
            receiver.onResult(getEmptyCaloriesRecords())
            getEmptyCaloriesRecords()
        }
    }

    private fun prepareEmptyFloorsClimbedAnswer():
        (InvocationOnMock) -> ReadRecordsResponse<FloorsClimbedRecord> {
        return { args: InvocationOnMock ->
            val receiver =
                args.arguments[2] as OutcomeReceiver<ReadRecordsResponse<FloorsClimbedRecord>, *>
            receiver.onResult(getEmptyFloorsClimbedRecords())
            getEmptyFloorsClimbedRecords()
        }
    }

    private fun prepareBodyTemperatureAnswer():
        (InvocationOnMock) -> ReadRecordsResponse<BodyTemperatureRecord> {
        return { args: InvocationOnMock ->
            val receiver =
                args.arguments[2] as OutcomeReceiver<ReadRecordsResponse<BodyTemperatureRecord>, *>
            receiver.onResult(getBodyTemperatureRecords())
            getBodyTemperatureRecords()
        }
    }

    private fun prepareSleepAnswer(
        timePeriod: DateNavigationPeriod
    ): (InvocationOnMock) -> ReadRecordsResponse<SleepSessionRecord> {
        return { args: InvocationOnMock ->
            val receiver =
                args.arguments[2] as OutcomeReceiver<ReadRecordsResponse<SleepSessionRecord>, *>
            receiver.onResult(getSleepRecords(timePeriod))
            getSleepRecords(timePeriod)
        }
    }

    private fun prepareOxygenSaturationAnswer():
        (InvocationOnMock) -> ReadRecordsResponse<OxygenSaturationRecord> {
        return { args: InvocationOnMock ->
            val receiver =
                args.arguments[2] as OutcomeReceiver<ReadRecordsResponse<OxygenSaturationRecord>, *>
            receiver.onResult(getOxygenSaturationRecords())
            getOxygenSaturationRecords()
        }
    }

    private fun prepareHydrationAnswer():
        (InvocationOnMock) -> ReadRecordsResponse<HydrationRecord> {
        return { args: InvocationOnMock ->
            val receiver =
                args.arguments[2] as OutcomeReceiver<ReadRecordsResponse<HydrationRecord>, *>
            receiver.onResult(getHydrationRecords())
            getHydrationRecords()
        }
    }

    private fun prepareBodyWaterMassAnswer():
        (InvocationOnMock) -> ReadRecordsResponse<BodyWaterMassRecord> {
        return { args: InvocationOnMock ->
            val receiver =
                args.arguments[2] as OutcomeReceiver<ReadRecordsResponse<BodyWaterMassRecord>, *>
            receiver.onResult(getBodyWaterMassRecords())
            getBodyWaterMassRecords()
        }
    }

    private fun getSleepRecords(
        timePeriod: DateNavigationPeriod
    ): ReadRecordsResponse<SleepSessionRecord> {
        return when (timePeriod) {
            DateNavigationPeriod.PERIOD_DAY ->
                return ReadRecordsResponse<SleepSessionRecord>(
                    listOf(SLEEP_DAY_9H15, SLEEP_DAY_0H20, SLEEP_DAY_1H45), -1)
            DateNavigationPeriod.PERIOD_WEEK ->
                return ReadRecordsResponse<SleepSessionRecord>(
                    listOf(
                        SLEEP_DAY_9H15,
                        SLEEP_DAY_0H20,
                        SLEEP_DAY_1H45,
                        SLEEP_WEEK_33H15,
                        SLEEP_WEEK_9H15),
                    -1)
            DateNavigationPeriod.PERIOD_MONTH ->
                return ReadRecordsResponse<SleepSessionRecord>(
                    listOf(
                        SLEEP_DAY_9H15,
                        SLEEP_DAY_0H20,
                        SLEEP_DAY_1H45,
                        SLEEP_WEEK_33H15,
                        SLEEP_WEEK_9H15,
                        SLEEP_MONTH_81H15),
                    -1)
            else -> throw IllegalArgumentException("DateNavigationPeriod $timePeriod not supported")
        }
    }

    private fun getOxygenSaturationRecords(): ReadRecordsResponse<OxygenSaturationRecord> {
        return ReadRecordsResponse<OxygenSaturationRecord>(
            listOf(OXYGENSATURATION_DAY2, OXYGENSATURATION_DAY), -1)
    }

    private fun getBodyTemperatureRecords(): ReadRecordsResponse<BodyTemperatureRecord> {
        return ReadRecordsResponse<BodyTemperatureRecord>(listOf(BODYTEMPERATURE_MONTH), -1)
    }

    private fun getBodyWaterMassRecords(): ReadRecordsResponse<BodyWaterMassRecord> {
        return ReadRecordsResponse<BodyWaterMassRecord>(listOf(BODYWATERMASS_WEEK), -1)
    }

    private fun getMenstruationPeriodRecords(): ReadRecordsResponse<IntermenstrualBleedingRecord> {
        return ReadRecordsResponse<IntermenstrualBleedingRecord>(
            listOf(INTERMENSTRUAL_BLEEDING_DAY), -1)
    }

    private fun getDayWeightRecords(): ReadRecordsResponse<WeightRecord> {
        return ReadRecordsResponse<WeightRecord>(listOf(WEIGHT_DAY_100), -1)
    }

    private fun getWeekWeightRecords(): ReadRecordsResponse<WeightRecord> {
        return ReadRecordsResponse<WeightRecord>(listOf(WEIGHT_WEEK_100), -1)
    }

    private fun getMonthWeightRecords(): ReadRecordsResponse<WeightRecord> {
        return ReadRecordsResponse<WeightRecord>(listOf(WEIGHT_MONTH_100), -1)
    }

    private fun getStartDateWeightRecords(): ReadRecordsResponse<WeightRecord> {
        return ReadRecordsResponse<WeightRecord>(listOf(WEIGHT_STARTDATE_100), -1)
    }

    private fun getMonthDistanceRecords(): ReadRecordsResponse<DistanceRecord> {
        return ReadRecordsResponse<DistanceRecord>(listOf(DISTANCE_STARTDATE_1500), -1)
    }

    private fun getHydrationRecords(): ReadRecordsResponse<HydrationRecord> {
        return ReadRecordsResponse<HydrationRecord>(
            listOf(HYDRATION_MONTH3, HYDRATION_MONTH2, HYDRATION_MONTH), -1)
    }

    private fun getEmptyCaloriesRecords(): ReadRecordsResponse<TotalCaloriesBurnedRecord> {
        return ReadRecordsResponse<TotalCaloriesBurnedRecord>(listOf(), -1)
    }

    private fun getEmptyFloorsClimbedRecords(): ReadRecordsResponse<FloorsClimbedRecord> {
        return ReadRecordsResponse<FloorsClimbedRecord>(listOf(), -1)
    }

    private fun setupReadRecordTest(
        timePeriod: DateNavigationPeriod,
        permissionType: HealthPermissionType
    ): Pair<LoadDataEntriesInput, TimeInstantRangeFilter> {
        val input =
            LoadDataEntriesInput(
                displayedStartTime = defaultStartTime.atStartOfDay(),
                packageName = null,
                period = timePeriod,
                showDataOrigin = true,
                permissionType = permissionType)
        val timeRangeFilter =
            loadEntriesHelper.getTimeFilter(defaultStartTime.atStartOfDay(), timePeriod, true)

        var mockitoStubber: Stubber =
            when (permissionType) {
                HealthPermissionType.ACTIVE_CALORIES_BURNED ->
                    Mockito.doAnswer(prepareEmptyCaloriesAnswer())
                HealthPermissionType.SLEEP -> Mockito.doAnswer(prepareSleepAnswer(timePeriod))
                HealthPermissionType.WEIGHT -> Mockito.doAnswer(prepareWeightAnswer(timePeriod))
                HealthPermissionType.DISTANCE -> Mockito.doAnswer(prepareDistanceAnswer())
                HealthPermissionType.INTERMENSTRUAL_BLEEDING ->
                    Mockito.doAnswer(prepareMenstruationPeriodAnswer())
                HealthPermissionType.BODY_TEMPERATURE ->
                    Mockito.doAnswer(prepareBodyTemperatureAnswer())
                HealthPermissionType.OXYGEN_SATURATION ->
                    Mockito.doAnswer(prepareOxygenSaturationAnswer())
                HealthPermissionType.HYDRATION -> Mockito.doAnswer(prepareHydrationAnswer())
                HealthPermissionType.FLOORS_CLIMBED ->
                    Mockito.doAnswer(prepareEmptyFloorsClimbedAnswer())
                HealthPermissionType.BODY_WATER_MASS ->
                    Mockito.doAnswer(prepareBodyWaterMassAnswer())
                else ->
                    throw IllegalArgumentException(
                        "HealthPermissionType $permissionType not supported")
            }

        mockitoStubber
            .`when`(healthConnectManager)
            .readRecords(
                ArgumentMatchers.any(ReadRecordsRequestUsingFilters::class.java),
                ArgumentMatchers.any(),
                ArgumentMatchers.any())

        return Pair(input, timeRangeFilter)
    }

    private fun assertArgumentRequestCaptorValidity(
        requestCaptor: ArgumentCaptor<out ReadRecordsRequestUsingFilters<out Record>>,
        timeRangeFilter: TimeInstantRangeFilter,
        recordType: Class<out Record>,
        wantedInvocationCount: Int = 1
    ) {
        Mockito.verify(healthConnectManager, Mockito.times(wantedInvocationCount))
            .readRecords(requestCaptor.capture(), ArgumentMatchers.any(), ArgumentMatchers.any())
        assertThat(requestCaptor.value.recordType).isEqualTo(recordType)
        assertThat((requestCaptor.value.timeRangeFilter as TimeInstantRangeFilter).startTime)
            .isEqualTo(timeRangeFilter.startTime)
        assertThat((requestCaptor.value.timeRangeFilter as TimeInstantRangeFilter).endTime)
            .isEqualTo(timeRangeFilter.endTime)
        assertThat((requestCaptor.value.timeRangeFilter as TimeInstantRangeFilter).isBounded)
            .isEqualTo(timeRangeFilter.isBounded)
    }
}
