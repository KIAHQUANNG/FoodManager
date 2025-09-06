package com.group1.foodmanager

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


@Composable
fun StaffPurchasingScreen(   viewModel: StockViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val stockList = viewModel.stockList
    val loading = viewModel.loading

    val showMaterialSelectDialog = viewModel.showMaterialSelectDialog
    val showAddDialog = viewModel.showAddDialog
    val addStockItem = viewModel.addStockItem
    val addQty = viewModel.addQty

    val showAdjustDialog = viewModel.showAdjustDialog
    val adjustStockItem = viewModel.adjustStockItem
    val newStockQty = viewModel.newStockQty
    val adjustmentReason = viewModel.adjustmentReason

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is UiEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("Inventory Management", color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openMaterialSelect() }) {
                Icon(Icons.Default.Add, contentDescription = "Procurement of materials")
            }
        }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
                items(stockList, key = { it.foodId }) { stock ->
                    StockCard(
                        stock = stock,
                        onPurchase = { viewModel.openAddDialog(stock) },
                        onAdjust = { viewModel.openAdjustDialog(stock) }
                    )
                }


                val existingIds = stockList.map { it.foodId }.toSet()
                val availableMaterials = listOf(
                    StockItem(foodId = "bbq_pork", name = "BBQ Pork", price = 10.0),
                )
                val missingMaterials = availableMaterials.filter { it.foodId !in existingIds }

                if (missingMaterials.isNotEmpty()) {
                    item {
                        Text("Unpurchased materials", modifier = Modifier.padding(16.dp))
                    }
                    items(missingMaterials, key = { it.foodId }) { material ->
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { viewModel.selectMaterialToAdd(material) }
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(material.name)
                                    Text("Unit price: ${formatCurrency(material.price)}")
                                }
                                OutlinedButton(onClick = { viewModel.selectMaterialToAdd(material) }) {
                                    Text("Purchase")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMaterialSelectDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.closeMaterialSelect() },
            title = { Text("Select the materials to purchase") },
            text = {
                Column {  }
            },
            confirmButton = { TextButton(onClick = { viewModel.closeMaterialSelect() }) { Text("取消") } }
        )
    }

    if (showAddDialog && addStockItem != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closeAddDialog() },
            title = { Text("Purchase ${addStockItem.name}") },
            text = {
                Column {
                    Text("Unit price: ${formatCurrency(addStockItem.price)}")
                    Spacer(Modifier.height(16.dp))
                    Text("Purchase quantity:")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.decreaseAddQty() }) { Text("-") }
                        Text(addQty.toString(), Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { viewModel.increaseAddQty() }) { Text("+") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Total cost: ${formatCurrency(addStockItem.price * addQty)}")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPurchase() }) { Text("Confirm purchase") }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.closeAddDialog() }) { Text("Cancel") }
            }
        )
    }
    val scrollState = rememberScrollState()
    if (showAdjustDialog && adjustStockItem != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closeAdjustDialog() },
            title = { Text("Adjust inventory - ${adjustStockItem.name}") },
            text = {
                Column (
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ){
                    Text("Current Inventory: ${adjustStockItem.quantity}")
                    Spacer(Modifier.height(16.dp))
                    Text("New inventory quantity:")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.decreaseNewStockQty() }) { Text("-") }
                        Text(newStockQty.toString(), Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { viewModel.increaseNewStockQty() }) { Text("+") }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = viewModel.adjustmentReason,
                        onValueChange = { viewModel.updateAdjustmentReason(it) },
                        label = { Text("Reason for adjustment") },
                        placeholder = { Text("For example: damage, expiration, inventory correction, etc.") },
                        modifier = Modifier.fillMaxWidth() .heightIn(min = 100.dp),maxLines = 5
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmAdjust() }, enabled = adjustmentReason.isNotBlank()) {
                    Text("Confirm the adjustment")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.closeAdjustDialog() }) { Text("Cancel") }
            }
        )
    }
}
@Composable
fun StockCard(
    stock: StockItem,
    onPurchase: () -> Unit,
    onAdjust: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stock.name, style = MaterialTheme.typography.titleMedium)
                    Text("Unit price: ${formatCurrency(stock.price)}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Stock: ${stock.quantity}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (stock.quantity < 10) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary)
                }

                Column(horizontalAlignment = Alignment.End) {
                    OutlinedButton(onClick = onPurchase) {
                        Icon(Icons.Default.Add, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Purchase")
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = onAdjust) {
                        Icon(Icons.Default.Edit, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Adjustment")
                    }
                }
            }
        }
    }
}

suspend fun purchaseStock(item: StockItem, quantity: Int) {
    val db = FirebaseFirestore.getInstance()
    val stockRef = db.collection("stock").document(item.foodId)
    val totalCost = item.price * quantity
    try {
        val finalPrice: Double = db.runTransaction { transaction ->
            val snap = transaction.get(stockRef)
            if (!snap.exists()) {
                val data = hashMapOf<String, Any>(
                    "name" to item.name,
                    "price" to item.price,
                    "quantity" to quantity.toLong()
                )
                transaction.set(stockRef, data)
                item.price
            } else {
                val currentQty = (snap.getLong("quantity") ?: 0L)
                transaction.update(stockRef, "quantity", currentQty + quantity.toLong())
                (snap.get("price") as? Number)?.toDouble() ?: item.price
            }
        }.await()

        val expense = hashMapOf(
            "type" to "expense",
            "amount" to totalCost,
            "category" to "stock_purchase",
            "description" to "purchase ${item.name} x$quantity",
            "date" to System.currentTimeMillis(),
            "itemName" to item.name,
            "itemId" to item.foodId,
            "quantity" to quantity,
            "unitPrice" to item.price
        )
        db.collection("transactions").add(expense).await()

    } catch (e: Exception) {
        Log.e("purchaseStock", "Failed purchase: ${e.message}", e)
        throw e
    }
}

suspend fun adjustStock(item: StockItem, newQty: Int, reason: String) {
    val db = FirebaseFirestore.getInstance()
    val stockRef = db.collection("stock").document(item.foodId)

    try {
        val difference = db.runTransaction { transaction ->
            val snap = transaction.get(stockRef)
            return@runTransaction if (snap.exists()) {
                val oldQty = snap.getLong("quantity") ?: 0L
                val diff = newQty.toLong() - oldQty
                transaction.update(stockRef, "quantity", newQty.coerceAtLeast(0).toLong())
                diff
            } else {
                transaction.set(stockRef, mapOf(
                    "name" to item.name,
                    "price" to item.price,
                    "quantity" to newQty.coerceAtLeast(0).toLong()
                ))
                0L
            }
        }.await()

        if (difference < 0) {
            val lossAmount = item.price * (-difference)
            val lossRecord = hashMapOf(
                "type" to "expense",
                "amount" to lossAmount,
                "category" to "stock_loss",
                "description" to "Inventory loss: ${item.name} x${-difference} - $reason",
                "date" to System.currentTimeMillis(),
                "itemName" to item.name,
                "itemId" to item.foodId,
                "quantity" to -difference,
                "unitPrice" to item.price,
                "reason" to reason
            )
            db.collection("transactions").add(lossRecord).await()
        }

    } catch (e: Exception) {
        Log.e("adjustStock", "Adjusting inventory failed: ${e.message}", e)
        throw e
    }
}
