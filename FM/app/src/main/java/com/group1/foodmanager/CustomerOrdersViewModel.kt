package com.group1.foodmanager

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class CustomerOrdersViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private var currentCustomerId: String? = null

    private var ordersReg: ListenerRegistration? = null
    private var menuReg: ListenerRegistration? = null
    private var stockReg: ListenerRegistration? = null

    private val _orders = mutableStateOf<List<OrderModel>>(emptyList())
    val orders: State<List<OrderModel>> get() = _orders

    private val _menuList = mutableStateOf<List<MenuItem>>(emptyList())
    val menuList: State<List<MenuItem>> get() = _menuList

    private val _stockById = mutableStateOf<Map<String, StockItem>>(emptyMap())

    private val _orderToUpdate = mutableStateOf<OrderModel?>(null)
    val orderToUpdate: State<OrderModel?> get() = _orderToUpdate

    private val _loading = mutableStateOf(true)
    val loading: State<Boolean> get() = _loading

    private val _isProcessing = mutableStateOf(false)
    val isProcessing: State<Boolean> get() = _isProcessing

    fun startListeners(customerId: String) {
        if (currentCustomerId == customerId && ordersReg != null) return

        stopListeners()
        currentCustomerId = customerId
        _loading.value = true

        ordersReg = db.collection("orders")
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CustomerOrdersVM", "orders listen failed: ${error.message}")
                    _loading.value = false
                    return@addSnapshotListener
                }
                _orders.value = snapshot?.documents
                    ?.mapNotNull { it.toObject(OrderModel::class.java)?.copy(orderId = it.id) }
                    ?: emptyList()
                _loading.value = false
            }

        menuReg = db.collection("menu").addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e("CustomerOrdersVM", "menu listen failed: ${err.message}")
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull { doc ->
                val name = doc.getString("name") ?: doc.id
                val price = readPrice(doc)
                val recipe = readRecipe(doc.get("recipe"))
                val imageName = doc.getString("imageResName")

                MenuItem(
                    menuId = doc.id,
                    name = name,
                    price = price,
                    recipe = recipe,
                    imageResName = imageName,
                    imageResId = 0
                )
            }.orEmpty()
            _menuList.value = list
        }

        stockReg = db.collection("stock").addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e("CustomerOrdersVM", "stock listen failed: ${err.message}")
                return@addSnapshotListener
            }
            val stocks = snap?.documents?.mapNotNull { d ->
                d.toObject(StockItem::class.java)?.copy(foodId = d.id)
            } ?: emptyList()
            _stockById.value = stocks.associateBy { it.foodId }
        }
    }

    fun stopListeners() {
        ordersReg?.remove(); ordersReg = null
        menuReg?.remove(); menuReg = null
        stockReg?.remove(); stockReg = null
        currentCustomerId = null
    }

    override fun onCleared() {
        stopListeners()
        super.onCleared()
    }

    fun setOrderToUpdate(order: OrderModel?) {
        _orderToUpdate.value = order
    }

    fun deleteOrder(
        orderId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                deleteOrderTransactionalMulti(orderId)
                onSuccess()
            } catch (e: Exception) {
                Log.e("CustomerOrdersVM", "deleteOrder err", e)
                onError(e.message ?: "Delete failed")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun updateOrderFromScreen(
        orderId: String,
        targetMenuId: String,
        newQty: Int,
        newAddons: List<SelectedAddon>,
        newNote: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                updateOrderWithAddonsAndNote(
                    orderId = orderId,
                    targetMenuId = targetMenuId,
                    newQty = newQty,
                    newAddons = newAddons,
                    newNote = newNote
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e("CustomerOrdersVM", "updateOrder err", e)
                onError(e.message ?: "Update failed")
            } finally {
                _isProcessing.value = false
            }
        }
    }
}