package com.group1.foodmanager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UiEvent {
    data class Toast(val message: String) : UiEvent()
    data class Error(val message: String) : UiEvent()
}

class StockViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private var registration: ListenerRegistration? = null

    var stockList by mutableStateOf(listOf<StockItem>())
        private set
    var loading by mutableStateOf(true)
        private set
    var adjustmentReason by mutableStateOf("")
        private set

    var showMaterialSelectDialog by mutableStateOf(false)
    var showAddDialog by mutableStateOf(false)
    var addStockItem by mutableStateOf<StockItem?>(null)
    var addQty by mutableStateOf(1)

    var showAdjustDialog by mutableStateOf(false)
    var adjustStockItem by mutableStateOf<StockItem?>(null)
    var newStockQty by mutableStateOf(1)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        registration = db.collection("stock")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    loading = false
                    viewModelScope.launch {
                        _events.emit(UiEvent.Error("Inventory monitoring failed: ${error.message}"))
                    }
                    return@addSnapshotListener
                }
                stockList = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(StockItem::class.java)?.copy(foodId = doc.id)
                } ?: emptyList()
                loading = false
            }
    }

    override fun onCleared() {
        registration?.remove()
        super.onCleared()
    }

    fun openMaterialSelect() { showMaterialSelectDialog = true }
    fun closeMaterialSelect() { showMaterialSelectDialog = false }

    fun selectMaterialToAdd(material: StockItem) {
        addStockItem = material
        addQty = 1
        showMaterialSelectDialog = false
        showAddDialog = true
    }

    fun openAddDialog(item: StockItem) {
        addStockItem = item
        addQty = 1
        showAddDialog = true
    }
    fun closeAddDialog() {
        showAddDialog = false
        addStockItem = null
    }
    fun increaseAddQty() { if (addQty < 999) addQty++ }
    fun decreaseAddQty() { if (addQty > 1) addQty-- }

    fun confirmPurchase() {
        val item = addStockItem ?: return
        val qty = addQty
        viewModelScope.launch {
            try {
                purchaseStock(item, qty)
                _events.emit(UiEvent.Toast("Successful procurement ${item.name} x$qty"))
            } catch (e: Exception) {
                _events.emit(UiEvent.Error("Failed purchase: ${e.message}"))
            }finally {
                closeAddDialog()
            }
        }
    }

    fun openAdjustDialog(item: StockItem) {
        adjustStockItem = item
        newStockQty = item.quantity.toInt()
        adjustmentReason = ""
        showAdjustDialog = true
    }
    fun closeAdjustDialog() {
        showAdjustDialog = false
        adjustStockItem = null
    }
    fun increaseNewStockQty() { if (newStockQty < 999) newStockQty++ }
    fun decreaseNewStockQty() { if (newStockQty > 0) newStockQty-- }
    fun updateAdjustmentReason(reason: String) {
        adjustmentReason = reason
    }


    fun confirmAdjust() {
        val item = adjustStockItem ?: return
        val reason = adjustmentReason.trim()
        if (reason.isBlank()) {
            viewModelScope.launch { _events.emit(UiEvent.Error("Please enter the reason for the adjustment")) }
            return
        }
        val qty = newStockQty
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    adjustStock(item, qty, reason)
                }
                _events.emit(UiEvent.Toast("Inventory adjustment successful"))
                closeAdjustDialog()
            } catch (e: Exception) {
                _events.emit(UiEvent.Error("Adjustment failed: ${e.message}"))
            }
        }
    }
}
