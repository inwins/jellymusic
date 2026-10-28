package com.example.jellymusic.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.jellymusic.jellyfin.BaseItem

class SelectionStore {
    var selecting by mutableStateOf(false)
        private set

    var allItems: List<BaseItem> = emptyList()
        private set

    val selected = mutableStateMapOf<String, BaseItem>()

    fun begin(item: BaseItem? = null) {
        selecting = true
        if (item != null) selected[item.id] = item
    }

    fun toggle(item: BaseItem) {
        if (!selecting) return
        if (selected.containsKey(item.id)) {
            selected.remove(item.id)
        } else {
            selected[item.id] = item
        }
    }

    fun setAllItems(items: List<BaseItem>) {
        allItems = items
    }

    fun selectAll() {
        allItems.forEach { selected[it.id] = it }
        if (selected.isNotEmpty()) selecting = true
    }

    fun clear() {
        selected.clear()
        selecting = false
        allItems = emptyList()
    }
}
