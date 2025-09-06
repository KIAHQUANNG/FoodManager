package com.group1.foodmanager

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.firebase.auth.FirebaseAuth

@Composable
fun StaffHome(onLogout: () -> Unit,onAccountDeleted: () -> Unit) {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: return

    val items = listOf(
        NavItem("staff_home", "Home", Icons.Default.Home),
        NavItem("staff_purchasing", "Stock", Icons.Default.ShoppingCart),
        NavItem("staff_finance", "Finance", Icons.Default.AttachMoney),
        NavItem("staff_profile", "Profile", Icons.Default.Person)
    )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    Scaffold(
        bottomBar = {
            if (!isLandscape) {
                BottomBar(items = items, navController = navController)
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize()) {
            if (isLandscape) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.primary    ,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    items.forEach { item ->
                        NavigationRailItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    }
                                }
                            },
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
                startDestination = items[0].route,
                modifier = Modifier
                    .padding(padding)
                    .weight(1f)
            ) {
                composable("staff_home") {
                    StaffDashboard(navController = navController, onLogout = onLogout)
                }
                composable("staff_purchasing") {
                    StaffPurchasingScreen()
                }
                composable("staff_finance") {
                    StaffFinanceScreen()
                }
                composable("staff_profile") {
                    UserProfileScreen(uid = uid, isSelf = true, onBack = { navController.popBackStack() },
                    onAccountDeleted = onAccountDeleted,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}