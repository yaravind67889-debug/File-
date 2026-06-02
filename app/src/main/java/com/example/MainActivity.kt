package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.MainSharingAppHost
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.OfflineSharingViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: OfflineSharingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var permissionsGranted by remember { mutableStateOf(checkRequiredPermissions()) }

                    // Standard multi-permission launcher
                    val permissionsLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { results ->
                        // Verify all requested are granted
                        val allOk = results.values.all { it }
                        permissionsGranted = allOk
                        if (allOk) {
                            Toast.makeText(this, "Permissions granted! Enjoy sharing offline.", Toast.LENGTH_SHORT).show()
                            viewModel.refreshLocalFiles()
                        } else {
                            Toast.makeText(this, "Some local permissions are missing. Sharing features might run in demo mode.", Toast.LENGTH_LONG).show()
                        }
                    }

                    if (permissionsGranted) {
                        // Render full high-speed file sharing experience host
                        MainSharingAppHost(viewModel = viewModel)
                    } else {
                        // Render extremely elegant permission onboarding view
                        PermissionOnboardingView(
                            onRequestPermissions = {
                                permissionsLauncher.launch(getSharingPermissionsList().toTypedArray())
                            }
                        )
                    }
                }
            }
        }
    }

    // Checking if necessary capabilities are granted
    private fun checkRequiredPermissions(): Boolean {
        return getSharingPermissionsList().all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Evaluates which Android platform keys are needed depending on Build SDK level
    private fun getSharingPermissionsList(): List<String> {
        val permissions = mutableListOf<String>()

        // Core Location permissions needed for network peer search
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        // Bluetooth scanning permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Modern Wifi scanning rules for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        return permissions
    }
}

@Composable
fun PermissionOnboardingView(
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Offline File Sharing needs local Bluetooth, Wi-Fi Direct scanning, and Storage read permission in order to scan, connect, and transfer peer files without the internet.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(48.dp)
        ) {
            Text("Grant Permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
