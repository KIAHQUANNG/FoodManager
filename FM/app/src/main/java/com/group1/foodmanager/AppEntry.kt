package com.group1.foodmanager

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AppEntry() {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val navController = rememberNavController()

    var checking by remember { mutableStateOf(true) }
    var currentRole by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(Unit) {
        val user = auth.currentUser
        if (user != null) {
            try {
                val snapshot = db.collection("users").document(user.uid).get().await()
                currentRole = snapshot.getString("role") ?: "customer"
            } catch (e: Exception) {
                currentRole = "customer"
            }
        }
        checking = false
    }

    if (checking) {
        Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        NavHost(
            navController = navController,
            startDestination = when {
                auth.currentUser == null -> "auth"
                currentRole == "admin" -> "admin"
                currentRole == "staff" -> "staff"
                else -> "customer"
            }
        ) {
            composable("auth") {
                AuthScreen { roleAfterLogin ->
                    currentRole = roleAfterLogin
                    when (roleAfterLogin) {
                        "admin" -> navController.navigate("admin") { popUpTo("auth") { inclusive = true } }
                        "staff" -> navController.navigate("staff") { popUpTo("auth") { inclusive = true } }
                        else -> navController.navigate("customer") { popUpTo("auth") { inclusive = true } }
                    }
                }
            }

            composable("admin") {
                AdminUserManagement(
                    onBack = {
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate("auth") { popUpTo("admin") { inclusive = true } }
                    },
                    onOpenUser = { uid ->
                        navController.navigate("user/$uid")
                    }
                )
            }

            composable("user/{uid}") { backStackEntry ->
                val uid = backStackEntry.arguments?.getString("uid")
                if (uid.isNullOrBlank()) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }
                val isSelf = FirebaseAuth.getInstance().currentUser?.uid == uid
                UserProfileScreen(uid = uid, isSelf = isSelf, onBack = { navController.popBackStack() },
                    onAccountDeleted = {
                    navController.navigate("auth") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                },
                    onLogout = {
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate("auth") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    } )
            }

            composable("staff") {
                StaffHome(
                    onLogout = {
                        FirebaseAuth.getInstance().signOut()
                        currentRole = null
                        navController.navigate("auth") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    },
                    onAccountDeleted = {
                        FirebaseAuth.getInstance().signOut()
                        currentRole = null
                        navController.navigate("auth") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }
                )
            }
            composable("foodDetail/{menuId}") { backStackEntry ->
                val menuId = backStackEntry.arguments?.getString("menuId")
                if (menuId.isNullOrBlank()) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }

                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val uid = FirebaseAuth.getInstance().currentUser?.uid

                var menuItem by remember { mutableStateOf<MenuItem?>(null) }
                var loading by remember { mutableStateOf(true) }
                var errorMessage by remember { mutableStateOf<String?>(null) }

                // 从 Firestore 获取菜品信息
                LaunchedEffect(menuId) {
                    try {
                        val db = FirebaseFirestore.getInstance()
                        val doc = db.collection("menu").document(menuId).get().await()
                        if (doc.exists()) {
                            val name = doc.getString("name") ?: menuId
                            val price = readPrice(doc)
                            val recipe = readRecipe(doc.get("recipe"))
                            val imageName = doc.getString("imageResName")
                            val imageResId = imageName?.let { imgName ->
                                val id = context.resources.getIdentifier(imgName, "drawable", context.packageName)
                                if (id != 0) id else R.drawable.chicken_rice
                            } ?: R.drawable.chicken_rice

                            menuItem = MenuItem(
                                menuId = menuId,
                                name = name,
                                price = price,
                                recipe = recipe,
                                imageResName = imageName,
                                imageResId = imageResId
                            )
                        } else {
                            errorMessage = "Menu item not found"
                        }
                    } catch (e: Exception) {
                        Log.e("FoodDetail", "Failed to load menu item", e)
                        errorMessage = "Failed to load menu item: ${e.message}"
                    } finally {
                        loading = false
                    }
                }

                when {
                    loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    errorMessage != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                                Button(onClick = { navController.popBackStack() }) {
                                    Text("Go Back")
                                }
                            }
                        }
                    }
                    menuItem != null -> {
                        FoodDetailScreen(
                            menuItem = menuItem!!,
                            onPlaceOrder = { qty, addons, note ->
                                if (uid == null) {
                                    Toast.makeText(context, "Please log in first", Toast.LENGTH_SHORT).show()
                                    return@FoodDetailScreen
                                }

                                scope.launch {
                                    try {
                                        val addonObjects = addons.map { name ->
                                            when (name) {
                                                "egg" -> SelectedAddon("egg", "egg", 2.0)
                                                "tofu" -> SelectedAddon("tofu", "tofu", 1.0)
                                                else -> SelectedAddon(name, name, 0.0)
                                            }
                                        }

                                        val selectedMenu = SelectedMenu(
                                            menuId = menuId,
                                            qty = qty,
                                            addons = addonObjects,
                                            note = note
                                        )

                                        createOrderTransactionalMulti(
                                            customerId = uid,
                                            selectedMenus = listOf(selectedMenu)
                                        )

                                        Toast.makeText(context, "Order successfully placed", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    } catch (e: Exception) {
                                        Log.e("FoodDetail", "Place order failed", e)
                                        Toast.makeText(context, "Order failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
            composable("customer") {
                CustomerHome(
                    onOpenFoodDetail = { menuId -> navController.navigate("foodDetail/$menuId") },
                    onLogout = {
                        FirebaseAuth.getInstance().signOut()
                        currentRole = null
                        navController.navigate("auth") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    },
                    onAccountDeleted = {
                        FirebaseAuth.getInstance().signOut()
                        currentRole = null
                        navController.navigate("auth") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}


