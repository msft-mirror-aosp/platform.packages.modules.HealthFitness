package com.android.healthconnect.controller.tests.selectabledeletion

import android.os.Parcel
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.selectabledeletion.DeletionType
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import junit.framework.Assert.assertTrue
import org.junit.Test

class DeletionTypeTest {

    @Test
    fun deletionTypeHealthPermissionTypeData_isParcelable() {
        val deletionType =
            DeletionType.DeletionTypeHealthPermissionTypes(
                listOf(
                    FitnessPermissionType.ACTIVE_CALORIES_BURNED,
                    FitnessPermissionType.BLOOD_GLUCOSE))

        val parcel = Parcel.obtain()
        deletionType.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val recreatedDeletionType =
            DeletionType.DeletionTypeHealthPermissionTypes.CREATOR.createFromParcel(parcel)

        assertTrue(
            recreatedDeletionType.fitnessPermissionTypes == deletionType.fitnessPermissionTypes)
        assertTrue(recreatedDeletionType.hasPermissionTypes)
        assertTrue(!recreatedDeletionType.hasAppData)
        assertTrue(!recreatedDeletionType.hasEntryIds)
    }

    @Test
    fun deletionTypeAppData_isParcelable() {
        val deletionType =
            DeletionType.DeletionTypeAppData(
                packageName = TEST_APP_PACKAGE_NAME, appName = TEST_APP_NAME)

        val parcel = Parcel.obtain()
        deletionType.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val recreatedDeletionType =
            DeletionType.DeletionTypeAppData.CREATOR.createFromParcel(parcel)

        assertTrue(recreatedDeletionType.appName == deletionType.appName)
        assertTrue(recreatedDeletionType.packageName == deletionType.packageName)
        assertTrue(!recreatedDeletionType.hasPermissionTypes)
        assertTrue(recreatedDeletionType.hasAppData)
        assertTrue(!recreatedDeletionType.hasEntryIds)
    }

    @Test
    fun deletionTypeEntries_isParcelable() {
        val deletionType =
            DeletionType.DeletionTypeEntries(
                listOf("dataEntryId1", "dataEntryId2"), DataType.ACTIVE_CALORIES_BURNED)

        val parcel = Parcel.obtain()
        deletionType.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val recreatedDeletionType =
            DeletionType.DeletionTypeEntries.CREATOR.createFromParcel(parcel)

        assertTrue(recreatedDeletionType.dataType == deletionType.dataType)
        assertTrue(recreatedDeletionType.ids == deletionType.ids)
        assertTrue(!recreatedDeletionType.hasPermissionTypes)
        assertTrue(!recreatedDeletionType.hasAppData)
        assertTrue(recreatedDeletionType.hasEntryIds)
    }

    @Test
    fun deletionTypeHealthPermissionTypesFromApp_isParcelable() {
        val deletionType =
            DeletionType.DeletionTypeHealthPermissionTypesFromApp(
                listOf(
                    FitnessPermissionType.ACTIVE_CALORIES_BURNED,
                    FitnessPermissionType.BLOOD_GLUCOSE),
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME)

        val parcel = Parcel.obtain()
        deletionType.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val recreatedDeletionType =
            DeletionType.DeletionTypeHealthPermissionTypesFromApp.CREATOR.createFromParcel(parcel)

        assertTrue(recreatedDeletionType.appName == deletionType.appName)
        assertTrue(recreatedDeletionType.packageName == deletionType.packageName)
        assertTrue(
            recreatedDeletionType.fitnessPermissionTypes == deletionType.fitnessPermissionTypes)
        assertTrue(recreatedDeletionType.hasPermissionTypes)
        assertTrue(recreatedDeletionType.hasAppData)
        assertTrue(!deletionType.hasEntryIds)
    }
}
