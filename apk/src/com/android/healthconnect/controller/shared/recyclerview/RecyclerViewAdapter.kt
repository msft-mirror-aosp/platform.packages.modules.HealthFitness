/**
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.healthconnect.controller.shared.recyclerview

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView
import com.android.healthconnect.controller.data.entries.EntriesViewModel
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.shared.DataType

/**
 * {@link RecyclerView.Adapter} that handles binding objects of different classes to a corresponding
 * {@link View}.
 */
class RecyclerViewAdapter
private constructor(
    private val itemClassToItemViewTypeMap: Map<Class<*>, Int>,
    private val itemViewTypeToViewBinderMap: Map<Int, ViewBinder<*, out View>>,
    private val viewModel: ViewModel,
) : RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {

    class Builder {
        companion object {
            // Base item view type to use when setting a view binder for objects of a specific class
            private const val BASE_ITEM_VIEW_TYPE = 100
        }

        private var nextItemType = BASE_ITEM_VIEW_TYPE
        private val itemClassToItemViewTypeMap: MutableMap<Class<*>, Int> = mutableMapOf()
        private val itemViewTypeToViewBinderMap: MutableMap<Int, ViewBinder<*, out View>> =
            mutableMapOf()
        private lateinit var viewModel: ViewModel

        fun <T> setViewBinder(clazz: Class<T>, viewBinder: ViewBinder<T, out View>): Builder {
            itemClassToItemViewTypeMap[clazz] = nextItemType
            itemViewTypeToViewBinderMap[nextItemType] = viewBinder
            nextItemType++
            return this
        }

        fun setViewModel(viewModel: ViewModel): Builder {
            this.viewModel = viewModel
            return this
        }

        fun build() =
            RecyclerViewAdapter(itemClassToItemViewTypeMap, itemViewTypeToViewBinderMap, viewModel)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private var data: MutableList<Any> = mutableListOf()
    private var isDeletionState = false
    private var isChecked = false
    private var deleteMap: Map<String, DataType> = emptyMap()
    private var isSelectAllChecked: Boolean = false

    fun updateData(entries: List<Any>) {
        this.data = entries.toMutableList()
        notifyDataSetChanged()
    }

    fun insertSelectAll(selectAll: FormattedEntry.SelectAllHeader) {
        val entriesList = (viewModel as EntriesViewModel).getEntriesList()
        if (entriesList.isNotEmpty() && entriesList.first() !is FormattedEntry.SelectAllHeader) {
            entriesList.add(0, selectAll)
            updateData(entriesList)
        }
    }

    fun insertAggregation(aggregation: FormattedEntry.FormattedAggregation) {
        val entriesList = (viewModel as EntriesViewModel).getEntriesList()
        if (
            entriesList.isNotEmpty() && entriesList.first() !is FormattedEntry.FormattedAggregation
        ) {
            entriesList.add(0, aggregation)
            updateData(entriesList)
        }
    }

    fun removeSelectAll() {
        val entriesList = (viewModel as EntriesViewModel).getEntriesList()
        if (entriesList.isNotEmpty() && entriesList.first() is FormattedEntry.SelectAllHeader) {
            entriesList.removeAt(0)
            updateData(entriesList)
        }
    }

    fun removeAggregation() {
        val entriesList = (viewModel as EntriesViewModel).getEntriesList()
        if (
            entriesList.isNotEmpty() && entriesList.first() is FormattedEntry.FormattedAggregation
        ) {
            entriesList.removeAt(0)
            updateData(entriesList)
        }
    }

    fun showCheckBox(isDeletionState: Boolean) {
        this.isDeletionState = isDeletionState
        notifyDataSetChanged()
    }

    fun checkSelectAll(isChecked: Boolean) {
        this.isSelectAllChecked = isChecked
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewBinder = checkNotNull(itemViewTypeToViewBinderMap[viewType])
        return ViewHolder(viewBinder.newView(parent))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val viewBinder: ViewBinder<Any, View> =
            checkNotNull(itemViewTypeToViewBinderMap[getItemViewType(position)])
                as ViewBinder<Any, View>
        val item = data[position]

        if (viewBinder is SimpleViewBinder) {
            viewBinder.bind(holder.itemView, item, position)
        } else if (item is FormattedEntry.SelectAllHeader) {
            (viewBinder as DeletionViewBinder).bind(
                holder.itemView,
                item,
                position,
                isDeletionState,
                isSelectAllChecked,
            )
        } else if (viewBinder is DeletionViewBinder && viewModel is EntriesViewModel) {
            this.deleteMap = viewModel.mapOfEntriesToBeDeleted.value.orEmpty()
            isChecked = (item as FormattedEntry).uuid in deleteMap
            viewBinder.bind(holder.itemView, item, position, isDeletionState, isChecked)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val clazz = data[position].javaClass
        return checkNotNull(itemClassToItemViewTypeMap[clazz])
    }

    override fun getItemCount(): Int {
        return data.size
    }
}
