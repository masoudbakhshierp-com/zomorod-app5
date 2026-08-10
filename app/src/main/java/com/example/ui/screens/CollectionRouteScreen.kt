package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import com.example.ui.components.RealisticOrderMapPreview
import com.example.ui.theme.*
import com.example.utils.FarsiUtils
import com.example.utils.NavigationUtils

@Composable
fun CollectionRouteScreen(
    orders: List<OrderWithItems>,
    onSelectOrderForInvoice: (OrderWithItems) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    // Filter pickup / collection orders (only those pending collection / invoice registration)
    val pickupOrders = orders.filter {
        it.order.status == "ASSIGNED"
    }

    val filteredOrders = pickupOrders.filter { item ->
        searchQuery.isBlank() ||
                item.order.customerName.contains(searchQuery, true) ||
                item.order.customerPhone.contains(searchQuery) ||
                item.order.id.contains(searchQuery, true) ||
                item.order.address.contains(searchQuery, true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("جستجو در نام مشتری، تلفن، آدرس یا کد سفارش...", fontSize = 11.sp) },
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // List of Collection Items listed vertically
        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inbox,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "هیچ سفارشی برای جمع‌آوری یافت نشد",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredOrders, key = { it.order.id }) { item ->
                    CollectionOrderItemCard(
                        orderWithItems = item,
                        onNavigate = {
                            NavigationUtils.launchNeshan(
                                context,
                                item.order.latitude,
                                item.order.longitude,
                                item.order.address
                            )
                        },
                        onCall = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${item.order.customerPhone}")
                            }
                            context.startActivity(intent)
                        },
                        onRegisterInvoice = {
                            onSelectOrderForInvoice(item)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Vertical Collection Item containing:
 * 1. Approximate pinned Neshan Map preview box
 * 2. Customer details card
 * 3. Register Invoice ("ثبت فاکتور") action button under the card
 */
@Composable
private fun CollectionOrderItemCard(
    orderWithItems: OrderWithItems,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onRegisterInvoice: () -> Unit
) {
    val order = orderWithItems.order
    val itemCount = orderWithItems.items.size

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Attached Realistic Map Preview with driver & destination route
            RealisticOrderMapPreview(
                customerName = order.customerName,
                address = order.address,
                orderId = order.id,
                latitude = order.latitude,
                longitude = order.longitude,
                heightDp = 135,
                isDeliveryMode = false,
                onNavigate = onNavigate
            )

            // 2. Customer Details Card Content
            Column(modifier = Modifier.padding(14.dp)) {
                // Header: Order ID & Customer Name
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
                    val statusText = when (order.status) {
                        "ASSIGNED" -> "در انتظار دریافت"
                        "COLLECTED_IN_INSPECTION" -> "فاکتور ثبت شده"
                        else -> "آماده دریافت"
                    }
                    val statusBg = if (order.status == "ASSIGNED") Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
                    val statusColor = if (order.status == "ASSIGNED") Color(0xFFD97706) else Color(0xFF16A34A)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
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

                // Registered carpet items summary if any
                if (itemCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "تعداد اقلام ثبت شده: ${FarsiUtils.toFarsiDigits(itemCount.toString())} تخته فرش",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CleanBluePrimary
                            )
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Row: Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Icon Buttons (Call & Neshan Navigation)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Phone Call Icon Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CleanBlueContainer,
                            modifier = Modifier
                                .size(42.dp)
                                .clickable { onCall() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = "تماس تلفنی",
                                    tint = CleanBluePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 2. Neshan Navigation Icon Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier
                                .size(42.dp)
                                .clickable { onNavigate() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.TurnRight,
                                    contentDescription = "مسیریابی نشان",
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 3. Register Invoice Action Button
                    Button(
                        onClick = onRegisterInvoice,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CleanBluePrimary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (itemCount > 0) "ویرایش اقلام فاکتور" else "ثبت اقلام و دریافت",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
