package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import com.example.data.local.model.OrderWithItems
import com.example.ui.components.BarcodeScannerModal
import com.example.ui.components.PrinterDeviceDialog
import com.example.ui.components.RackAssignmentDialog
import com.example.ui.components.SettlementDialog
import com.example.ui.components.SyncQueueDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DriverViewModel
import com.example.utils.FarsiUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDriverScreen(viewModel: DriverViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val otpSent by viewModel.otpSent.collectAsState()
    val authLoading by viewModel.authLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val generatedOtp by viewModel.generatedOtp.collectAsState()

    val isOnline by viewModel.isOnline.collectAsState()
    val pendingQueueItems by viewModel.pendingQueueItems.collectAsState()
    val pendingQueueCount by viewModel.pendingQueueCount.collectAsState()

    val orders by viewModel.ordersList.collectAsState()
    val selectedOrder by viewModel.selectedOrder.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isGpsActive by viewModel.isGpsActive.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncToastMessage.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val recentGpsLogs by viewModel.gpsLogs.collectAsState()

    val showScannerDialog by viewModel.showScannerDialog.collectAsState()
    val scanStage by viewModel.scanStage.collectAsState()

    val connectedPrinter by viewModel.connectedPrinter.collectAsState()
    val availablePrinters by viewModel.availablePrinters.collectAsState()
    val isPrinting by viewModel.isPrinting.collectAsState()

    val serverUrl by viewModel.serverUrl.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    val backupInfo by viewModel.backupInfo.collectAsState()
    val isBgServiceRunning by viewModel.isBackgroundServiceRunning.collectAsState()
    val bgLastSyncTime by viewModel.backgroundLastSyncTime.collectAsState()

    var showPrinterDialog by remember { mutableStateOf(false) }
    var showSyncQueueDialog by remember { mutableStateOf(false) }
    var rackDialogOrderId by remember { mutableStateOf<String?>(null) }
    var settlementOrder by remember { mutableStateOf<OrderWithItems?>(null) }

    LaunchedEffect(syncMessage) {
        syncMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearToastMessage()
        }
    }

    if (showPrinterDialog) {
        PrinterDeviceDialog(
            connectedPrinter = connectedPrinter,
            availablePrinters = availablePrinters,
            onScan = { viewModel.scanBluetoothPrinters(context) },
            onConnect = { viewModel.connectPrinter(it) },
            onDisconnect = { /* handled in manager */ },
            onDismiss = { showPrinterDialog = false }
        )
    }

    if (showSyncQueueDialog) {
        SyncQueueDialog(
            isOnline = isOnline,
            pendingQueue = pendingQueueItems,
            isSyncing = isSyncing,
            onDismiss = { showSyncQueueDialog = false },
            onSyncNow = { viewModel.syncWithWebPanel() }
        )
    }

    val activeRackOrderId = rackDialogOrderId
    if (activeRackOrderId != null) {
        val currentOrder = orders.find { it.order.id == activeRackOrderId }
        RackAssignmentDialog(
            orderId = activeRackOrderId,
            currentRackCode = currentOrder?.order?.rackCode ?: "",
            onDismiss = { rackDialogOrderId = null },
            onConfirm = { rackCode ->
                viewModel.assignRackCode(activeRackOrderId, rackCode)
                rackDialogOrderId = null
            }
        )
    }

    val activeSettlementOrder = settlementOrder
    if (activeSettlementOrder != null) {
        SettlementDialog(
            orderWithItems = activeSettlementOrder,
            onDismiss = { settlementOrder = null },
            onConfirmSettlement = { paid, discount, method, print ->
                viewModel.settlePayment(activeSettlementOrder.order.id, paid, discount, method)
                if (print) {
                    viewModel.printOrderReceipt("رسید تسویه حساب و تحویل فرش", activeSettlementOrder, method)
                }
                settlementOrder = null
            }
        )
    }

    if (showScannerDialog) {
        BarcodeScannerModal(
            expectedOrder = selectedOrder,
            allOrders = orders,
            scanStage = scanStage,
            onDismiss = { viewModel.closeScanner() },
            onConfirmVerification = { success -> viewModel.handleScanSuccess(success) },
            onReportMismatchToDispatch = { alertText -> viewModel.reportScanMismatchToDispatch(alertText) }
        )
    }

    ZomorrodDriverTheme(darkTheme = isDarkMode) {
        if (!isLoggedIn) {
            DriverLoginScreen(
                onSendOtp = { phone -> viewModel.requestOtp(phone) },
                onVerifyOtp = { phone, code -> viewModel.verifyOtp(phone, code) },
                onResetOtp = { viewModel.resetOtpState() },
                otpSent = otpSent,
                isLoading = authLoading,
                errorMessage = authError
            )
        } else {
            Scaffold(
                topBar = {
                    Surface(
                        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp
                    ) {
                        TopAppBar(
                        title = {
                            val currentScreenTitle = when (activeTab) {
                                0 -> "مسیر تحویل مشتریان"
                                1 -> "جمع‌آوری و ثبت فاکتور"
                                2 -> "تحویل به انبار قالیشویی"
                                3 -> "تسویه حساب و فاکتورها"
                                4 -> "پشتیبانی و چت دیسپچ"
                                5 -> "موقعیت مکانی GPS"
                                6 -> "تنظیمات نرم‌افزار"
                                99 -> "صدور پیش‌فاکتور دریافت"
                                else -> "قالیشویی زمرد"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CleanBluePrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        when (activeTab) {
                                            0 -> Icons.Default.LocalShipping
                                            1 -> Icons.Default.EditNote
                                            2 -> Icons.Default.Warehouse
                                            3 -> Icons.Default.CheckCircle
                                            4 -> Icons.Default.Chat
                                            5 -> Icons.Default.GpsFixed
                                            6 -> Icons.Default.Settings
                                            else -> Icons.Default.LocalShipping
                                        },
                                        contentDescription = null,
                                        tint = CleanBluePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = currentScreenTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "قالیشویی زمرد • سامانه سفیران",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            // Active status pill badge with Room offline sync indicator
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (!isOnline || pendingQueueCount > 0) Color(0xFFFFF3E0) else CleanTealContainer)
                                    .clickable { showSyncQueueDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (!isOnline || pendingQueueCount > 0) Color(0xFFFF9800) else CleanTealAccent)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (pendingQueueCount > 0) "${FarsiUtils.toFarsiDigits(pendingQueueCount.toString())} آفلاین" else if (!isOnline) "آفلاین" else "آنلاین",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isOnline || pendingQueueCount > 0) Color(0xFFE65100) else CleanTealAccent
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // Barcode/QR Scanner button
                            IconButton(onClick = { viewModel.openScanner(com.example.data.model.ScanStage.DELIVERY) }) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "اسکن بارکد / QR کد",
                                    tint = CleanBluePrimary
                                )
                            }

                            // Quick Settings Button
                            IconButton(onClick = { viewModel.setActiveTab(6) }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "تنظیمات برنامه",
                                    tint = CleanBluePrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            },
            bottomBar = {
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent
                    ) {
                        // 1. جمع‌آوری
                        NavigationBarItem(
                            selected = activeTab == 1 || activeTab == 99,
                            onClick = { viewModel.setActiveTab(1) },
                            icon = { Icon(Icons.Default.EditNote, contentDescription = "جمع‌آوری") }
                        )
                        // 2. انبار
                        NavigationBarItem(
                            selected = activeTab == 2,
                            onClick = { viewModel.setActiveTab(2) },
                            icon = { Icon(Icons.Default.Warehouse, contentDescription = "انبار") }
                        )
                        // 3. تحویل
                        NavigationBarItem(
                            selected = activeTab == 0,
                            onClick = { viewModel.setActiveTab(0) },
                            icon = { Icon(Icons.Default.LocalShipping, contentDescription = "تحویل") }
                        )
                        // 4. تسویه
                        NavigationBarItem(
                            selected = activeTab == 3,
                            onClick = { viewModel.setActiveTab(3) },
                            icon = { Icon(Icons.Default.CheckCircle, contentDescription = "تسویه") }
                        )
                        // 5. چت پشتیبانی
                        NavigationBarItem(
                            selected = activeTab == 4,
                            onClick = { viewModel.setActiveTab(4) },
                            icon = { Icon(Icons.Default.Chat, contentDescription = "پشتیبانی") }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (activeTab) {
                    0 -> DeliveryRouteScreen(
                        orders = orders,
                        onSelectOrderForSettlement = { orderWithItems ->
                            viewModel.selectOrder(orderWithItems.order.id)
                            viewModel.setActiveTab(3)
                        },
                        onOpenScanner = { orderId ->
                            viewModel.openScanner(com.example.data.model.ScanStage.DELIVERY, orderId)
                        },
                        onReturnToCleanWarehouse = { orderId, cleanRack, reason ->
                            viewModel.returnToCleanWarehouse(orderId, cleanRack, reason)
                        }
                    )
                    1 -> CollectionRouteScreen(
                        orders = orders,
                        onSelectOrderForInvoice = { orderWithItems ->
                            viewModel.selectOrder(orderWithItems.order.id)
                            viewModel.setActiveTab(99)
                        }
                    )
                    2 -> WarehouseHandoverScreen(
                        orders = orders,
                        onConfirmWarehouseHandover = { orderId, rackCode ->
                            viewModel.confirmWarehouseHandover(orderId, rackCode)
                        },
                        onPrintWarehouseReceipt = { orderWithItems ->
                            viewModel.printOrderReceipt("رسید تحویل و نگهداری انباردار", orderWithItems)
                        },
                        onOpenScanner = { targetId ->
                            viewModel.openScanner(com.example.data.model.ScanStage.WORKSHOP, targetId)
                        }
                    )
                    3 -> DeliverySettlementScreen(
                        orders = orders,
                        onSettlePayment = { id, paid, discount, method ->
                            viewModel.settlePayment(id, paid, discount, method)
                        },
                        onPrintReceipt = { orderWithItems, method ->
                            viewModel.printOrderReceipt("رسید تسویه حساب و تحویل فرش", orderWithItems, method)
                        },
                        onOpenScanner = { targetId ->
                            viewModel.openScanner(com.example.data.model.ScanStage.DELIVERY, targetId)
                        },
                        onSettleWithOffice = {
                            viewModel.settleWithOffice()
                        },
                        onPrintDailySettlementReport = {
                            viewModel.printDailySettlementReport(settledOrders = orders.filter { it.order.status == "DELIVERED_SETTLED" })
                        },
                        onSignatureCaptured = { orderId, signatureData ->
                            viewModel.captureCustomerSignature(orderId, signatureData)
                        },
                        onReturnToCleanWarehouse = { orderId, cleanRack, reason ->
                            viewModel.returnToCleanWarehouse(orderId, cleanRack, reason)
                        }
                    )
                    4 -> DispatchChatScreen(
                        messages = chatMessages,
                        onSendMessage = { text -> viewModel.sendChatMessage(text) }
                    )
                    5 -> GpsTrackingScreen(
                        isGpsActive = isGpsActive,
                        unsyncedCount = unsyncedCount,
                        isSyncing = isSyncing,
                        recentGpsLogs = recentGpsLogs,
                        onToggleGps = { viewModel.toggleGpsTracking() },
                        onSyncNow = { viewModel.syncWithWebPanel() }
                    )
                    6 -> SettingsScreen(
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = { viewModel.toggleDarkMode() },
                        connectedPrinterName = connectedPrinter?.name,
                        onOpenPrinterDialog = {
                            viewModel.scanBluetoothPrinters(context)
                            showPrinterDialog = true
                        },
                        onPrintTestReceipt = { viewModel.printTestReceipt() },
                        onSyncNow = { viewModel.syncWithWebPanel() },
                        savedServerUrl = serverUrl,
                        isTestingConnection = isTestingConnection,
                        connectionTestResult = connectionTestResult,
                        onUpdateServerUrl = { viewModel.updateServerUrl(it) },
                        onTestConnection = { viewModel.testServerConnection(it) },
                        backupInfo = backupInfo,
                        onBackupDatabase = { viewModel.backupDatabase() },
                        onRestoreDatabase = { viewModel.restoreDatabase() },
                        onLogout = { viewModel.logoutDriver() }
                    )
                    99 -> CarpetRegistrationScreen(
                        orderWithItems = selectedOrder,
                        isPrinting = isPrinting,
                        onBack = { viewModel.setActiveTab(1) },
                        onAddCarpetItem = { type, len, wid, price, servs, defs, notes, tag ->
                            selectedOrder?.let {
                                viewModel.addCarpetItem(it.order.id, type, len, wid, price, servs, defs, notes, tag)
                            }
                        },
                        onDeleteCarpetItem = { itemId ->
                            selectedOrder?.let {
                                viewModel.deleteCarpetItem(itemId, it.order.id)
                            }
                        },
                        onPrintReceipt = {
                            selectedOrder?.let {
                                viewModel.printOrderReceipt("پیش‌فاکتور اولیه دریافت فرش", it)
                            }
                        },
                        onProceedToWorkshop = {
                            selectedOrder?.let {
                                viewModel.finalizeInvoiceRegistration(it.order.id)
                            }
                        }
                    )
                }
            }
        }
    }
}
}

