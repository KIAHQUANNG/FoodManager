package com.group1.foodmanager

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination

@Composable
fun StaffDashboard(
    navController: NavController,
    lowStockThreshold: Int = 5,
    onLogout: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var todayIncome by remember { mutableDoubleStateOf(0.0) }
    var monthIncome by remember { mutableDoubleStateOf(0.0) }
    var monthExpense by remember { mutableDoubleStateOf(0.0) }
    var lowStockCount by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    val now = remember { System.currentTimeMillis() }
    val (todayStart, todayEnd) = remember(now) { computeRange("day", now) }
    val (monthStart, monthEnd) = remember(now) { computeRange("month", now) }
    DisposableEffect(db, todayStart, todayEnd, monthStart, monthEnd, lowStockThreshold) {
        loading = true
        val col = db.collection("transactions")

        val todayReg: ListenerRegistration = col
            .whereGreaterThanOrEqualTo("date", todayStart)
            .whereLessThanOrEqualTo("date", todayEnd)
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("StaffDashboard", "today listener error: ${err.message}")
                    loading = false
                    return@addSnapshotListener
                }
                val sum = snap?.documents
                    ?.mapNotNull { d ->
                        val t = d.getString("type")
                        val amt = d.getDouble("amount") ?: d.getLong("amount")?.toDouble() ?: 0.0
                        t?.let { Pair(it, amt) }
                    }
                    ?.filter { it.first == "income" }
                    ?.sumOf { it.second } ?: 0.0
                todayIncome = sum
                loading = false
            }

        val monthReg: ListenerRegistration = col
            .whereGreaterThanOrEqualTo("date", monthStart)
            .whereLessThanOrEqualTo("date", monthEnd)
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("StaffDashboard", "month listener error: ${err.message}")
                    loading = false
                    return@addSnapshotListener
                }
                val docs = snap?.documents ?: emptyList()
                monthIncome = docs.mapNotNull { d ->
                    d.getString("type")?.takeIf { it == "income" }
                        ?.let { d.getDouble("amount") ?: d.getLong("amount")?.toDouble() ?: 0.0 }
                }.sum()
                monthExpense = docs.mapNotNull { d ->
                    d.getString("type")?.takeIf { it == "expense" }
                        ?.let { d.getDouble("amount") ?: d.getLong("amount")?.toDouble() ?: 0.0 }
                }.sum()
                loading = false
            }

        val stockReg: ListenerRegistration = db.collection("stock")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("StaffDashboard", "stock listener error: ${err.message}")
                    loading = false
                    return@addSnapshotListener
                }
                val docs = snap?.documents ?: emptyList()
                lowStockCount =
                    docs.count { (it.getLong("quantity") ?: 0L).toInt() <= lowStockThreshold }
                loading = false
            }

        onDispose {
            try {
                todayReg.remove()
            } catch (_: Exception) {
            }
            try {
                monthReg.remove()
            } catch (_: Exception) {
            }
            try {
                stockReg.remove()
            } catch (_: Exception) {
            }
        }
    }

    val monthNet = monthIncome - monthExpense
    val incomeColor = Color(0xFF2E7D32)
    val expenseColor = Color(0xFFC62828)
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Dashboard", style = MaterialTheme.typography.headlineSmall)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = "Today's Income",
                    value = formatCurrency(todayIncome),
                    valueColor = incomeColor,
                    subtitle = dateMillisToString(todayStart),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            navController.navigate("staff_finance") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                )
                StatCard(
                    title = "This Month",
                    value = formatCurrency(monthNet),
                    valueColor = if (monthNet >= 0) incomeColor else expenseColor,
                    subtitle = "• Income  ${formatCurrency(monthIncome)} \n• Expense ${formatCurrency(monthExpense)}",
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            navController.navigate("staff_finance") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = "Low Stock",
                    value = lowStockCount.toString(),
                    subtitle = "≤ $lowStockThreshold items",
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            navController.navigate("staff_purchasing") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                )

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(IntrinsicSize.Min)
                        .clickable {
                            navController.navigate("staff_profile") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Quick Actions", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Open Profile")
                    }
                }
            }

            Text("Recent transactions", style = MaterialTheme.typography.titleMedium)
            RecentTransactionsPreview()

            Spacer(Modifier.height(24.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    Card(modifier = modifier.height(120.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = valueColor ?: LocalContentColor.current
                )
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
fun RecentTransactionsPreview(limit: Int = 5) {
    val db = remember { FirebaseFirestore.getInstance() }
    var list by remember { mutableStateOf(listOf<TransactionModel>()) }

    DisposableEffect(db, limit) {
        val reg = db.collection("transactions")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("RecentTx", "listen failed: ${err.message}")
                    return@addSnapshotListener
                }
                list = snap?.documents?.mapNotNull { it.toTransactionModel() } ?: emptyList()
            }

        onDispose {
            try { reg.remove() } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (list.isEmpty()) {
            Text("No recent transactions", style = MaterialTheme.typography.bodySmall)
        } else {
            list.forEachIndexed { idx, tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${tx.category} • ${tx.type}", style = MaterialTheme.typography.bodyMedium)
                    Text(formatCurrency(tx.amount), style = MaterialTheme.typography.bodyMedium)
                }
                if (idx < list.size - 1) HorizontalDivider()
            }
        }
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
            createdBy = this.getString("createdBy") ?: ""
        )
    } catch (e: Exception) {
        Log.e("toTransactionModel", "mapping failed: ${e.message}")
        null
    }
}
