package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.local.model.OrderWithItems
import com.example.ui.components.ReturnToCleanWarehouseDialog
import com.example.ui.components.SettlementDialog
import com.example.ui.theme.CleanBlueContainer
import com.example.ui.theme.CleanBluePrimary
import com.example.ui.theme.CleanPurpleAccent
import com.example.ui.theme.CleanPurpleContainer
import com.example.ui.theme.CleanTealAccent
import com.example.utils.FarsiUtils
import com.example.utils.NavigationUtils

@Composable
fun DeliverySettlementScreen(
    orders: List<OrderWithItems>,
    onSettlePayment: (orderId: String, paidAmount: Long, discountAmount: Long, paymentMethod: String) -> Unit,
    onPrintReceipt: (OrderWithItems, String) -> Unit,
    onOpenScanner: (orderId: String) -> Unit = {},
    onSettleWithOffice: () -> Unit = {},
    onPrintDailySettlementReport: () -> Unit = {},
    onSignatureCaptured: (orderId: String, signatureData: String) -> Unit = { _, _ -> },
    onReturnToCleanWarehouse: (orderId: String, cleanRackCode: String, reason: String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current

    // Active pending delivery orders (excluding settled or returned ones)
    val pendingDeliveryOrders = orders.filter {
        (it.order.orderType == "DELIVERY" || it.order.status == "READY_FOR_DELIVERY") &&
                it.order.status != "DELIVERED_SETTLED" &&
                it.order.status != "OFFICE_SETTLED" &&
                it.order.status != "RETURNED_TO_CLEAN_WAREHOUSE"
    }

    // Today's settled orders waiting for office handover
    val settledTodayOrders = orders.filter {
        it.order.status == "DELIVERED_SETTLED"
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Pending, 1: Settled Today
    var selectedOrderForSettlement by remember { mutableStateOf<OrderWithItems?>(null) }
    var orderForCleanWarehouseReturn by remember { mutableStateOf<OrderWithItems?>(null) }
    var showOfficeSettlementDialog by remember { mutableStateOf(false) }

    // Calculate Financial Summary Statistics based strictly on today's active cycle
    val totalReceived = settledTodayOrders.sumOf { it.order.paidAmount }

    val pendingCollection = pendingDeliveryOrders.sumOf { maxOf(0L, (it.order.totalAmount - it.order.discountAmount - it.order.paidAmount)) }

    val cashReceived = settledTodayOrders.filter {
        it.order.paidAmount > 0 && (it.order.paymentMethod.contains("CASH", ignoreCase = true) || it.order.paymentMethod.contains("نقدی") || it.order.paymentMethod.contains("نقد"))
    }.sumOf { it.order.paidAmount }

    val posReceived = maxOf(0L, totalReceived - cashReceived)
    val settledCount = settledTodayOrders.size

    val activeSettlement = selectedOrderForSettlement
    if (activeSettlement != null) {
        SettlementDialog(
            orderWithItems = activeSettlement,
            onDismiss = { selectedOrderForSettlement = null },
            onConfirmSettlement = { paid, discount, method, print ->
                onSettlePayment(activeSettlement.order.id, paid, discount, method)
                if (print) {
                    onPrintReceipt(activeSettlement, method)
                }
                selectedOrderForSettlement = null
            },
            onSignatureCaptured = onSignatureCaptured
        )
    }

    val activeReturn = orderForCleanWarehouseReturn
    if (activeReturn != null) {
        ReturnToCleanWarehouseDialog(
            orderId = activeReturn.order.id,
            customerName = activeReturn.order.customerName,
            currentRackCode = activeReturn.order.rackCode,
            onDismiss = { orderForCleanWarehouseReturn = null },
            onConfirm = { cleanRackCode, reason ->
                onReturnToCleanWarehouse(activeReturn.order.id, cleanRackCode, reason)
                orderForCleanWarehouseReturn = null
            }
        )
    }

    // Office Settlement Confirmation Dialog
    if (showOfficeSettlementDialog) {
        AlertDialog(
            onDismissRequest = { showOfficeSettlementDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        showOfficeSettlementDialog = false
                        onSettleWithOffice()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanPurpleAccent)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تأیید و پاکسازی لیست امروز")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOfficeSettlementDialog = false }) {
                    Text("انصراف")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = CleanPurpleAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تسویه حساب با دفتر مدیریت", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "آیا از انجام تسویه نهایی روزانه و بستن کارکرد امروز با مدیریت اطمینان دارید؟",
                        fontSize = 13.sp
                    )
                    Divider()
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("تعداد فاکتورهای تسویه‌شده امروز:", fontSize = 12.sp)
                        Text("${FarsiUtils.toFarsiDigits(settledCount.toString())} فاکتور", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("مجموع کل مبالغ دریافتی:", fontSize = 12.sp)
                        Text(FarsiUtils.formatPrice(totalReceived), fontWeight = FontWeight.Bold, color = CleanBluePrimary, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("دریافتی نقد:", fontSize = 12.sp)
                        Text(FarsiUtils.formatPrice(cashReceived), fontWeight = FontWeight.Bold, color = CleanTealAccent, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("دریافتی کارتخوان (POS):", fontSize = 12.sp)
                        Text(FarsiUtils.formatPrice(posReceived), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("مانده در انتظار وصول:", fontSize = 12.sp)
                        Text(FarsiUtils.formatPrice(pendingCollection), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "با انجام این عمل، اطلاعات گزارش مالی به دفتر ارسال شده و لیست تصفیه‌شده‌های امروز جهت شروع روز کاری بعد پاک می‌گردد.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // 1. Top Financial Summary Report Cards
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Assessment,
                            contentDescription = null,
                            tint = CleanBluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "گزارش مالی و تسویه کارکرد",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CleanBlueContainer
                    ) {
                        Text(
                            text = "روز کاری جاری",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CleanBluePrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3 Financial Summary Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total Received Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CleanBlueContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = CleanBluePrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("کل دریافتی", fontSize = 11.sp, color = CleanBluePrimary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FarsiUtils.formatPrice(totalReceived),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = CleanBluePrimary
                            )
                        }
                    }

                    // Cash Received Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("وجه نقد", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FarsiUtils.formatPrice(cashReceived),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    // Pending Collection Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("در انتظار وصول", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FarsiUtils.formatPrice(pendingCollection),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Action Buttons: Settlement with Office & Print Daily Report
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showOfficeSettlementDialog = true },
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CleanPurpleAccent)
            ) {
                Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "تسویه روزانه با دفتر",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = onPrintDailySettlementReport,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "چاپ بیلان روزانه",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Filter Tabs for Pending vs Settled Today
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = {
                    Text(
                        text = "در انتظار تسویه (${FarsiUtils.toFarsiDigits(pendingDeliveryOrders.size.toString())})",
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = {
                    Text(
                        text = "تصفیه‌شده‌های امروز (${FarsiUtils.toFarsiDigits(settledTodayOrders.size.toString())})",
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CleanPurpleAccent,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. List Content based on selected tab
        if (selectedTab == 0) {
            // Pending Settlement List
            if (pendingDeliveryOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "هیچ فاکتوری در صف تسویه قرار ندارد.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pendingDeliveryOrders, key = { it.order.id }) { item ->
                        DeliveryOrderCard(
                            orderWithItems = item,
                            onCall = { NavigationUtils.makePhoneCall(context, item.order.customerPhone) },
                            onNavigateNeshan = { NavigationUtils.launchNeshan(context, item.order.latitude, item.order.longitude, item.order.address) },
                            onSettleClick = { selectedOrderForSettlement = item },
                            onOpenScanVerification = { onOpenScanner(item.order.id) },
                            onReturnToCleanWarehouseClick = { orderForCleanWarehouseReturn = item }
                        )
                    }
                }
            }
        } else {
            // Settled Today List (for tracking/follow-up)
            if (settledTodayOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.HistoryEdu,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = CleanPurpleAccent.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "هیچ فاکتور تسویه‌شده‌ای در انتظار تحویل به دفتر وجود ندارد.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(settledTodayOrders, key = { it.order.id }) { item ->
                        DeliveryOrderCard(
                            orderWithItems = item,
                            onCall = { NavigationUtils.makePhoneCall(context, item.order.customerPhone) },
                            onNavigateNeshan = { NavigationUtils.launchNeshan(context, item.order.latitude, item.order.longitude, item.order.address) },
                            onSettleClick = { selectedOrderForSettlement = item },
                            onOpenScanVerification = { onOpenScanner(item.order.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeliveryOrderCard(
    orderWithItems: OrderWithItems,
    onCall: () -> Unit,
    onNavigateNeshan: () -> Unit,
    onSettleClick: () -> Unit,
    onOpenScanVerification: () -> Unit = {},
    onReturnToCleanWarehouseClick: () -> Unit = {}
) {
    val order = orderWithItems.order
    val isSettled = order.status == "DELIVERED_SETTLED"

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSettled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSettled) Color(0xFF16A34A).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Invoice ID & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CleanBluePrimary
                    ) {
                        Text(
                            text = "فاکتور ${order.id}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = order.customerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                StatusBadge(status = order.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Phone
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = CleanBlueContainer,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = CleanBluePrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = FarsiUtils.toFarsiDigits(order.customerPhone),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Address
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color(0xFFFEE2E2),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = order.address,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }

            if (order.rackCode.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CleanPurpleContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Warehouse,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = CleanPurpleAccent
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "محل برداشت از انبار: قفسه ${order.rackCode}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = CleanPurpleAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Carpet items summary
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = CleanBluePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "اقلام این فاکتور (${FarsiUtils.toFarsiDigits(orderWithItems.items.size.toString())} تخته فرش):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    orderWithItems.items.forEach { item ->
                        val tag = if (item.barcodeTag.isNotBlank()) item.barcodeTag else "ST-${item.orderId.takeLast(4)}-${item.id}"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "• ${item.carpetType} (${FarsiUtils.toFarsiDigits(item.lengthMeter.toString())}×${FarsiUtils.toFarsiDigits(item.widthMeter.toString())} م)",
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CleanPurpleAccent
                            ) {
                                Text(
                                    text = "کد: $tag",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CleanBlueContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مبلغ قابلاخذ:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${FarsiUtils.formatPrice(order.totalAmount - order.discountAmount)} تومان",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CleanBluePrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Phone Call
                IconButton(
                    onClick = onCall,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CleanBlueContainer)
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "تماس با مشتری",
                        tint = CleanBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 2. Navigation
                IconButton(
                    onClick = onNavigateNeshan,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFDCFCE7))
                ) {
                    Icon(
                        Icons.Default.TurnRight,
                        contentDescription = "مسیریابی نشان",
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 3. Scan Verification
                IconButton(
                    onClick = onOpenScanVerification,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CleanPurpleContainer)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "اسکن تطبیق تحویل",
                        tint = CleanPurpleAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (!isSettled) {
                    // 4. Return to Clean Warehouse (Customer Absent)
                    IconButton(
                        onClick = onReturnToCleanWarehouseClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFEF3C7))
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "عدم حضور مشتری / برگشت به انبار",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 5. Settle Payment
                    IconButton(
                        onClick = onSettleClick,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CleanBluePrimary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Payments,
                                contentDescription = "تسویه و تحویل",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تسویه",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    // View Receipt
                    IconButton(
                        onClick = onSettleClick,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFDCFCE7))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = "مشاهده رسید تسویه",
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "رسید",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                    }
                }
            }
        }
    }
}
