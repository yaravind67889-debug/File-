package com.example.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.HistoryEntity
import com.example.model.*
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningYellow
import com.example.ui.theme.ErrorRed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.viewmodel.OfflineSharingViewModel
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

// Navigation route constant strings
object Routes {
    const val DASHBOARD = "dashboard"
    const val SEND_PICKER = "send_picker"
    const val SEND_DISCOVERY = "send_discovery"
    const val RECEIVE_STANDBY = "receive_standby"
    const val TRANSFER_MANAGER = "transfer_manager"
    const val HISTORY_LOGS = "history_logs"
    const val SETTINGS_PAGE = "settings_page"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSharingAppHost(
    viewModel: OfflineSharingViewModel,
    modifier: Modifier = Modifier
) {
    var currentRoute by remember { mutableStateOf(Routes.DASHBOARD) }
    val context = LocalContext.current

    // Gather states
    val connectionState by viewModel.p2pManager.currentConnectionState.collectAsStateWithLifecycle()
    val activeSession by viewModel.p2pManager.activeSession.collectAsStateWithLifecycle()
    val incomingRequest by viewModel.p2pManager.incomingRequest.collectAsStateWithLifecycle()

    // Safety auto routing
    LaunchedEffect(connectionState, activeSession) {
        if (connectionState == ConnectionState.TRANSFERRING && activeSession != null) {
            currentRoute = Routes.TRANSFER_MANAGER
        } else if (connectionState == ConnectionState.COMPLETED && activeSession?.status == TransferStatus.COMPLETED) {
            currentRoute = Routes.TRANSFER_MANAGER
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Screen router
            AnimatedContent(
                targetState = currentRoute,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "ScreenNavigator"
            ) { route ->
                when (route) {
                    Routes.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigate = { currentRoute = it }
                    )
                    Routes.SEND_PICKER -> FilePickerDialog(
                        viewModel = viewModel,
                        onCancel = { currentRoute = Routes.DASHBOARD },
                        onNext = { currentRoute = Routes.SEND_DISCOVERY }
                    )
                    Routes.SEND_DISCOVERY -> DiscoveryRadarView(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.stopDiscovery()
                            currentRoute = Routes.SEND_PICKER
                        },
                        onNavigateTransfer = { currentRoute = Routes.TRANSFER_MANAGER }
                    )
                    Routes.RECEIVE_STANDBY -> ReceiveWaitingView(
                        viewModel = viewModel,
                        onBack = {
                            viewModel.resetConnectionAndP2P()
                            currentRoute = Routes.DASHBOARD
                        },
                        onNavigateTransfer = { currentRoute = Routes.TRANSFER_MANAGER }
                    )
                    Routes.TRANSFER_MANAGER -> ActiveTransferView(
                        viewModel = viewModel,
                        onDone = {
                            viewModel.resetConnectionAndP2P()
                            currentRoute = Routes.DASHBOARD
                        }
                    )
                    Routes.HISTORY_LOGS -> HistoryExplorerView(
                        viewModel = viewModel,
                        onBack = { currentRoute = Routes.DASHBOARD }
                    )
                    Routes.SETTINGS_PAGE -> AppSettingsView(
                        viewModel = viewModel,
                        onBack = { currentRoute = Routes.DASHBOARD }
                    )
                }
            }

            // Global Incoming File Share Approval Alert Popup Dialog
            incomingRequest?.let { incomingSess ->
                PermissionApprovalDialog(
                    session = incomingSess,
                    onAccept = {
                        viewModel.acceptIncoming()
                        currentRoute = Routes.TRANSFER_MANAGER
                        Toast.makeText(context, "Transfer accepted and starting!", Toast.LENGTH_SHORT).show()
                    },
                    onDecline = {
                        viewModel.declineIncoming()
                        Toast.makeText(context, "Transfer declined", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

// Custom modern Avatar preset graphics
@Composable
fun UserAvatar(
    avatarIndex: Int,
    size: Int = 48,
    modifier: Modifier = Modifier
) {
    val presets = listOf(
        Pair(Icons.Default.Person, Brush.linearGradient(listOf(Color(0xFF818CF8), Color(0xFF4F46E5)))),
        Pair(Icons.Default.AccountBalance, Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669)))),
        Pair(Icons.Default.Anchor, Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF2563EB)))),
        Pair(Icons.Default.Face, Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706)))),
        Pair(Icons.Default.Star, Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFDB2777)))),
        Pair(Icons.Default.Security, Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C3AED))))
    )

    val selected = presets[avatarIndex.coerceIn(0, presets.size - 1)]

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(selected.second)
    ) {
        Icon(
            imageVector = selected.first,
            contentDescription = "Avatar icon",
            tint = Color.White,
            modifier = Modifier.size((size * 0.55f).dp)
        )
    }
}

