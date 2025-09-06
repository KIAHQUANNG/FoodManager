@file:OptIn(ExperimentalMaterial3Api::class)
package com.group1.foodmanager

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException

@Composable
fun UserProfileScreen(
    uid: String,
    isSelf: Boolean,
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    var email by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("customer") }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }

    var currentPassword by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uid) {
        try {
            val snap = db.collection("users").document(uid).get().await()

            if (email.isBlank()) {
                email = snap.getString("email") ?: ""
            }
            if (role.isBlank()) {
                role = snap.getString("role") ?: "customer"
            }
            if (name.isBlank()) {
                name = snap.getString("name") ?: ""
            }
            if (phone.isBlank()) {
                phone = snap.getString("phone") ?: ""
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to read data: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                OutlinedTextField(
                    value = email,
                    onValueChange = { },
                    label = { Text("Email (Log in to your email)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = false
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Phone
                    )
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                saveProfileSuspend(
                                    uid = uid,
                                    email = email,
                                    name = name,
                                    phone = phone,
                                    db = db
                                )
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving && !deleting
                ) {
                    if (saving) CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    else Text("Save")
                }

                if (isSelf) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Confirm password (required to delete account)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                deleting = true
                                try {
                                    selfDeleteAuthAndDocSuspend(
                                        auth = auth,
                                        db = db,
                                        password = currentPassword
                                    )
                                    Toast.makeText(context, "Account deleted", Toast.LENGTH_LONG).show()
                                    onAccountDeleted()

                                } catch (e: Exception) {
                                    val msg = when (e) {
                                        is FirebaseAuthRecentLoginRequiredException ->
                                            "Please log in again or enter the correct password and try again"
                                        else -> e.message ?: "Deletion failed"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                } finally {
                                    deleting = false
                                }
                            }
                        },
                        enabled = !saving && !deleting && currentPassword.isNotBlank()
                    ) {
                        if (deleting) CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        else Text("Delete My Account")
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }

            }
        }
    }
}
private suspend fun saveProfileSuspend(
    uid: String,
    email: String,
    name: String,
    phone: String,
    db: FirebaseFirestore
) = withContext(Dispatchers.IO) {
    val updateMap = mutableMapOf<String, Any>(
        "email" to email,
        "name" to name,
        "phone" to phone
    )
    db.collection("users").document(uid).update(updateMap).await()
}


private suspend fun selfDeleteAuthAndDocSuspend(
    auth: FirebaseAuth,
    db: FirebaseFirestore,
    password: String
) = withContext(Dispatchers.IO) {
    val user = auth.currentUser ?: throw IllegalStateException("No current user")
    val email = user.email ?: throw IllegalStateException("User has no email")

    val credential = EmailAuthProvider.getCredential(email, password)
    user.reauthenticate(credential).await()

    db.collection("users").document(user.uid).delete().await()

    user.delete().await()
}
