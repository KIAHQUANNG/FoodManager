package com.group1.foodmanager

import android.app.DatePickerDialog
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*


data class TransactionModel(
    val id: String = "",
    val type: String = "expense",
    val amount: Double = 0.0,
    val category: String = "",
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    val createdBy: String? = null
)


@Composable
fun StaffFinanceScreen() {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    var transactions by remember { mutableStateOf(listOf<TransactionModel>()) }
    var loading by remember { mutableStateOf(true) }

    var filterMode by rememberSaveable  { mutableStateOf("day") }
    var selectedDateMillis by rememberSaveable  { mutableLongStateOf(System.currentTimeMillis()) }

    var showAddDialog by rememberSaveable  { mutableStateOf(false) }
    var editTransaction by rememberSaveable(stateSaver = TransactionModelSaver) { val mutableStateOf =
        mutableStateOf<TransactionModel?>(null)
        mutableStateOf
    }

    DisposableEffect(filterMode, selectedDateMillis) {
        loading = true
        val (start, end) = computeRange(filterMode, selectedDateMillis)
        val col = db.collection("transactions")

        val registration: ListenerRegistration = if (filterMode == "all") {
            col.orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.e("StaffFinance", "listen failed: ${err.message}")
                        loading = false
                        return@addSnapshotListener
                    }
                    transactions = snap?.documents?.mapNotNull { it.toTransactionModel() } ?: emptyList()
                    loading = false
                }
        } else {
            col.whereGreaterThanOrEqualTo("date", start)
                .whereLessThanOrEqualTo("date", end)
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.e("StaffFinance", "listen failed: ${err.message}")
                        loading = false
                        return@addSnapshotListener
                    }
                    transactions = snap?.documents?.mapNotNull { it.toTransactionModel() } ?: emptyList()
                    loading = false
                }
        }

        onDispose {
            try {
                registration.remove()
            } catch (e: Exception) {
                Log.w("StaffFinance", "failed to remove listener: ${e.message}")
            }
        }
    }

    val totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
    val netProfit = totalIncome - totalExpense

    Scaffold(
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("Finance Transaction", color = MaterialTheme.colorScheme.onPrimary, fontSize = 22.sp)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item{
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedFilter(selected = filterMode, onChange = { filterMode = it })
                DatePickerButton(
                    dateMillis = selectedDateMillis,
                    onDateSelected = { selectedDateMillis = it })
            } }
            item{
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Summary", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row {
                            Text(
                                "Income: ",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF2E7D32),
                                        fontSize = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatCurrency(totalIncome),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                fontSize = 18.sp,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                        Row {
                            Text(
                                "Expense: ",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFC62828),
                                fontSize = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatCurrency(totalExpense),
                                fontSize = 18.sp,
                                color = Color(0xFFC62828),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                        Row {
                            Text(
                                "Net: ",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (netProfit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontSize = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatCurrency(netProfit),
                                fontSize = 18.sp,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (netProfit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )

                    }}
                }
            }}

            if (loading) {
                item {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }} else {
                if (transactions.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            Text("No transactions found")
                        }
                    }
                } else {
                    items(items = transactions, key = { it.id }) { tx ->
                        TransactionCard(
                            tx,
                            onEdit = { editTransaction = it },
                            onDelete = { toDelete ->
                                if (toDelete.id.isNotBlank()) {
                                    deleteTransaction(
                                        toDelete.id,
                                        onSuccess = {
                                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { msg ->
                                            Toast.makeText(context, "Delete failed: $msg", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                } else {
                                    Toast.makeText(context, "Delete failed: invalid id", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }}
            }
        }


    if (showAddDialog) {
        TransactionEditDialog(
            initial = null,
            onConfirm = { type, amt, cat, desc, dateMillis ->
                addTransaction(
                    type, amt, cat, desc, dateMillis, auth.currentUser?.uid,
                    onSuccess = {
                        Toast.makeText(context, "Added", Toast.LENGTH_SHORT).show()
                    },
                    onError = { msg ->
                        Toast.makeText(context, "Add failed: $msg", Toast.LENGTH_LONG).show()
                    }
                )
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (editTransaction != null) {
        val txToEdit = editTransaction
        TransactionEditDialog(
            initial = txToEdit,
            onConfirm = { type, amt, cat, desc, dateMillis ->
                val id = txToEdit?.id
                if (id.isNullOrBlank()) {
                    Toast.makeText(context, "Update failed: invalid id", Toast.LENGTH_LONG).show()
                } else {
                    updateTransaction(
                        id, type, amt, cat, desc, dateMillis,
                        onSuccess = {
                            Toast.makeText(context, "Updated", Toast.LENGTH_SHORT).show()
                        },
                        onError = { msg ->
                            Toast.makeText(context, "Update failed: $msg", Toast.LENGTH_LONG).show()
                        }
                    )
                }
                editTransaction = null
            },
            onDismiss = { editTransaction = null }
        )
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toTransactionModel(): TransactionModel? {
    return try {
        TransactionModel(
            id = this.id,
            type = (this.getString("type") ?: "expense"),
            amount = (this.getDouble("amount") ?: (this.getLong("amount")?.toDouble() ?: 0.0)),
            category = (this.getString("category") ?: ""),
            description = (this.getString("description") ?: ""),
            date = (this.getLong("date") ?: 0L),
            createdBy = this.getString("createdBy")
        )
    } catch (e: Exception) {
        null
    }
}

@Composable
fun TransactionCard(tx: TransactionModel, onEdit: (TransactionModel) -> Unit, onDelete: (TransactionModel) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${tx.category} • ${tx.type.uppercase()}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    tx.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(dateMillisToString(tx.date), style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatCurrency(tx.amount), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Row {
                    IconButton(onClick = { onEdit(tx) }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { onDelete(tx) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            }
        }
    }
}

@Composable
fun TransactionEditDialog(
    initial: TransactionModel?,
    onConfirm: (type: String, amount: Double, category: String, description: String, dateMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf(initial?.type ?: "income") }
    var amountText by rememberSaveable { mutableStateOf(initial?.amount?.toString() ?: "") }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: "") }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var dateMillis by rememberSaveable { mutableLongStateOf(initial?.date ?: System.currentTimeMillis()) }

    val dateStr = dateMillisToString(dateMillis)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Transaction" else "Edit Transaction") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row {
                        SelectableButton(
                            text = "Income",
                            selected = (type == "income"),
                            onClick = { type = "income" },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        SelectableButton(
                            text = "Expense",
                            selected = (type == "expense"),
                            onClick = { type = "expense" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val c2 = Calendar.getInstance()
                                c2.set(y, m, d, 12, 0, 0)
                                dateMillis = c2.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Text("Date: $dateStr")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amountText.toDoubleOrNull() ?: 0.0
                if (amt <= 0.0) {
                    Toast.makeText(context, "Please enter valid amount!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                onConfirm(type, amt, category, description, dateMillis)
            }) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun addTransaction(
    type: String,
    amount: Double,
    category: String,
    description: String,
    dateMillis: Long,
    createdBy: String?,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val uid = createdBy ?: FirebaseAuth.getInstance().currentUser?.uid

    val map = hashMapOf<String, Any?>(
        "type" to type,
        "amount" to amount,
        "category" to category,
        "description" to description,
        "date" to dateMillis,
        "createdBy" to uid
    )

    db.collection("transactions")
        .add(map)
        .addOnSuccessListener { docRef ->
            Log.d("StaffFinance", "addTransaction: success id=${docRef.id}")
            onSuccess()
        }
        .addOnFailureListener { e ->
            Log.e("StaffFinance", "addTransaction: failed", e)
            onError(e.message ?: "Unknown error")
        }
}

fun updateTransaction(
    id: String,
    type: String,
    amount: Double,
    category: String,
    description: String,
    dateMillis: Long,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val map = mapOf<String, Any>(
        "type" to type,
        "amount" to amount,
        "category" to category,
        "description" to description,
        "date" to dateMillis
    )

    db.collection("transactions").document(id)
        .update(map)
        .addOnSuccessListener {
            Log.d("StaffFinance", "updateTransaction: success id=$id")
            onSuccess()
        }
        .addOnFailureListener { e ->
            Log.e("StaffFinance", "updateTransaction: failed id=$id", e)
            onError(e.message ?: "Unknown error")
        }
}

fun deleteTransaction(
    id: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    db.collection("transactions").document(id)
        .delete()
        .addOnSuccessListener {
            Log.d("StaffFinance", "deleteTransaction: success id=$id")
            onSuccess()
        }
        .addOnFailureListener { e ->
            Log.e("StaffFinance", "deleteTransaction: failed id=$id", e)
            onError(e.message ?: "Unknown error")
        }
}

fun dateMillisToString(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return fmt.format(Date(millis))
}

fun formatCurrency(amount: Double): String {
    return try {
        val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
        nf.format(amount)
    } catch (e: Exception) {
        String.format(Locale.getDefault(), "%.2f", amount)
    }
}

fun computeRange(mode: String, dateMillis: Long): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.timeInMillis = dateMillis
    return when (mode) {
        "day" -> {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val end = cal.timeInMillis - 1
            start to end
        }
        "month" -> {
            cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            val end = cal.timeInMillis - 1
            start to end
        }
        else -> Pair(0L, Long.MAX_VALUE)
    }
}

@Composable
fun SegmentedFilter(selected: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val options = listOf("day" to "Day", "month" to "Month", "all" to "All")
        options.forEach { (value, label) ->
            val isSelected = value == selected
            SelectableButton(
                text = label,
                selected = isSelected,
                onClick = { onChange(value) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DatePickerButton(
    dateMillis: Long,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val dateStr = dateMillisToString(dateMillis)

    Button(
        onClick = {
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            DatePickerDialog(ctx, { _, y, m, d ->
                val c2 = Calendar.getInstance()
                c2.set(y, m, d, 12, 0, 0)
                onDateSelected(c2.timeInMillis)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar")
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = dateStr,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

@Composable
fun SelectableButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .padding(2.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

val TransactionModelSaver = Saver<TransactionModel?, Map<String, Any?>>(
    save = { tx ->
        tx?.let {
            mapOf(
                "id" to it.id,
                "type" to it.type,
                "amount" to it.amount,
                "category" to it.category,
                "description" to it.description,
                "date" to it.date,
                "createdBy" to it.createdBy
            )
        }
    },
    restore = { map ->
        map.let {
            TransactionModel(
                id = it["id"] as String,
                type = it["type"] as String,
                amount = it["amount"] as Double,
                category = it["category"] as String,
                description = it["description"] as String,
                date = it["date"] as Long,
                createdBy = it["createdBy"] as String?
            )
        }
    }
)

