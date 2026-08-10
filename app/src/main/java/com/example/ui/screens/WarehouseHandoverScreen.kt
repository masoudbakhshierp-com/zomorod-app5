package com.example.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.OrderWithItems
import com.example.ui.theme.*
import com.example.utils.FarsiUtils
import com.example.utils.NavigationUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WarehouseHandoverScreen(
    orders: List<OrderWithItems>,
    onConfirmWarehouseHandover: (orderId: String, rackCode: String) -> Unit,
    onPrintWarehouseReceipt: (OrderWithItems) -> Unit,
    onOpenScanner: (orderId: String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, COMPLETED

    // Filter orders relevant for warehouse handover (only pending handover to panel)
    val warehouseOrders = orders.filter { item ->
        val status = item.order.status
        // Orders that are collected in inspection or assigned with carpet items, and NOT yet delivered to workshop
        (status == "COLLECTED_IN_INSPECTION" || (status == "ASSIGNED" && item.items.isNotEmpty())) && status != "DELIVERED_TO_WORKSHOP"
    }.filter { item ->
        val matchesSearch = searchQuery.isBlank() ||
                item.order.id.contains(searchQuery, true) ||
                item.order.customerName.contains(searchQuery, true) ||
                item.order.rackCode.contains(searchQuery, true)

        val matchesFilter = when (selectedFilter) {
            "PENDING" -> item.order.rackCode.isBlank()
            "WITH_RACK" -> item.order.rackCode.isNotBlank()
            else -> true
        }

        matchesSearch && matchesFilter
    }

    val pendingRackCount = warehouseOrders.count { it.order.rackCode.isBlank() }
    val withRackCount = warehouseOrders.count { it.order.rackCode.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("جستجو بر اساس نام مشتری، شماره فاکتور یا کد قفسه...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )

        // List of Warehouse Handover Cards
        if (warehouseOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Warehouse,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "هیچ سفارشی جهت تحویل به انباردار یا تعیین قفسه یافت نشد",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(warehouseOrders, key = { it.order.id }) { item ->
                    WarehouseHandoverCard(
                        orderWithItems = item,
                        onConfirmHandover = { rackCode ->
                            onConfirmWarehouseHandover(item.order.id, rackCode)
                        },
                        onPrintReceipt = { onPrintWarehouseReceipt(item) },
                        onOpenScanner = { onOpenScanner(item.order.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WarehouseHandoverCard(
    orderWithItems: OrderWithItems,
    onConfirmHandover: (rackCode: String) -> Unit,
    onPrintReceipt: () -> Unit,
    onOpenScanner: () -> Unit
) {
    val order = orderWithItems.order
    val items = orderWithItems.items

    var rackInput by remember(order.rackCode) {
        mutableStateOf(if (order.rackCode.isNotBlank()) order.rackCode else "A-01")
    }

    val isHandedOver = order.status == "DELIVERED_TO_WORKSHOP" && order.rackCode.isNotBlank()
    val quickRacks = listOf("A-01", "A-02", "A-05", "B-01", "B-04", "B-10", "C-03", "D-12")

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHandedOver) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isHandedOver) CleanTealAccent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Order ID, Status, Customer Name
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

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isHandedOver) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = if (isHandedOver) "تحویل انباردار شد" else "در انتظار تعیین قفسه",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHandedOver) Color(0xFF16A34A) else Color(0xFFD97706),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Carpet Details Summary
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = CleanBluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "اقلام تحویلی (${FarsiUtils.toFarsiDigits(items.size.toString())} تخته):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "متراژ: ${FarsiUtils.toFarsiDigits(String.format("%.1f", items.sumOf { it.areaSqMeter }))} م۲",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    items.forEachIndexed { index, carpet ->
                        val stapleTag = if (carpet.barcodeTag.isNotBlank()) carpet.barcodeTag else "ST-${carpet.orderId.takeLast(4)}-${carpet.id}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${FarsiUtils.toFarsiDigits((index + 1).toString())}. ${carpet.carpetType} (${FarsiUtils.toFarsiDigits(carpet.lengthMeter.toString())}×${FarsiUtils.toFarsiDigits(carpet.widthMeter.toString())} م)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CleanPurpleAccent
                                ) {
                                    Text(
                                        text = "کد: $stapleTag",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (carpet.defectsJson.isNotBlank() && carpet.defectsJson != "بدون عیب") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CleanRedContainer
                                ) {
                                    Text(
                                        text = carpet.defectsJson,
                                        fontSize = 10.sp,
                                        color = CleanRedAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rack Code Selection Section
            Text(
                text = "تعیین شماره قفسه / داربست انبار:",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Rack Selection Field + Barcode Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = rackInput,
                    onValueChange = { rackInput = it.uppercase() },
                    placeholder = { Text("شماره قفسه (مثلاً A-01)", fontSize = 12.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Warehouse, contentDescription = null, tint = CleanPurpleAccent) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onOpenScanner,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CleanPurpleContainer)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "اسکن بارکد قفسه",
                        tint = CleanPurpleAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Selection Chips for Racks
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                quickRacks.forEach { code ->
                    FilterChip(
                        selected = rackInput == code,
                        onClick = { rackInput = code },
                        label = { Text(code, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (rackInput.isNotBlank()) {
                            onConfirmHandover(rackInput)
                        }
                    },
                    enabled = rackInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CleanBluePrimary
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تأیید انباردار & ثبت در پنل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onPrintReceipt,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("رسید انبار", fontSize = 11.sp)
                }
            }
        }
    }
}
