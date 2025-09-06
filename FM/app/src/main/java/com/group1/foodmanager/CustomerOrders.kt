package com.group1.foodmanager

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.math.RoundingMode

data class SelectedAddon(
    val addonId: String,
    val name: String,
    val price: Double,
    val qtyPerUnit: Int = 1
)

data class SelectedMenu(
    val menuId: String,
    val qty: Int,
    val addons: List<SelectedAddon> = emptyList(),
    val note: String? = null
)

data class Addon(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0
)

data class OrderItem(
    val foodId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 0,
    val addons: List<Addon> = emptyList(),
    val note: String = "",
    val recipeSnapshot: Map<String, Long> = emptyMap(),
    val addonsSnapshot: List<Map<String, Any>> = emptyList()
)

data class OrderModel(
    val orderId: String = "",
    val customerId: String = "",
    val items: List<OrderItem> = emptyList(),
    val totalPrice: Double = 0.0,
    val serviceCharge: Double = 0.0,
    val status: String = "pending",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val incomeId: String = ""
)

data class MenuItem(
    val menuId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val recipe: Map<String, Long> = emptyMap(),
    val imageResName: String? = null,
    val imageResId: Int = R.drawable.chicken_rice
)

data class StockItem(
    val foodId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Long = 0L
)
@Composable
fun OrderCard(order: OrderModel, onUpdate: (OrderModel) -> Unit, onDelete: (OrderModel) -> Unit, isBusy: Boolean = false) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Order ID: ${order.orderId}", style = MaterialTheme.typography.bodyMedium)
            Text("Status: ${order.status}", style = MaterialTheme.typography.bodySmall)
            Text("Total: ${formatCurrency(order.totalPrice)}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Column {
                if (order.items.isEmpty()) {
                    Text("No items")
                } else {
                    order.items.forEach {
                        Text("${it.name} x${it.quantity} - ${formatCurrency(it.price * it.quantity)}")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onUpdate(order) }, enabled = !isBusy) { Text("Update") }
                OutlinedButton(onClick = { onDelete(order) }, enabled = !isBusy) { Text("Delete") }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerOrdersScreen(
    customerId: String,
    vm: CustomerOrdersViewModel = viewModel()
) {
    val context = LocalContext.current
    val orders by vm.orders
    val menuList by vm.menuList
    val orderToUpdate by vm.orderToUpdate
    val loading by vm.loading
    val isProcessing by vm.isProcessing

    DisposableEffect(customerId) {
        vm.startListeners(customerId)
        onDispose { vm.stopListeners() }
    }

    if (orderToUpdate != null) {
        val order = orderToUpdate!!
        val orderItem = order.items.firstOrNull()
        val menuItemRaw = orderItem?.foodId?.let { id -> menuList.find { it.menuId == id } }

        if (menuItemRaw != null && orderItem != null) {
            val menuItem = remember(menuItemRaw) {
                val resolvedImageId = menuItemRaw.imageResId.takeIf { it != 0 }
                    ?: run {
                        val name = menuItemRaw.imageResName ?: ""
                        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
                        if (id != 0) id else R.drawable.chicken_rice
                    }
                menuItemRaw.copy(imageResId = resolvedImageId)
            }

            FoodDetailScreen(
                menuItem = menuItem,
                orderItem = orderItem,
                isUpdating = true,
                onPlaceOrder = { _, _, _ ->  },
                onUpdateOrder = { newQuantity, newAddons, newNote ->
                    if (isProcessing) return@FoodDetailScreen
                    vm.updateOrderFromScreen(
                        orderId = order.orderId,
                        targetMenuId = orderItem.foodId,
                        newQty = newQuantity,
                        newAddons = newAddons,
                        newNote = newNote,
                        onSuccess = {
                            Toast.makeText(context, "Order updated successfully", Toast.LENGTH_SHORT).show()
                            vm.setOrderToUpdate(null)
                        },
                        onError = { msg ->
                            Toast.makeText(context, "Update failed: $msg", Toast.LENGTH_LONG).show()
                        }
                    )
                },
                onBack = { vm.setOrderToUpdate(null) }
            )
        } else {
            LaunchedEffect(Unit) {
                Toast.makeText(context, "Unable to load order details", Toast.LENGTH_SHORT).show()
                vm.setOrderToUpdate(null)
            }
        }
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Orders") }) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                orders.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("You have no orders yet.")
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(orders, key = { it.orderId }) { order ->
                            OrderCard(
                                order = order,
                                onUpdate = { if (!isProcessing) vm.setOrderToUpdate(it) },
                                onDelete = {
                                    if (isProcessing) return@OrderCard
                                    vm.deleteOrder(
                                        it.orderId,
                                        onSuccess = { Toast.makeText(context, "Order deleted", Toast.LENGTH_SHORT).show() },
                                        onError = { msg -> Toast.makeText(context, "Deletion failed: $msg", Toast.LENGTH_LONG).show() }
                                    )
                                },
                                isBusy = isProcessing
                            )
                        }
                    }
                }
            }
        }
    }
}

