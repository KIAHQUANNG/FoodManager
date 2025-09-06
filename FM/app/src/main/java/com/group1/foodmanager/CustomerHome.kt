package com.group1.foodmanager

import android.content.res.Configuration
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


@Composable
fun CustomerHome(
    onOpenFoodDetail: (String) -> Unit,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid

    if (uid == null) {
        LaunchedEffect(Unit) { onLogout() }
        return
    }

    val items = listOf(
        NavItem("customer_home", "Home", Icons.Default.Home),
        NavItem("customer_orders", "Orders", Icons.Default.ShoppingCart),
        NavItem("customer_profile", "Profile", Icons.Default.Person)
    )

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isPortrait) {
                BottomBar(items = items, navController = navController)
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize()) {
            if (!isPortrait) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                NavigationRail(
                    modifier = Modifier.padding(padding),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    items.forEach { item ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == item.route } == true

                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(item.route) {
                                        val startId = navController.graph.findStartDestination().id
                                        popUpTo(startId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                indicatorColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = items.first().route,
                modifier = Modifier
                    .padding(padding)
                    .weight(1f)
            ) {
                composable("customer_home") {
                    CustomerHomeContent(
                        onOpenFoodDetail = onOpenFoodDetail
                    )
                }

                composable("customer_orders") {
                    CustomerOrdersScreen(customerId = uid)
                }

                composable("customer_profile") {
                    UserProfileScreen(
                        uid = uid,
                        isSelf = true,
                        onBack = {},
                        onAccountDeleted = onAccountDeleted,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerHomeContent(
    onOpenFoodDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    var menuList by remember { mutableStateOf(listOf<MenuItem>()) }
    var stockById by remember { mutableStateOf<Map<String, StockItem>>(emptyMap()) }
    var menusLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val menuReg = db.collection("menu")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("CustomerHome", "Listen menu failed: ${err.message}")
                    errorMessage = "Failed to load menu: ${err.message}"
                    menusLoading = false
                    return@addSnapshotListener
                }

                try {
                    val list = snap?.documents?.mapNotNull { doc ->
                        val name = doc.getString("name") ?: doc.id
                        val price = readPrice(doc)
                        val recipe = readRecipe(doc.get("recipe"))
                        val imageName = doc.getString("imageResName")

                        val imageResId = imageName?.let { imgName ->
                            val id = context.resources.getIdentifier(imgName, "drawable", context.packageName)
                            if (id != 0) id else R.drawable.chicken_rice
                        } ?: R.drawable.chicken_rice

                        MenuItem(
                            menuId = doc.id,
                            name = name,
                            price = price,
                            recipe = recipe,
                            imageResName = imageName,
                            imageResId = imageResId
                        )
                    }.orEmpty()

                    menuList = list
                    errorMessage = null
                } catch (e: Exception) {
                    Log.e("CustomerHome", "Error processing menu data", e)
                    errorMessage = "Error processing menu data: ${e.message}"
                } finally {
                    menusLoading = false
                }
            }

        val stockReg = db.collection("stock")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("CustomerHome", "Listen stock failed: ${err.message}")
                    return@addSnapshotListener
                }

                try {
                    val stocks = snap?.documents?.mapNotNull { d ->
                        d.toObject(StockItem::class.java)?.copy(foodId = d.id)
                    } ?: emptyList()
                    stockById = stocks.associateBy { it.foodId }
                } catch (e: Exception) {
                    Log.e("CustomerHome", "Error processing stock data", e)
                }
            }

        onDispose {
            try {
                menuReg.remove()
                stockReg.remove()
            } catch (e: Exception) {
                Log.e("CustomerHome", "Error removing listeners", e)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Customer Home", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        when {
            menusLoading -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            menuList.isEmpty() -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.RestaurantMenu,
                            contentDescription = "No Menu",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No menu items available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(menuList, key = { it.menuId }) { menu ->
                        val available = maxAvailableForMenu(menu, stockById)
                        MenuItemCard(
                            menu = menu,
                            availableQty = available,
                            onOpen = {
                                if (available > 0) {
                                    onOpenFoodDetail(menu.menuId)
                                } else {
                                    Toast.makeText(context, "${menu.name} is sold out", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun MenuItemCard(menu: MenuItem, availableQty: Int, onOpen: () -> Unit) {
    val isAvailable = availableQty > 0
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isAvailable) { onOpen() },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {
            Image(
                painter = painterResource(id = menu.imageResId),
                contentDescription = menu.name,
                modifier = Modifier
                    .height(120.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.padding(12.dp)) {
                Text(menu.name, style = MaterialTheme.typography.titleMedium)
                Text("RM %.2f".format(menu.price), style = MaterialTheme.typography.bodyLarge)

                Spacer(Modifier.height(6.dp))

                if (!isAvailable) {
                    Text(
                        "Sold Out",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Available: $availableQty",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (isAvailable) {
                            onOpen()
                        } else {
                            Toast.makeText(context, "${menu.name} is not available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = isAvailable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isAvailable) "Place Order" else "Not Available")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDetailScreen(
    menuItem: MenuItem,
    orderItem: OrderItem? = null,
    isUpdating: Boolean = false,
    onPlaceOrder: (Int, List<String>, String) -> Unit,
    onUpdateOrder: (Int, List<SelectedAddon>, String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {

    var quantity by rememberSaveable {
        mutableIntStateOf(orderItem?.quantity ?: 1)
    }
    var note by rememberSaveable {
        mutableStateOf(orderItem?.note ?: "")
    }

    val availableAddons = listOf(
        SelectedAddon("egg", "Egg", 2.0, 1),
        SelectedAddon("tofu", "Tofu", 1.0, 1),
        SelectedAddon("vegetables", "Extra Vegetables", 1.5, 1),
        SelectedAddon("sauce", "Extra Sauce", 0.5, 1)
    )

    var selectedAddons by rememberSaveable {
        mutableStateOf(
            if (isUpdating && orderItem != null) {

                orderItem.addonsSnapshot.mapNotNull { addonMap ->
                    val addonId = addonMap["addonId"] as? String ?: return@mapNotNull null
                    val name = addonMap["name"] as? String ?: return@mapNotNull null
                    val price = (addonMap["price"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val qtyPerUnit = (addonMap["qtyPerUnit"] as? Number)?.toInt() ?: 1
                    SelectedAddon(addonId, name, price, qtyPerUnit)
                }.toSet()
            } else {
                setOf<SelectedAddon>()
            }
        )
    }

    val addonPrice = selectedAddons.sumOf { it.price * it.qtyPerUnit }
    val totalPrice = (menuItem.price + addonPrice) * quantity

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isUpdating) "Update Order - ${menuItem.name}" else menuItem.name)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Image(
                painter = painterResource(menuItem.imageResId),
                contentDescription = menuItem.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(12.dp))

            Text(menuItem.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                "RM %.2f".format(totalPrice),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            if (isUpdating && orderItem != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Current Order Details",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Quantity: ${orderItem.quantity}")
                        if (orderItem.addonsSnapshot.isNotEmpty()) {
                            Text("Add-ons: ${orderItem.addonsSnapshot.joinToString(", ") {
                                it["name"] as? String ?: "Unknown"
                            }}")
                        }
                        if (orderItem.note.isNotEmpty()) {
                            Text("Note: ${orderItem.note}")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (availableAddons.isNotEmpty()) {
                Text("Add-ons", style = MaterialTheme.typography.titleMedium)
                availableAddons.forEach { addon ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = selectedAddons.any { it.addonId == addon.addonId },
                            onCheckedChange = { checked ->
                                selectedAddons = if (checked) {
                                    selectedAddons + addon
                                } else {
                                    selectedAddons.filterNot { it.addonId == addon.addonId }.toSet()
                                }
                            }
                        )
                        Text("${addon.name} (+RM %.2f)".format(addon.price * addon.qtyPerUnit))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text("Special Instructions", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("e.g., no spicy, extra sauce, no vegetables") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { if (quantity > 1) quantity-- },
                    enabled = quantity > 1
                ) {
                    Text("-")
                }
                Text(
                    quantity.toString(),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                Button(onClick = { quantity++ }) { Text("+") }
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (quantity > 0) {
                        if (isUpdating) {
                            onUpdateOrder(quantity, selectedAddons.toList(), note.trim())
                        } else {
                            val addonNames = selectedAddons.map { it.addonId }
                            onPlaceOrder(quantity, addonNames, note.trim())
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = quantity > 0
            ) {
                Text(
                    if (isUpdating) "Update Order (RM %.2f)".format(totalPrice)
                    else "Place Order (RM %.2f)".format(totalPrice)
                )
            }
        }
    }
}
