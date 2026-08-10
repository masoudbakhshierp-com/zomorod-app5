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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.CarpetItemEntity
import com.example.data.local.model.OrderWithItems
import com.example.ui.components.AddCarpetItemDialog
import com.example.ui.components.BarcodeView
import com.example.ui.components.QrCodeView
import com.example.ui.components.ReceiptPreviewDialog
import com.example.utils.FarsiUtils

import com.example.ui.theme.CleanPurpleAccent
import com.example.ui.theme.CleanPurpleContainer

@Composable
fun CarpetRegistrationScreen(
    orderWithItems: OrderWithItems?,
    isPrinting: Boolean,
    onBack: () -> Unit = {},
    onAddCarpetItem: (
        carpetType: String,
        length: Double,
        width: Double,
        unitPrice: Long,
        services: List<String>,
        defects: List<String>,
        notes: String,
        barcodeTag: String
    ) -> Unit,
    onDeleteCarpetItem: (itemId: Long) -> Unit,
    onPrintReceipt: () -> Unit,
    onProceedToWorkshop: () -> Unit
) {
    if (orderWithItems == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لطفاً ابتدا یک سفارش را از لیست ماموریت‌ها انتخاب کنید.")
        }
        return
    }

    val order = orderWithItems.order
    val items = orderWithItems.items

    var showAddDialog by remember { mutableStateOf(false) }
    var showReceiptPreview by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddCarpetItemDialog(
            orderId = order.id,
            onDismiss = { showAddDialog = false },
            onConfirm = { type, len, wid, price, servs, defs, notes, tag ->
                onAddCarpetItem(type, len, wid, price, servs, defs, notes, tag)
            }
        )
    }

    if (showReceiptPreview) {
        ReceiptPreviewDialog(
            title = "پیش‌فاکتور اولیه دریافت فرش در محل مشتری",
            orderWithItems = orderWithItems,
            paymentMethodLabel = "پیش‌فاکتور اولیه - در انتظار شستشو",
            isPrinting = isPrinting,
            onDismiss = { showReceiptPreview = false },
            onPrintConfirm = {
                onPrintReceipt()
                showReceiptPreview = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Customer Header Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CleanPurpleContainer.copy(alpha = 0.6f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, CleanPurpleAccent.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "بازگشت",
                                tint = CleanPurpleAccent
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Column {
                            Text(
                                text = "ثبت فاکتور اولیه و اسکن بارکد فرش",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CleanPurpleAccent
                            )
                            Text(
                                text = "کد سفارش: ${FarsiUtils.toFarsiDigits(order.id)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CleanPurpleAccent
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${items.size} فرش",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = CleanPurpleAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${order.customerName} (${FarsiUtils.toFarsiDigits(order.customerPhone)})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = order.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = null, tint = CleanPurpleAccent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "لیست فرش‌های فاکتور:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CleanPurpleAccent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("افزودن فرش جدید", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Items List
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LayersClear,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = CleanPurpleAccent.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("هنوز هیچ فرشی برای این فاکتور ثبت نشده است.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("جهت ثبت فرش، دکمه «افزودن فرش جدید» را بزنید.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { carpetItem ->
                    CarpetItemCard(
                        item = carpetItem,
                        onDelete = { onDeleteCarpetItem(carpetItem.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Pro-Forma Invoice Totals & QR Tracking Code Box
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CleanPurpleContainer.copy(alpha = 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, CleanPurpleAccent.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Straighten, contentDescription = null, tint = CleanPurpleAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مجموع متراژ:", fontSize = 12.sp)
                    }
                    Text(
                        FarsiUtils.formatArea(items.sumOf { it.areaSqMeter }),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CleanPurpleAccent
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = CleanPurpleAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مبلغ کل پیش‌فاکتور:", fontSize = 12.sp)
                    }
                    Text(
                        FarsiUtils.formatPrice(order.totalAmount),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = CleanPurpleAccent
                    )
                }

                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = CleanPurpleAccent.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QrCode, contentDescription = null, tint = CleanPurpleAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("QR کد پیگیری فاکتور مشتری", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CleanPurpleAccent)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "چاپ همزمان در ۲ نسخه (نسخه مشتری و نسخه راننده)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CleanPurpleAccent.copy(alpha = 0.3f))
                        ) {
                            Box(modifier = Modifier.padding(4.dp)) {
                                QrCodeView(code = "ORD-${order.id}", size = 52.dp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showReceiptPreview = true },
                enabled = items.isNotEmpty(),
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("چاپ فاکتور (۲ نسخه)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onProceedToWorkshop,
                enabled = items.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = CleanPurpleAccent),
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Warehouse, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("انتقال به انبار", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CarpetItemCard(
    item: CarpetItemEntity,
    onDelete: () -> Unit
) {
    val displayTag = if (item.barcodeTag.isNotBlank()) item.barcodeTag else "ST-${item.orderId.takeLast(4)}-${item.id}"

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = item.carpetType, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Stapled Barcode Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CleanPurpleContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanPurpleAccent.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = CleanPurpleAccent
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "کد فرش: $displayTag",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CleanPurpleAccent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ابعاد: ${item.lengthMeter} × ${item.widthMeter} متر (${FarsiUtils.formatArea(item.areaSqMeter)})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = FarsiUtils.formatPrice(item.totalPrice),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(text = "خدمات: ${item.requestedServicesJson}", fontSize = 12.sp)

            if (item.defectsJson.isNotBlank() && item.defectsJson != "بدون عیب اولیه") {
                Text(
                    text = "عیوب اولیه: ${item.defectsJson}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Barcode tag preview for physical stapled carpet label
            BarcodeView(
                code = displayTag,
                height = 36.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