fun maxAvailableForMenu(menu: MenuItem, stockById: Map<String, StockItem>): Int {
    if (menu.recipe.isEmpty()) return Int.MAX_VALUE
    val minByIng = menu.recipe.entries.map { (ingId, perUnit) ->
        if (perUnit <= 0L) Long.MAX_VALUE else (stockById[ingId]?.quantity ?: 0L) / perUnit
    }.minOrNull() ?: 0L
    return minByIng.coerceAtMost(9999L).toInt()
}

suspend fun updateOrderWithAddonsAndNote(
    orderId: String,
    targetMenuId: String,
    newQty: Int,
    newAddons: List<SelectedAddon>,
    newNote: String
) {
    val db = FirebaseFirestore.getInstance()

    db.runTransaction { tx ->

        val orderRef = db.collection("orders").document(orderId)
        val orderSnap = tx.get(orderRef)
        if (!orderSnap.exists()) throw IllegalStateException("Order not found")

        val oldItems = (orderSnap.get("items") as? List<Map<String, Any>>)?.toMutableList()
            ?: mutableListOf()
        val idx = oldItems.indexOfFirst { (it["foodId"] as? String) == targetMenuId }
        if (idx == -1) throw IllegalStateException("Item not found in order: $targetMenuId")

        val oldItemMap = oldItems[idx]
        val oldQty = (oldItemMap["quantity"] as? Number)?.toInt() ?: 0


        val oldAddonsSnapshot = (oldItemMap["addonsSnapshot"] as? List<*>) ?: emptyList<Any>()
        val oldAddonsByIngredient = mutableMapOf<String, Long>()
        oldAddonsSnapshot.forEach { raw ->
            val m = raw as? Map<*, *> ?: return@forEach
            val addonId = m["addonId"] as? String ?: return@forEach
            val perUnit = anyToLong(m["qtyPerUnit"]) ?: 1L
            oldAddonsByIngredient[addonId] = (oldAddonsByIngredient[addonId] ?: 0L) + perUnit
        }

        val newAddonsByIngredient = mutableMapOf<String, Long>()
        newAddons.forEach { addon ->
            newAddonsByIngredient[addon.addonId] =
                (newAddonsByIngredient[addon.addonId] ?: 0L) + addon.qtyPerUnit.toLong()
        }

        val recipeSnapshot = (oldItemMap["recipeSnapshot"] as? Map<*, *>)?.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val per = anyToLong(v) ?: return@mapNotNull null
            key to per
        }?.toMap().orEmpty()

        val stockChanges = mutableMapOf<String, Long>()

        val qtyDelta = newQty - oldQty
        recipeSnapshot.forEach { (ingId, perUnit) ->
            val change = perUnit * qtyDelta.toLong()
            stockChanges[ingId] = (stockChanges[ingId] ?: 0L) + change
        }

        val allAddonIngredients = (oldAddonsByIngredient.keys + newAddonsByIngredient.keys).toSet()
        allAddonIngredients.forEach { addonId ->
            val oldTotal = (oldAddonsByIngredient[addonId] ?: 0L) * oldQty.toLong()
            val newTotal = (newAddonsByIngredient[addonId] ?: 0L) * newQty.toLong()
            val change = newTotal - oldTotal
            stockChanges[addonId] = (stockChanges[addonId] ?: 0L) + change
        }

        val menuSnap = tx.get(db.collection("menu").document(targetMenuId))
        if (!menuSnap.exists()) throw IllegalStateException("Menu not found: $targetMenuId")
        val basePrice = anyToDouble(menuSnap.get("price")) ?: 0.0

        val stockRefs = stockChanges.keys.associateWith { ingId ->
            db.collection("stock").document(ingId)
        }
        val stockSnaps = stockRefs.mapValues { (_, ref) -> tx.get(ref) }

        stockChanges.forEach { (ingId, change) ->
            if (change > 0) { // Only check for stock decreases (positive change means less stock)
                val snap = stockSnaps[ingId] ?: throw IllegalStateException("Missing snapshot $ingId")
                val have = snap.getLong("quantity") ?: 0L
                if (have < change) {
                    throw IllegalStateException("Not enough stock: $ingId Need $change, Existing $have")
                }
            }
        }

        stockChanges.forEach { (ingId, change) ->
            val snap = stockSnaps[ingId]!!
            val stockRef = stockRefs[ingId]!!

            if (snap.exists()) {
                val have = snap.getLong("quantity") ?: 0L
                tx.update(stockRef, "quantity", have - change)
            } else {
                if (change < 0) {
                    tx.set(stockRef, mapOf("quantity" to (-change)))
                } else {
                    throw IllegalStateException("Inventory record does not exist: $ingId")
                }
            }
        }

        val addonPrice = newAddons.sumOf { it.price * it.qtyPerUnit }
        val newUnitPrice = basePrice + addonPrice

        val newAddonsSnapshot = newAddons.map { addon ->
            mapOf(
                "addonId" to addon.addonId,
                "name" to addon.name,
                "price" to addon.price,
                "qtyPerUnit" to addon.qtyPerUnit
            )
        }

        oldItems[idx] = oldItemMap.toMutableMap().apply {
            this["quantity"] = newQty
            this["price"] = newUnitPrice
            this["note"] = newNote
            this["addonsSnapshot"] = newAddonsSnapshot
            this["name"] = menuSnap.getString("name") ?: (oldItemMap["name"] as? String) ?: targetMenuId
        }

        val total = oldItems.sumOf {
            val p = (it["price"] as? Number)?.toDouble() ?: 0.0
            val q = (it["quantity"] as? Number)?.toDouble() ?: 0.0
            p * q
        }
        val totalRounded = roundMoney(total)
        val service = roundMoney(total * 0.1)

        tx.update(orderRef, mapOf(
            "items" to oldItems,
            "totalPrice" to totalRounded,
            "serviceCharge" to service,
            "updatedAt" to System.currentTimeMillis()
        ))

        null
    }.await()
}

