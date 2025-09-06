package com.group1.foodmanager

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.remember
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton

data class UserModel(
    val uid: String = "",
    val email: String = "",
    val role: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserManagement(onBack: () -> Unit, onOpenUser: (String) -> Unit){
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    var users by remember { mutableStateOf(listOf<UserModel>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val snapshot = db.collection("users").get().await()
            users = snapshot.documents.mapNotNull { doc ->
                val email = doc.getString("email") ?: return@mapNotNull null
                val role = doc.getString("role") ?: "customer"
                UserModel(uid = doc.id, email = email, role = role)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load user: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Management (Admin Only)") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                LazyColumn {
                    items(users) { user ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onOpenUser(user.uid) },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Email: ${user.email}", style = MaterialTheme.typography.bodyLarge)
                                Text("Current Role: ${user.role}", style = MaterialTheme.typography.bodyMedium)

                                Spacer(Modifier.height(8.dp))

                                Row {
                                    OutlinedButton(
                                        onClick = {
                                            updateUserRole(db, user.uid, "customer") {
                                                Toast.makeText(context, "Changed to Customer", Toast.LENGTH_SHORT).show()
                                                users = users.map { if (it.uid == user.uid) it.copy(role = "customer") else it }
                                            }
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text("Set Customer")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            updateUserRole(db, user.uid, "staff") {
                                                Toast.makeText(context, "Changed to Staff", Toast.LENGTH_SHORT).show()
                                                users = users.map { if (it.uid == user.uid) it.copy(role = "staff") else it }
                                            }
                                        }
                                    ) {
                                        Text("Set Staff")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun updateUserRole(
    db: FirebaseFirestore,
    uid: String,
    newRole: String,
    onSuccess: () -> Unit
) {
    db.collection("users").document(uid).get()
        .addOnSuccessListener { doc ->
            val currentRole = doc.getString("role") ?: "customer"

            if (currentRole == "admin") {
                Log.w("updateUserRole", "The admin role cannot be modified")
                return@addOnSuccessListener
            }

            if (newRole == "customer" || newRole == "staff") {
                db.collection("users").document(uid)
                    .update("role", newRole)
                    .addOnSuccessListener { onSuccess() }
            }
        }
}