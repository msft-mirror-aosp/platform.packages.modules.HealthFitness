package com.android.healthconnect.controller.dataentries

import com.android.healthconnect.controller.shared.DataType
import java.time.Instant

@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
/** OnDeleteListener for Data entries. */
interface OnDeleteEntryListener {
    fun onDeleteEntry(
        id: String,
        dataType: DataType,
        index: Int,
        startTime: Instant? = null,
        endTime: Instant? = null
    )
}
