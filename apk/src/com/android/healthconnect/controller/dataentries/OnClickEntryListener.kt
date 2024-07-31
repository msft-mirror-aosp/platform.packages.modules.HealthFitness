package com.android.healthconnect.controller.dataentries

@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
/** OnClickListener for Data entries. */
interface OnClickEntryListener {
    fun onItemClicked(id: String, index: Int)
}