// HELPER formatters
fun bytesToFormattedSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// -------------------------------------------------------------
// 1. DASHBOARD SCREEN
// -------------------------------------------------------------
@Composable
fun DashboardScreen(
    viewModel: OfflineSharingViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val history by viewModel.transferHistory.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteDevices.collectAsStateWithLifecycle()

    // Space properties
    val totalBytes by viewModel.storageTotalBytes.collectAsStateWithLifecycle()
    val freeBytes by viewModel.storageFreeBytes.collectAsStateWithLifecycle()
    val appUsedBytes by viewModel.storageAppUsedBytes.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Welcome and App Branding Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Offline Share",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = "Device Name logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            IconButton(
                onClick = { onNavigate(Routes.SETTINGS_PAGE) },
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings Icon"
                )
            }
        }

        // Circular Giant Send / Receive Visual Triggers
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // SEND CIRCLE CARD
            Card(
                onClick = { onNavigate(Routes.SEND_PICKER) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .testTag("send_button_main")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send file Icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Send Files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // RECEIVE CIRCLE CARD
            Card(
                onClick = {
                    viewModel.startDiscovery()
                    onNavigate(Routes.RECEIVE_STANDBY)
                },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .testTag("receive_button_main")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Receive icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Receive Files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // STORAGE STATUS VISUALIZER ARCS
        Text(
            text = "Storage Space",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)
        )
        StorageVisualizerCard(
            total = totalBytes,
            free = freeBytes,
            appUsed = appUsedBytes
        )

        Spacer(modifier = Modifier.height(24.dp))

        // History logs header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = "Recent Transfers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = { onNavigate(Routes.HISTORY_LOGS) },
                modifier = Modifier.testTag("view_all_history")
            ) {
                Text("View All")
                Icon(Icons.Default.ChevronRight, contentDescription = "View all link", modifier = Modifier.size(16.dp))
            }
        }

        // Recent transfers list or empty indicator
        if (history.isEmpty()) {
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Empty files icon",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No file transfers yet",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Shared files history will appear here offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    history.take(4).forEach { item ->
                        RecentTransferRow(item, onDelete = { viewModel.deleteHistoryItem(item.id) })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Favorite devices shortcuts
        if (favorites.isNotEmpty()) {
            Text(
                text = "Favorite Devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(favorites) { fav ->
                    Card(
                        onClick = {
                            // Pre-fill device discovery connection
                            Toast.makeText(context, "Ready to send back to ${fav.name}!", Toast.LENGTH_SHORT).show()
                            onNavigate(Routes.SEND_PICKER)
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.width(110.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp).fillMaxWidth()
                        ) {
                            UserAvatar(avatarIndex = fav.avatarIndex, size = 42)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = fav.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Saved", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Storage Visualizer pie Card component
@Composable
fun StorageVisualizerCard(
    total: Long,
    free: Long,
    appUsed: Long,
    modifier: Modifier = Modifier
) {
    val usedBytes = total - free
    val otherUsedBytes = usedBytes - appUsed
    val usedPercent = if (total > 0) usedBytes.toFloat() / total else 0f
    val appPercent = if (total > 0) appUsed.toFloat() / total else 0f
    val freePercent = if (total > 0) free.toFloat() / total else 0f

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            // Draw radial arc pie on canvas
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(90.dp)
                    .padding(end = 12.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val freeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

                Canvas(modifier = Modifier.size(75.dp)) {
                    val strokeWidth = 8.dp.toPx()
                    val sizeDim = size.width - strokeWidth
                    val sizeObj = Size(sizeDim, sizeDim)
                    val offset = strokeWidth / 2f

                    // Draw free space arc starting at 270 (top)
                    drawArc(
                        color = freeColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(offset, offset),
                        size = sizeObj,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )

                    // Draw general android system data used arc
                    val generalSweep = otherUsedBytes.toFloat() / total * 360f
                    drawArc(
                        color = secondaryColor,
                        startAngle = -90f,
                        sweepAngle = generalSweep,
                        useCenter = false,
                        topLeft = Offset(offset, offset),
                        size = sizeObj,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )

                    // Draw our App shared size used arc overlayed
                    val appSweep = appPercent * 360f
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f + generalSweep,
                        sweepAngle = appSweep,
                        useCenter = false,
                        topLeft = Offset(offset, offset),
                        size = sizeObj,
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.0f%%", (usedPercent * 100)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Used",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Legend labels column
            Column(modifier = Modifier.weight(1f)) {
                StorageCategoryRow(
                    label = "Downloads Folder",
                    sizeText = bytesToFormattedSize(appUsed),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                StorageCategoryRow(
                    label = "System & Other",
                    sizeText = bytesToFormattedSize(otherUsedBytes),
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                StorageCategoryRow(
                    label = "Available Free",
                    sizeText = bytesToFormattedSize(free),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )

                Divider(modifier = Modifier.padding(vertical = 10.dp))

                Text(
                    text = "Total Space: ${bytesToFormattedSize(total)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun StorageCategoryRow(
    label: String,
    sizeText: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        }
        Text(sizeText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentTransferRow(
    item: HistoryEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Icon category selector
        val icon = when (item.fileType) {
            "IMAGE" -> Icons.Default.Image
            "VIDEO" -> Icons.Default.Videocam
            "AUDIO" -> Icons.Default.Audiotrack
            "DOCUMENT" -> Icons.Default.Description
            "APK" -> Icons.Default.Android
            "ZIP" -> Icons.Default.FolderZip
            else -> Icons.Default.InsertDriveFile
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = if (item.isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = if (item.isIncoming) SuccessGreen else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = if (item.isIncoming) "Incoming" else "Sent",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "•",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = bytesToFormattedSize(item.fileSize),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Delete small button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete from Log",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


// -------------------------------------------------------------
// 2. FILE PICKER SCREEN
// -------------------------------------------------------------
@Composable
fun FilePickerDialog(
    viewModel: OfflineSharingViewModel,
    onCancel: () -> Unit,
    onNext: () -> Unit
) {
    val localFiles by viewModel.localFiles.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(FileType.IMAGE) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Picker Header with Select Count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Select Files To Share",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${selectedFiles.size} items selected (${bytesToFormattedSize(selectedFiles.sumOf { it.sizeInBytes })})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Tabs to choose categories (Images, Videos, Files, APKs, ZIPs)
        ScrollableTabRow(
            selectedTabIndex = activeTab.ordinal,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab.ordinal]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            FileType.values().forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = {
                        Text(
                            text = tab.name,
                            fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Files container grid list
        val filteredList = localFiles.filter { it.type == activeTab }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (filteredList.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No files found in this category",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (activeTab == FileType.IMAGE || activeTab == FileType.VIDEO) 3 else 1),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList) { shareFile ->
                        val isSelected = selectedFiles.any { it.id == shareFile.id }

                        if (activeTab == FileType.IMAGE || activeTab == FileType.VIDEO) {
                            // Grid block representation for media
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.toggleFileSelection(shareFile) }
                            ) {
                                // Draw mock gradient thumb to avoid blank media box
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (activeTab == FileType.IMAGE) Icons.Default.Image else Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // Selection check overlay
                                if (isSelected) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .align(Alignment.TopEnd)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "checked",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                // Name label footer
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.61f))
                                        .align(Alignment.BottomCenter)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = shareFile.name,
                                        fontSize = 9.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            // Row representation for apks/documents
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleFileSelection(shareFile) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    val iconRep = when (shareFile.type) {
                                        FileType.DOCUMENT -> Icons.Default.Description
                                        FileType.APK -> Icons.Default.Android
                                        FileType.ZIP -> Icons.Default.FolderZip
                                        FileType.AUDIO -> Icons.Default.Audiotrack
                                        else -> Icons.Default.InsertDriveFile
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    ) {
                                        Icon(
                                            imageVector = iconRep,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = shareFile.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(shareFile.extension.uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(shareFile.sizeFormatted, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                    }

                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { viewModel.toggleFileSelection(shareFile) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Float navigation action bottom footer
        Surface(
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        viewModel.startDiscovery()
                        onNext()
                    },
                    enabled = selectedFiles.isNotEmpty(),
                    modifier = Modifier.testTag("send_next_step")
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send (${selectedFiles.size} items)")
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. DISCOVERY RADAR SEARCH VIEW
// -------------------------------------------------------------
@Composable
fun DiscoveryRadarView(
    viewModel: OfflineSharingViewModel,
    onBack: () -> Unit,
    onNavigateTransfer: () -> Unit
) {
    val devices by viewModel.p2pManager.discoveredDevices.collectAsStateWithLifecycle()
    val isRunning by viewModel.p2pManager.isDiscoveryRunning.collectAsStateWithLifecycle()
    val connectionState by viewModel.p2pManager.currentConnectionState.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()

    // Setup pulse expansion animations
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Screen header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Nearby Device Discovery",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = { viewModel.startDiscovery() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Restart scanning")
                }
            }
        }

        // Connect alert message
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ensure the receiving device also has 'Receive Files' screen open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Discovery states
        if (connectionState == ConnectionState.PAIRING || connectionState == ConnectionState.CONNECTING) {
            // Display clean loading indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (connectionState == ConnectionState.PAIRING) "Pairing devices..." else "Establishing link...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Exchanging security keys...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            // Standard peer matching with Radar visual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                // Radar Ripple waves on custom Canvas
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = size.width.coerceAtMost(size.height) / 2

                    // 3 expanding pulsing rings
                    for (i in 0..2) {
                        val pulse = (pulseProgress + i / 3f) % 1f
                        drawCircle(
                            color = primaryColor.copy(alpha = (1f - pulse) * 0.25f),
                            radius = pulse * maxRadius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // Static concentric division rings
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.08f),
                        radius = maxRadius * 0.33f,
                        center = center
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.05f),
                        radius = maxRadius * 0.66f,
                        center = center
                    )
                }

                // Centered pulsating local device icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(primaryColor, secondaryColor)
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = "Radar Central Transmitter",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Text(
                text = "Scanning for offline receivers...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Discovered nearby peers scroll list
            Text(
                text = "Discovered Devices (${devices.size})",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            )

            if (devices.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Searching...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(devices) { peer ->
                        Card(
                            onClick = {
                                viewModel.connectAndSend(peer)
                                onNavigateTransfer()
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().testTag("connect_peer_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                UserAvatar(avatarIndex = peer.avatarIndex, size = 44)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = peer.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = peer.connectionType.name.replace("_", " "),
                                                fontSize = 8.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.SignalWifiStatusbar4Bar,
                                            contentDescription = "Signal StrengthIndicator",
                                            tint = if (peer.signalStrength >= 3) SuccessGreen else WarningYellow,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(1.dp))
                                        Text(
                                            text = "${peer.signalStrength}/4 Signal",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                // Quick favorite heart toggle
                                IconButton(onClick = { viewModel.toggleDeviceFavorite(peer.id, peer.name, peer.avatarIndex) }) {
                                    Icon(
                                        imageVector = if (peer.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite status icon",
                                        tint = if (peer.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }

                                Button(
                                    onClick = {
                                        viewModel.connectAndSend(peer)
                                        onNavigateTransfer()
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("send_connect_inline")
                                ) {
                                    Text("Send", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. RECEIVE WAITING STANDBY SCREEN
// -------------------------------------------------------------
@Composable
fun ReceiveWaitingView(
    viewModel: OfflineSharingViewModel,
    onBack: () -> Unit,
    onNavigateTransfer: () -> Unit
) {
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val isRunning by viewModel.p2pManager.isDiscoveryRunning.collectAsStateWithLifecycle()

    // Build unique demo dynamic pairing credentials
    val pairingCode = "729 451"

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Receiver Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Receive Files Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large high-contrast Vector QR Code pairer drawn on custom canvas container
        Text(
            text = "Scan QR Code to Pair Instant Link",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tell sender to scan this pattern matching credentials",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.size(200.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Vector Canvas drawing QR Code squares & position finders
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sizePt = size.width
                    val pxCell = sizePt / 8f

                    val qrColor = Color(0xFF090A0F)

                    // 1. Top-Left finder square
                    drawRect(color = qrColor, topLeft = Offset(0f, 0f), size = Size(pxCell * 2.5f, pxCell * 2.5f))
                    drawRect(color = Color.White, topLeft = Offset(pxCell * 0.4f, pxCell * 0.4f), size = Size(pxCell * 1.7f, pxCell * 1.7f))
                    drawRect(color = qrColor, topLeft = Offset(pxCell * 0.7f, pxCell * 0.7f), size = Size(pxCell * 1.1f, pxCell * 1.1f))

                    // 2. Top-Right finder square
                    drawRect(color = qrColor, topLeft = Offset(sizePt - pxCell * 2.5f, 0f), size = Size(pxCell * 2.5f, pxCell * 2.5f))
                    drawRect(color = Color.White, topLeft = Offset(sizePt - pxCell * 2.1f, pxCell * 0.4f), size = Size(pxCell * 1.7f, pxCell * 1.7f))
                    drawRect(color = qrColor, topLeft = Offset(sizePt - pxCell * 1.8f, pxCell * 0.7f), size = Size(pxCell * 1.1f, pxCell * 1.1f))

                    // 3. Bottom-Left finder square
                    drawRect(color = qrColor, topLeft = Offset(0f, sizePt - pxCell * 2.5f), size = Size(pxCell * 2.5f, pxCell * 2.5f))
                    drawRect(color = Color.White, topLeft = Offset(pxCell * 0.4f, sizePt - pxCell * 2.1f), size = Size(pxCell * 1.7f, pxCell * 1.7f))
                    drawRect(color = qrColor, topLeft = Offset(pxCell * 0.7f, sizePt - pxCell * 1.8f), size = Size(pxCell * 1.1f, pxCell * 1.1f))

                    // 4. Center QR noise dots
                    drawRect(color = qrColor, topLeft = Offset(pxCell * 3.5f, pxCell * 1f), size = Size(pxCell * 0.8f, pxCell * 0.8f))
                    drawRect(color = qrColor, topLeft = Offset(pxCell * 4.5f, pxCell * 2.5f), size = Size(pxCell * 0.8f, pxCell * 1.6f))
                    drawRect(color = qrColor, topLeft = Offset(pxCell * 1f, pxCell * 4.5f), size = Size(pxCell * 1.6f, pxCell * 0.8f))
                    drawRect(color = qrColor, topLeft = Offset(pxCell * 3.5f, pxCell * 4f), size = Size(pxCell * 0.8f, pxCell * 0.8f))
                    drawRect(color = qrColor, topLeft = Offset(pxCell * 5f, pxCell * 5f), size = Size(pxCell * 1.2f, pxCell * 1.2f))
                    drawRect(color = qrColor, topLeft = Offset(pxCell * 4f, pxCell * 6.5f), size = Size(pxCell * 1.5f, pxCell * 0.8f))
                    drawRect(color = qrColor, topLeft = Offset(pxCell * 6.5f, pxCell * 6.2f), size = Size(pxCell * 0.8f, pxCell * 1.1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Visual numeric pairings credentials block
        Text(
            text = "6-Digit Pairing Verification Code",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = pairingCode,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp).testTag("pairing_code_display")
        )

        Divider(modifier = Modifier.padding(horizontal = 40.dp))

        Spacer(modifier = Modifier.height(20.dp))

        // Listening local name banner info
        CircularProgressIndicator(modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Listening offline as \"$deviceName\"",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Allows others nearby to scan and detect you on Wi-Fi Direct, Bluetooth & Hotspots.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(bottom = 24.dp).testTag("stop_receiving_button")
        ) {
            Text("Stop Receiving")
        }
    }
}

// -------------------------------------------------------------
// 5. ACTIVE TRANSFER SCREEN & SPEED SUMMARY
// -------------------------------------------------------------
@Composable
fun ActiveTransferView(
    viewModel: OfflineSharingViewModel,
    onDone: () -> Unit
) {
    val activeSession by viewModel.p2pManager.activeSession.collectAsStateWithLifecycle()

    if (activeSession == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active session.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onDone) { Text("Back To Safety") }
            }
        }
        return
    }

    val session = activeSession!!
    val isComplete = session.status == TransferStatus.COMPLETED
    val isPaused = session.status == TransferStatus.PAUSED
    val isCancelled = session.status == TransferStatus.CANCELLED || session.status == TransferStatus.FAILED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Status Row Label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isComplete -> SuccessGreen.copy(alpha = 0.15f)
                            isPaused -> WarningYellow.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when {
                        isComplete -> "Completed"
                        isPaused -> "Paused"
                        isCancelled -> "Failed"
                        else -> "Transferring"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isComplete -> SuccessGreen
                        isPaused -> WarningYellow
                        isCancelled -> ErrorRed
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.testTag("transfer_status_label")
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isComplete) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.testTag("transfer_done_button")
                ) {
                    Text("Go Home")
                }
            } else {
                TextButton(
                    onClick = { viewModel.cancelActiveTransfer() },
                    modifier = Modifier.testTag("transfer_cancel_button")
                ) {
                    Text("Cancel Share", color = ErrorRed)
                }
            }
        }

        // Card displaying sender info and totals
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                UserAvatar(avatarIndex = session.deviceAvatarIndex, size = 52)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (session.isIncoming) "Incoming Share from" else "Sending Files to",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = session.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Via offline ${session.connectionType.name.replace("_", " ")}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Large Speed / Progress details circular visualization
        Text(
            text = "Progress Tracker",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Big Transfer rate speed or completed indicator
                Text(
                    text = if (isComplete) "Transferred Successfully!" else session.speedFormatted,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("transfer_speed_numeric")
                )

                Text(
                    text = if (isComplete) "All files fully saved in downloads" else "Remaining: ${session.timeRemainingFormatted}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Progress Bar slider
                LinearProgressIndicator(
                    progress = session.progress,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape)
                        .testTag("transfer_progress_bar")
                )

                // Percentage and files sum row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%.1f %%", session.progress * 100),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${bytesToFormattedSize(session.bytesTransferred)} / ${bytesToFormattedSize(session.totalBytes)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Pause / Resume buttons if not completed
                if (!isComplete) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 20.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isPaused) viewModel.resumeActiveTransfer() else viewModel.pauseActiveTransfer()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPaused) SuccessGreen else MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.testTag("transfer_pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (isPaused) "Resume" else "Pause")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Shared item lists inside session checkpoint
        Text(
            text = "Files Checklist (${session.files.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(session.files) { fUpdate ->
                val completeness = fUpdate.bytesTransferred.toFloat() / fUpdate.totalSize

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = if (fUpdate.status == TransferStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (fUpdate.status == TransferStatus.COMPLETED) SuccessGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                fUpdate.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(bytesToFormattedSize(fUpdate.totalSize), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                if (fUpdate.status == TransferStatus.IN_PROGRESS) {
                                    Text(
                                        text = String.format("%.0f%%", completeness * 100),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (fUpdate.status == TransferStatus.IN_PROGRESS) {
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = completeness,
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. DETAILED TRANSFER HISTORY / DOWNLOADS SCREEN
// -------------------------------------------------------------
@Composable
fun HistoryExplorerView(
    viewModel: OfflineSharingViewModel,
    onBack: () -> Unit
) {
    val searchVal by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredList by viewModel.filteredHistory.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Transfer History & History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Clear Log Button
            TextButton(
                onClick = {
                    viewModel.clearHistory()
                    Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.testTag("clear_history_log")
            ) {
                Text("Clear All")
            }
        }

        // Search text input bar
        OutlinedTextField(
            value = searchVal,
            onValueChange = { viewModel.setSearchQuery(it) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "search icon")
            },
            trailingIcon = if (searchVal.isNotEmpty()) {
                {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Cancel, contentDescription = "clear text")
                    }
                }
            } else null,
            placeholder = { Text("Search by filename or device name") },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .testTag("history_search_input")
        )

        // Historical view list
        if (filteredList.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FindInPage,
                        contentDescription = "empty check",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No match logs found",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(filteredList) { historyItem ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            // Render avatar category icon
                            val itemIcon = when (historyItem.fileType) {
                                "IMAGE" -> Icons.Default.Image
                                "VIDEO" -> Icons.Default.Videocam
                                "AUDIO" -> Icons.Default.Audiotrack
                                "DOCUMENT" -> Icons.Default.Description
                                "APK" -> Icons.Default.Android
                                "ZIP" -> Icons.Default.FolderZip
                                else -> Icons.Default.InsertDriveFile
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = itemIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = historyItem.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (historyItem.isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (historyItem.isIncoming) SuccessGreen else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = if (historyItem.isIncoming) "From: ${historyItem.deviceName}" else "To: ${historyItem.deviceName}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = bytesToFormattedSize(historyItem.fileSize),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            IconButton(onClick = { viewModel.deleteHistoryItem(historyItem.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete record",
                                    tint = ErrorRed.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 7. APP CONFIGURATION SETTINGS SCREEN
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsView(
    viewModel: OfflineSharingViewModel,
    onBack: () -> Unit
) {
    val darkByPref by viewModel.darkMode.collectAsStateWithLifecycle()
    val appCustomName by viewModel.deviceName.collectAsStateWithLifecycle()
    val autoAccept by viewModel.autoAcceptTransfers.collectAsStateWithLifecycle()
    val storageLoc by viewModel.storageLocationLabel.collectAsStateWithLifecycle()
    val language by viewModel.currentLanguage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var editingName by remember { mutableStateOf(appCustomName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Settings page Header ROW
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Application Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Rename Device Section
        Text(
            text = "Device Identification",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = editingName,
            onValueChange = { editingName = it },
            label = { Text("Display Name") },
            placeholder = { Text("Your Offline name...") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    viewModel.setDeviceName(editingName)
                    Toast.makeText(context, "Custom display identity set!", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "save name change")
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .testTag("device_name_field")
        )

        // General settings list block options
        Text(
            text = "Preferences",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Dark Mode Toggle row selection
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(4.dp)) {
                ListItem(
                    headlineContent = { Text("Dark Theme Mode") },
                    supportingContent = { Text("Enforces energy-saving eye protection midnight layout") },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.Brightness4, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = darkByPref,
                            onCheckedChange = { viewModel.setDarkMode(it) },
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }
                )

                Divider()

                // Auto accept transfers toggle row select
                ListItem(
                    headlineContent = { Text("Auto Accept Shares") },
                    supportingContent = { Text("Downloads files automatically from favorites without prompting") },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.Verified, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = autoAccept,
                            onCheckedChange = { viewModel.setAutoAcceptTransfers(it) },
                            modifier = Modifier.testTag("auto_accept_switch")
                        )
                    }
                )

                Divider()

                // Storage location selector read
                ListItem(
                    headlineContent = { Text("Storage Location Path") },
                    supportingContent = { Text(storageLoc) },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            viewModel.setStorageLocation("Internal Storage/Downloads/OfflineSharing")
                            Toast.makeText(context, "Location updated", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit position")
                        }
                    }
                )

                Divider()

                // Default matching Language selector list
                ListItem(
                    headlineContent = { Text("Default Matching Language") },
                    supportingContent = { Text(language) },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.Language, contentDescription = null)
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            viewModel.setLanguage(if (language == "English") "Spanish" else "English")
                            Toast.makeText(context, "Language cycle updated!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = "Language toggle link")
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // System Network status informational card panel
        Text(
            text = "Device Channels & Capabilities",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CapabilityRow(label = "Wi-Fi Direct Peer Link", active = true)
                CapabilityRow(label = "Bluetooth Discovery / Scan", active = true)
                CapabilityRow(label = "QR Local Handshake Router", active = true)
                CapabilityRow(label = "Local Hotspot Ad-hoc Network", active = true)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "App Version: 1.0.0 (Production Clean Build)",
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CapabilityRow(label: String, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (active) SuccessGreen else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

// -------------------------------------------------------------
// 8. SECURITY TRANSFERS APPROVAL POPUP MODAL
// -------------------------------------------------------------
@Composable
fun PermissionApprovalDialog(
    session: TransferSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Shield popup icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connection Request")
            }
        },
        text = {
            Column {
                Text(
                    text = "${session.deviceName} is trying to share files with you over offline ${session.connectionType.name.replace("_", " ")}.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Request Checklist (${session.files.size} items):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.heightIn(max = 140.dp)
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(session.files) { fUp ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = fUp.name,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(bytesToFormattedSize(fUp.totalSize), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Total Payload Size:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = bytesToFormattedSize(session.totalBytes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.testTag("dialog_accept")
            ) {
                Text("Accept Transfer")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.testTag("dialog_decline")
            ) {
                Text("Decline")
            }
        }
    )
}