suspend fun createOrderTransactionalMulti(
    customerId: String,
    selectedMenus: List<SelectedMenu>
) {
    val db = FirebaseFirestore.getInstance()
    val orderItems = mutableListOf<Map<String, Any>>()
    val aggregatedNeeds = mutableMapOf<String, Long>()

    for (sel in selectedMenus) {
        val menuId = sel.menuId
        val qty = sel.qty
        val menuDoc = db.collection("menu").document(menuId).get().await()
        if (!menuDoc.exists()) throw IllegalStateException("Menu not found: $menuId")
        val name = menuDoc.getString("name") ?: menuId
        val basePrice = readPrice(menuDoc)
        val recipe = readRecipe(menuDoc.get("recipe"))

        val addonsSnapshot = sel.addons.map { addon ->
            mapOf(
                "addonId" to addon.addonId,
                "name" to addon.name,
                "price" to addon.price,
                "qtyPerUnit" to addon.qtyPerUnit
            )
        }

        val addonUnitTotal = sel.addons.sumOf { it.price * it.qtyPerUnit }
        val unitPrice = basePrice + addonUnitTotal

        val orderItem = hashMapOf<String, Any>(
            "foodId" to menuId,
            "name" to name,
            "price" to unitPrice,
            "quantity" to qty,
            "recipeSnapshot" to recipe,
            "addonsSnapshot" to addonsSnapshot,
            "note" to (sel.note ?: "")
        )
        orderItems.add(orderItem)

        recipe.forEach { (ingId, perUnit) ->
            val need = perUnit * qty.toLong()
            aggregatedNeeds[ingId] = (aggregatedNeeds[ingId] ?: 0L) + need
        }

        sel.addons.forEach { addon ->
            val need = addon.qtyPerUnit.toLong() * qty.toLong()
            aggregatedNeeds[addon.addonId] = (aggregatedNeeds[addon.addonId] ?: 0L) + need
        }
    }

    val orderRef = db.collection("orders").document()
    val incomeRef = db.collection("transactions").document()

    db.runTransaction { tx ->
        val ingSnapsById: Map<String, com.google.firebase.firestore.DocumentSnapshot> =
            aggregatedNeeds.keys.associateWith { ingId ->
                tx.get(db.collection("stock").document(ingId))
            }

        aggregatedNeeds.forEach { (ingId, needTotal) ->
            val snap = ingSnapsById[ingId] ?: throw IllegalStateException("Missing snapshot $ingId")
            val have = (snap.getLong("quantity") ?: 0L)
            if (have < needTotal) throw IllegalStateException("Not enough stock: $ingId Need $needTotal, Existing $have")
        }

        aggregatedNeeds.forEach { (ingId, needTotal) ->
            val snap = ingSnapsById[ingId]!!
            val have = (snap.getLong("quantity") ?: 0L)
            tx.update(db.collection("stock").document(ingId), "quantity", have - needTotal)
        }

        val total = orderItems.sumOf {
            val p = anyToDouble(it["price"]) ?: 0.0
            val q = when (val qv = it["quantity"]) {
                is Number -> qv.toDouble()
                is String -> qv.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            p * q
        }
        val totalRounded = roundMoney(total)
        val orderData = mapOf(
            "customerId" to customerId,
            "items" to orderItems,
            "totalPrice" to totalRounded,
            "serviceCharge" to roundMoney(total * 0.1),
            "status" to "pending",
            "createdAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis(),
            "incomeId" to incomeRef.id
        )
        tx.set(orderRef, orderData)

        val income = mapOf(
            "type" to "income",
            "amount" to totalRounded,
            "category" to "order",
            "description" to "Order ${orderRef.id}",
            "date" to System.currentTimeMillis()
        )
        tx.set(incomeRef, income)
        null
    }.await()
}


suspend fun deleteOrderTransactionalMulti(orderId: String) {
    val db = FirebaseFirestore.getInstance()
    val orderRef = db.collection("orders").document(orderId)
    db.runTransaction { tx ->
        val orderSnap = tx.get(orderRef)
        if (!orderSnap.exists()) throw IllegalStateException("Order not found")
        val incomeId = orderSnap.getString("incomeId")
        val items = orderSnap.get("items") as? List<Map<String, Any>> ?: emptyList()
        val restoreAggregated = mutableMapOf<String, Long>()

        for (item in items) {
            val qty = (item["quantity"] as? Number)?.toInt() ?: 0

            val recipeSnapshot = (item["recipeSnapshot"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = anyToLong(v) ?: return@mapNotNull null
                key to value
            }?.toMap().orEmpty()
            recipeSnapshot.forEach { (ingId, perUnit) ->
                val need = perUnit * qty.toLong()
                restoreAggregated[ingId] = (restoreAggregated[ingId] ?: 0L) + need
            }

            val addonsSnapshot = (item["addonsSnapshot"] as? List<*>) ?: emptyList<Any>()
            addonsSnapshot.forEach { raw ->
                val m = raw as? Map<*, *> ?: return@forEach
                val addonId = m["addonId"] as? String ?: return@forEach
                val perUnit = anyToLong(m["qtyPerUnit"]) ?: 1L
                val need = perUnit * qty.toLong()
                restoreAggregated[addonId] = (restoreAggregated[addonId] ?: 0L) + need
            }
        }

        val ingSnaps = restoreAggregated.keys.associateWith { ingId ->
            tx.get(db.collection("stock").document(ingId))
        }
        restoreAggregated.forEach { (ingId, amount) ->
            val snap = ingSnaps[ingId]!!
            if (!snap.exists()) {
                tx.set(db.collection("stock").document(ingId), mapOf("quantity" to amount))
            } else {
                val have = snap.getLong("quantity") ?: 0L
                tx.update(db.collection("stock").document(ingId), "quantity", have + amount)
            }
        }

        tx.delete(orderRef)
        if (!incomeId.isNullOrBlank()) {
            val incomeRef = db.collection("transactions").document(incomeId)
            tx.delete(incomeRef)
        }
        null
    }.await()
}

fun roundMoney(value: Double): Double =
    BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()

fun anyToLong(v: Any?): Long? = when (v) {
    is Number -> v.toLong()
    is String -> v.toLongOrNull()
    else -> null
}

fun anyToDouble(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

fun readRecipe(map: Any?): Map<String, Long> =
    (map as? Map<*, *>)?.mapNotNull { (k, v) ->
        val id = k as? String ?: return@mapNotNull null
        val need = anyToLong(v) ?: return@mapNotNull null
        id to need
    }?.toMap().orEmpty()

fun readPrice(doc: com.google.firebase.firestore.DocumentSnapshot): Double =
    anyToDouble(doc.get("price")) ?: 0.0
