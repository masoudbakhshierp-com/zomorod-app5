package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.OrderWithItems
import com.example.data.model.ScanStage
import com.example.ui.components.BarcodeScannerModal
import com.example.ui.components.RealisticOrderMapPreview
import com.example.ui.theme.*
import com.example.utils.FarsiUtils
import com.example.utils.NavigationUtils

import com.example.ui.components.ReturnToCleanWarehouseDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeliveryRouteScreen(
    orders: List<OrderWithItems>,
    onSelectOrderForSettlement: (OrderWithItems) -> Unit,
    onOpenScanner: (orderId: String) -> Unit,
    onReturnToCleanWarehouse: (orderId: String, cleanRackCode: String, reason: String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current

    // Filter orders ready for delivery or assigned/collected that are scheduled on map (excluding settled or returned orders)
    val deliveryOrders = orders.filter {
        (it.order.status == "READY_FOR_DELIVERY" ||
                it.order.status == "DELIVERED_TO_WORKSHOP" ||
                it.order.orderType == "DELIVERY") &&
                it.order.status != "DELIVERED_SETTLED" &&
                it.order.status != "OFFICE_SETTLED" &&
                it.order.status != "RETURNED_TO_CLEAN_WAREHOUSE"
    }

    // Selected order on map or from cards
    var selectedOrderForMap by remember(deliveryOrders) {
        mutableStateOf<OrderWithItems?>(deliveryOrders.firstOrNull())
    }

    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, READY, WORKSHOP
    var searchQuery by remember { mutableStateOf("") }
    var showDeliverySearchScanner by remember { mutableStateOf(false) }
    var scanNoticeMessage by remember { mutableStateOf<String?>(null) }
    var orderForCleanWarehouseReturn by remember { mutableStateOf<OrderWithItems?>(null) }

    if (showDeliverySearchScanner) {
        BarcodeScannerModal(
            expectedOrder = null,
            allOrders = deliveryOrders,
            scanStage = ScanStage.DELIVERY,
            onDismiss = { showDeliverySearchScanner = false },
            onConfirmVerification = { result ->
                showDeliverySearchScanner = false
                val matchedOrder = result.orderWithItems
                searchQuery = matchedOrder.order.id
                onSelectOrderForSettlement(matchedOrder)
            },
            onReportMismatchToDispatch = { showDeliverySearchScanner = false }
        )
    }

    val activeNoticeMsg = scanNoticeMessage
    if (activeNoticeMsg != null) {
        AlertDialog(
            onDismissRequest = { scanNoticeMessage = null },
            confirmButton = {
                TextButton(onClick = { scanNoticeMessage = null }) {
                    Text("تأیید و بستن", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CleanBluePrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("نتیجه اسکن بارکد فاکتور", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text(activeNoticeMsg, fontSize = 13.sp) },
            shape = RoundedCornerShape(16.dp)
        )
    }

    val activeReturnOrder = orderForCleanWarehouseReturn
    if (activeReturnOrder != null) {
        ReturnToCleanWarehouseDialog(
            orderId = activeReturnOrder.order.id,
            customerName = activeReturnOrder.order.customerName,
            currentRackCode = activeReturnOrder.order.rackCode,
            onDismiss = { orderForCleanWarehouseReturn = null },
            onConfirm = { cleanRackCode, reason ->
                onReturnToCleanWarehouse(activeReturnOrder.order.id, cleanRackCode, reason)
                orderForCleanWarehouseReturn = null
            }
        )
    }

    val filteredOrders = deliveryOrders.filter { item ->
        val matchesSearch = item.order.customerName.contains(searchQuery, true) ||
                item.order.id.contains(searchQuery, true) ||
                item.order.rackCode.contains(searchQuery, true) ||
                item.items.any { it.barcodeTag.contains(searchQuery, true) }

        val matchesFilter = when (selectedFilter) {
            "READY" -> item.order.status == "READY_FOR_DELIVERY"
            "WORKSHOP" -> item.order.status == "DELIVERED_TO_WORKSHOP"
            else -> true
        }

        matchesSearch && matchesFilter
    }

    val readyCount = deliveryOrders.count { it.order.status == "READY_FOR_DELIVERY" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {

        Spacer(modifier = Modifier.height(12.dp))

        // Search & Filter Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "فاکتورهای آماده تحویل & بارگیری انبار:",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Quick Filter Chips
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("همه (${deliveryOrders.size})", fontSize = 10.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
                FilterChip(
                    selected = selectedFilter == "READY",
                    onClick = { selectedFilter = "READY" },
                    label = { Text("آماده (${readyCount})", fontSize = 10.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Search Field & Barcode Scanner Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("جستجو...", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "پاک کردن", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { showDeliverySearchScanner = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CleanBluePrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "اسکن بارکد فاکتور", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("اسکن فاکتور", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Cards List
        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "هیچ فاکتوری برای تحویل یا بارگیری یافت نشد",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredOrders, key = { it.order.id }) { item ->
                    val isSelected = selectedOrderForMap?.order?.id == item.order.id
                    DeliveryReadyCard(
                        orderWithItems = item,
                        isSelected = isSelected,
                        onCardClick = { selectedOrderForMap = item },
                        onOpenScanner = { onOpenScanner(item.order.id) },
                        onNavigate = {
                            NavigationUtils.launchNeshan(context, item.order.latitude, item.order.longitude, item.order.address)
                        },
                        onProceedToSettlement = {
                            onSelectOrderForSettlement(item)
                        },
                        onReturnToCleanWarehouseClick = {
                            orderForCleanWarehouseReturn = item
                        }
                    )
                }
            }
        }
    }
}

/**
 * Card component for individual Invoice ready for delivery & warehouse pick-up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeliveryReadyCard(
    orderWithItems: OrderWithItems,
    isSelected: Boolean,
    onCardClick: () -> Unit,
    onOpenScanner: () -> Unit,
    onNavigate: () -> Unit,
    onProceedToSettlement: () -> Unit,
    onReturnToCleanWarehouseClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val order = orderWithItems.order
    val items = orderWithItems.items
    val rackCode = if (order.rackCode.isNotBlank()) order.rackCode else "قفسه A-01"

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CleanBlueContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) CleanBluePrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Realistic Attached Map Preview
            RealisticOrderMapPreview(
                customerName = order.customerName,
                address = order.address,
                orderId = order.id,
                latitude = order.latitude,
                longitude = order.longitude,
                heightDp = 135,
                isDeliveryMode = true,
                onNavigate = onNavigate
            )

            Column(modifier = Modifier.padding(14.dp)) {
                // Header Row: Invoice ID, Customer Name, Rack Code Badge
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

                    // Prominent Warehouse Rack Code Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CleanPurpleContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanPurpleAccent.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Warehouse,
                                contentDescription = null,
                                tint = CleanPurpleAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "قفسه: $rackCode",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CleanPurpleAccent
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Customer Phone
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
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

                // Customer Address
                Row(verticalAlignment = Alignment.Top) {
                    Surface(
                        shape = CircleShape,
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

                Spacer(modifier = Modifier.height(10.dp))

                // Carpets & Stapled Barcodes Section
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                                text = "اقلام آماده بارگیری (${FarsiUtils.toFarsiDigits(items.size.toString())} تخته فرش):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        items.forEachIndexed { idx, carpet ->
                            val stapleTag = if (carpet.barcodeTag.isNotBlank()) carpet.barcodeTag else "ST-${carpet.orderId.takeLast(4)}-${carpet.id}"
                            Column(modifier = Modifier.padding(vertical = 3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${FarsiUtils.toFarsiDigits((idx + 1).toString())}. ${carpet.carpetType} (${FarsiUtils.toFarsiDigits(carpet.lengthMeter.toString())}×${FarsiUtils.toFarsiDigits(carpet.widthMeter.toString())} م)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CleanPurpleAccent
                                    ) {
                                        Text(
                                            text = "کد فرش: $stapleTag",
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
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons Row (Unified and aligned with other screens)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Direct Phone Call
                    IconButton(
                        onClick = { NavigationUtils.makePhoneCall(context, order.customerPhone) },
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

                    // 2. Neshan Navigation
                    IconButton(
                        onClick = onNavigate,
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

                    // 3. Scan Barcode
                    IconButton(
                        onClick = onOpenScanner,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CleanPurpleContainer)
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "اسکن بارکد",
                            tint = CleanPurpleAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

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

                    // 5. Proceed to Settlement
                    IconButton(
                        onClick = onProceedToSettlement,
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
                                contentDescription = "تحویل و تسویه فاکتور",
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
                }
            }
        }
    }
}
