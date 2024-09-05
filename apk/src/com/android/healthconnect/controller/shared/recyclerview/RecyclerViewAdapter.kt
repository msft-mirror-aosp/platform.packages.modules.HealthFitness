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

/**
 * {@link RecyclerView.Adapter} that handles binding objects of different classes to a corresponding
 * {@link View}.
 */
class RecyclerViewAdapter
private constructor(
    private val itemClassToItemViewTypeMap: Map<Class<*>, Int>,
    private val itemViewTypeToViewBinderMap: Map<Int, ViewBinder<*, out View>>,
        private val entriesViewModel: EntriesViewModel
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
        private lateinit var entriesViewModel : EntriesViewModel

        fun <T> setViewBinder(clazz: Class<T>, viewBinder: ViewBinder<T, out View>): Builder {
            itemClassToItemViewTypeMap[clazz] = nextItemType
            itemViewTypeToViewBinderMap[nextItemType] = viewBinder
            nextItemType++
            return this
        }

        fun setViewModel(viewModel: ViewModel): Builder{
            entriesViewModel = viewModel as EntriesViewModel
            return this
        }

        fun build() = RecyclerViewAdapter(itemClassToItemViewTypeMap, itemViewTypeToViewBinderMap, entriesViewModel)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private var data: List<Any> = emptyList()
    private var isDeletionState = false
    private var isChecked = false
    private var deleteSet: Set<String> = emptySet()

    fun updateData(entries: List<Any>) {
        this.data = entries
        notifyDataSetChanged()
    }

    fun showCheckBox(isDeletionState:Boolean){
        this.isDeletionState = isDeletionState
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
        } else if (viewBinder is DeletionViewBinder) {
            this.deleteSet = entriesViewModel.setOfEntriesToBeDeleted.value.orEmpty()
            isChecked = (item as FormattedEntry).uuid in deleteSet
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
