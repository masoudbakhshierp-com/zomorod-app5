package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ZomorrodDatabase
import com.example.data.local.entities.CarpetItemEntity
import com.example.data.local.entities.ChatMessageEntity
import com.example.data.local.entities.DriverEntity
import com.example.data.local.entities.DriverSettlementEntity
import com.example.data.local.entities.GpsLogEntity
import com.example.data.local.entities.OrderEntity
import com.example.data.local.entities.SyncQueueEntity
import com.example.data.local.model.OrderWithItems
import com.example.data.repository.ZomorrodRepository
import com.example.utils.BackupInfo
import com.example.utils.BluetoothPrinterDevice
import com.example.utils.DatabaseBackupManager
import com.example.utils.FarsiUtils
import com.example.utils.NetworkMonitor
import com.example.utils.PrinterManager
import com.example.utils.RealGpsManager
import com.example.utils.ZomorrodNotificationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DriverViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ZomorrodDatabase.getDatabase(application)
    private val repository = ZomorrodRepository(
        orderDao = db.orderDao(),
        chatMessageDao = db.chatMessageDao(),
        gpsLogDao = db.gpsLogDao(),
        syncQueueDao = db.syncQueueDao(),
        driverDao = db.driverDao(),
        driverSettlementDao = db.driverSettlementDao()
    )
    private val networkMonitor = NetworkMonitor(application)
    private val prefs = application.getSharedPreferences("zomorrod_driver_prefs", Context.MODE_PRIVATE)

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean("is_logged_in", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _savedDriverPhone = MutableStateFlow(prefs.getString("driver_phone", "") ?: "")
    val savedDriverPhone: StateFlow<String> = _savedDriverPhone

    private val _otpSent = MutableStateFlow(false)
    val otpSent: StateFlow<Boolean> = _otpSent

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    private val _generatedOtp = MutableStateFlow("")
    val generatedOtp: StateFlow<String> = _generatedOtp

    private val _serverUrl = MutableStateFlow(prefs.getString("server_url", com.example.data.remote.supabase.ZomorrodSupabaseConfig.DEFAULT_SUPABASE_URL) ?: com.example.data.remote.supabase.ZomorrodSupabaseConfig.DEFAULT_SUPABASE_URL)
    val serverUrl: StateFlow<String> = _serverUrl

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
        prefs.edit().putString("server_url", url).apply()
        repository.supabaseService.updateConfig(url)
    }

    fun testServerConnection(url: String) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            val result = repository.supabaseService.testConnection(url)
            _connectionTestResult.value = result.second
            _isTestingConnection.value = false
        }
    }

    fun requestOtp(phone: String) {
        val cleanPhone = FarsiUtils.toEnglishDigits(phone.trim()).replace(" ", "").replace("-", "")
        if (cleanPhone.length < 10) {
            _authError.value = "لطفاً شماره همراه معتبر (مانند ۰۹۱۲۳۴۵۶۷۸۹) وارد کنید."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val (ok, message) = repository.supabaseService.requestOtp(cleanPhone)
            _authLoading.value = false
            if (ok) {
                _otpSent.value = true
                _generatedOtp.value = message
                _syncToastMessage.value = message
            } else {
                _authError.value = message
            }
        }
    }

    fun verifyOtp(phone: String, code: String) {
        val cleanCode = FarsiUtils.toEnglishDigits(code.trim())
        if (cleanCode.length < 4) {
            _authError.value = "لطفاً کد تایید حداقل ۴ رقمی را وارد نمایید."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val cleanPhone = FarsiUtils.toEnglishDigits(phone.trim())
            val driverId = repository.supabaseService.verifyOtp(cleanPhone, cleanCode)
            _authLoading.value = false
            if (driverId != null) {
                prefs.edit()
                    .putBoolean("is_logged_in", true)
                    .putString("driver_phone", cleanPhone)
                    .putString("driver_id", driverId)
                    .apply()
                _savedDriverPhone.value = cleanPhone
                _isLoggedIn.value = true
                _otpSent.value = false
                _syncToastMessage.value = "خوش آمدید! ورود موفقیت‌آمیز به سامانه قالیشویی زمرد"
            } else {
                _authError.value = "کد وارد شده نامعتبر یا منقضی شده است."
            }
        }
    }

    // توجه: تابع quickLogin که قبلاً اینجا بود (ورود سریع بدون تایید واقعی
    // OTP) حذف شد — چون همون‌قدر که کد master سمت سرور خطرناک بود، این
    // میان‌بر هم می‌تونست بدون تایید واقعی کسی رو لاگین کنه. اگه بعداً
    // برای تست دوباره لازم شد، باید صریحاً پشت یه فلگ build نوع debug باشه.

    fun resetOtpState() {
        _otpSent.value = false
        _authError.value = null
    }

    fun logoutDriver() {
        prefs.edit().putBoolean("is_logged_in", false).apply()
        _isLoggedIn.value = false
        _otpSent.value = false
        _authError.value = null
        _syncToastMessage.value = "از حساب کاربری راننده خارج شدید."
    }

    fun printTestReceipt() {
        viewModelScope.launch {
            val testContent = """
===============================
     *** قالیشویی زمرد ***
    برگه تست سلامت چاپگر حرارتی
       پنل یکپارچه رانندگان
===============================
تاریخ و ساعت: ${FarsiUtils.formatCurrentTimeFarsi()}
سرور فعال: ${serverUrl.value}
وضعیت پرینتر: متصل و آماده به کار (OK)
عرض رول: ۸۰ میلی‌متر حرارتی (POS)
سفیر فعال: مسعود بختیاری
-------------------------------
✓ تست فونت فارسی: قالیشویی هوشمند زمرد
✓ تست اعداد و مبالغ: ۱۲,۳۴۵,۰۰۰ ریال
✓ تست جدول و خط‌کشی فاکتور
===============================
[ بارکد تست: ZOM-PRINTER-OK-2026 ]
===============================
    پایان برگه آزمایش چاپگر
            """.trimIndent()
            val success = PrinterManager.printRawText(testContent)
            if (success) {
                _syncToastMessage.value = "برگه تست با موفقیت به پرینتر ارسال و چاپ شد"
            } else {
                _syncToastMessage.value = "عدم ارتباط با چاپگر! لطفاً اتصال بلوتوث پرینتر را بررسی نمایید."
            }
        }
    }

    val ordersList: StateFlow<List<OrderWithItems>> = repository.allOrders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.allChatMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val unsyncedCount: StateFlow<Int> = repository.unsyncedOrdersCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val pendingQueueItems: StateFlow<List<SyncQueueEntity>> = repository.pendingQueue
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingQueueCount: StateFlow<Int> = repository.pendingQueueCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val gpsLogs: StateFlow<List<GpsLogEntity>> = repository.recentGpsLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allSettlements: StateFlow<List<DriverSettlementEntity>> = repository.allSettlements
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedOrderId = MutableStateFlow<String?>(null)
    val selectedOrderId: StateFlow<String?> = _selectedOrderId

    val selectedOrder: StateFlow<OrderWithItems?> = combine(ordersList, _selectedOrderId) { list, id ->
        if (id == null) list.firstOrNull() else list.find { it.order.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _activeTab = MutableStateFlow(0) // 0: Missions, 1: Pickup, 2: Delivery, 3: Chat, 4: GPS
    val activeTab: StateFlow<Int> = _activeTab

    private val _statusFilter = MutableStateFlow("ALL")
    val statusFilter: StateFlow<String> = _statusFilter

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _isGpsActive = MutableStateFlow(true)
    val isGpsActive: StateFlow<Boolean> = _isGpsActive

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _syncToastMessage = MutableStateFlow<String?>(null)
    val syncToastMessage: StateFlow<String?> = _syncToastMessage

    private val _showScannerDialog = MutableStateFlow(false)
    val showScannerDialog: StateFlow<Boolean> = _showScannerDialog

    private val _scanStage = MutableStateFlow(com.example.data.model.ScanStage.DELIVERY)
    val scanStage: StateFlow<com.example.data.model.ScanStage> = _scanStage

    val connectedPrinter: StateFlow<BluetoothPrinterDevice?> = PrinterManager.connectedPrinter
    val availablePrinters: StateFlow<List<BluetoothPrinterDevice>> = PrinterManager.availablePrinters
    val isPrinting: StateFlow<Boolean> = PrinterManager.isPrinting

    private val _backupInfo = MutableStateFlow<BackupInfo?>(null)
    val backupInfo: StateFlow<BackupInfo?> = _backupInfo

    private val realGpsManager = RealGpsManager(application)

    init {
        refreshBackupInfo()

        realGpsManager.setLocationCallback { lat, lng, speedKmh ->
            viewModelScope.launch {
                repository.logGpsLocation(lat, lng, speedKmh)
            }
        }
        if (_isGpsActive.value) {
            realGpsManager.startTracking()
        }

        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online && (pendingQueueCount.value > 0 || unsyncedCount.value > 0)) {
                    syncWithWebPanel()
                }
            }
        }
    }

    fun openScanner(stage: com.example.data.model.ScanStage = com.example.data.model.ScanStage.DELIVERY, targetOrderId: String? = null) {
        if (targetOrderId != null) {
            _selectedOrderId.value = targetOrderId
        }
        _scanStage.value = stage
        _showScannerDialog.value = true
    }

    fun closeScanner() {
        _showScannerDialog.value = false
    }

    fun handleScanSuccess(result: com.example.data.model.ScanVerificationResult.Success) {
        val orderId = result.orderWithItems.order.id
        viewModelScope.launch {
            when (result.scanStage) {
                com.example.data.model.ScanStage.COLLECTION -> {
                    repository.updateOrderStatus(orderId, "COLLECTED_IN_INSPECTION")
                    _syncToastMessage.value = "تطابق جمع‌آوری موفق: سفارش $orderId به عنوان جمع‌آوری شده ثبت شد"
                }
                com.example.data.model.ScanStage.WORKSHOP -> {
                    repository.updateOrderStatus(orderId, "DELIVERED_TO_WORKSHOP")
                    _syncToastMessage.value = "تطابق ورودی انبار موفق: فرش‌های سفارش $orderId تحویل کارگاه گردید"
                }
                com.example.data.model.ScanStage.DELIVERY -> {
                    repository.updateOrderStatus(orderId, "DELIVERED_SETTLED")
                    _syncToastMessage.value = "تطابق تحویل مشتری موفق: فرش‌های سفارش $orderId به مشتری تحویل داده شد"
                }
            }
        }
    }

    fun reportScanMismatchToDispatch(reportText: String) {
        viewModelScope.launch {
            val currentOrder = selectedOrder.value?.order?.id ?: "GENERAL"
            repository.sendChatMessage(
                orderId = currentOrder,
                messageText = "🚨 " + reportText,
                sender = "DRIVER"
            )
            _syncToastMessage.value = "هشدار عدم تطابق به مرکز پشتیبانی ارسال گردید"
        }
    }

    fun selectOrder(orderId: String) {
        _selectedOrderId.value = orderId
    }

    fun setActiveTab(tabIndex: Int) {
        _activeTab.value = tabIndex
    }

    fun setStatusFilter(filter: String) {
        _statusFilter.value = filter
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun startGpsTracking() {
        if (_isGpsActive.value) {
            val started = realGpsManager.startTracking()
            if (!started) {
                viewModelScope.launch {
                    repository.logGpsLocation(35.779, 51.405, 0.0f)
                }
            }
        }
    }

    fun toggleGpsTracking() {
        _isGpsActive.value = !_isGpsActive.value
        if (_isGpsActive.value) {
            startGpsTracking()
        } else {
            realGpsManager.stopTracking()
        }
    }

    fun addCarpetItem(
        orderId: String,
        carpetType: String,
        lengthMeter: Double,
        widthMeter: Double,
        unitPricePerMeter: Long,
        requestedServices: List<String>,
        defects: List<String>,
        notes: String,
        barcodeTag: String = ""
    ) {
        val area = lengthMeter * widthMeter
        val itemTotalPrice = (area * unitPricePerMeter).toLong()
        val finalTag = if (barcodeTag.isNotBlank()) barcodeTag.trim().uppercase()
        else "ST-${orderId.takeLast(4)}-${(1..99).random().toString().padStart(2, '0')}"

        val item = CarpetItemEntity(
            orderId = orderId,
            carpetType = carpetType,
            lengthMeter = lengthMeter,
            widthMeter = widthMeter,
            areaSqMeter = area,
            unitPricePerMeter = unitPricePerMeter,
            requestedServicesJson = requestedServices.joinToString("، "),
            defectsJson = if (defects.isEmpty()) "بدون عیب" else defects.joinToString("، "),
            totalPrice = itemTotalPrice,
            notes = notes,
            barcodeTag = finalTag
        )
        viewModelScope.launch {
            repository.addCarpetItemToOrder(orderId, item)
        }
    }

    fun deleteCarpetItem(itemId: Long, orderId: String) {
        viewModelScope.launch {
            repository.removeCarpetItem(itemId, orderId)
        }
    }

    fun finalizeInvoiceRegistration(orderId: String) {
        viewModelScope.launch {
            repository.updateOrderStatus(orderId, "COLLECTED_IN_INSPECTION")
            _syncToastMessage.value = "ثبت فاکتور سفارش $orderId انجام شد و به منوی تحویل انبار منتقل گردید"
            _activeTab.value = 1
        }
    }

    fun captureCustomerSignature(orderId: String, signatureData: String) {
        viewModelScope.launch {
            repository.updateCustomerSignature(orderId, signatureData)
            _syncToastMessage.value = "امضای دیجیتال مشتری برای سفارش $orderId با موفقیت ذخیره و الصاق گردید"
        }
    }

    fun assignRackCode(orderId: String, rackCode: String) {
        viewModelScope.launch {
            repository.updateRackAssignment(orderId, rackCode)
            _syncToastMessage.value = "شماره قفسه $rackCode برای سفارش $orderId با موفقیت ثبت شد"
        }
    }

    fun confirmWarehouseHandover(orderId: String, rackCode: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.updateRackAssignment(orderId, rackCode)
                val isSynced = repository.syncWithWebPanel(serverUrl.value)
                _isSyncing.value = false
                if (isSynced) {
                    repository.updateOrderStatus(orderId, "DELIVERED_TO_WORKSHOP")
                    _syncToastMessage.value = "تأیید تحویل به انباردار و قفسه $rackCode به سرور panel.yaselectrical.ir ارسال شد"
                } else {
                    _syncToastMessage.value = "تحویل قفسه $rackCode در دیتابیس ثبت و در صف همگام‌سازی قرار گرفت"
                }
            } catch (e: Exception) {
                _isSyncing.value = false
                _syncToastMessage.value = "خطا در ثبت تحویل به انبار: ${e.localizedMessage ?: "مجدداً تلاش کنید"}"
            }
        }
    }

    fun returnToCleanWarehouse(orderId: String, rackCode: String, reason: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.updateCleanWarehouseReturn(orderId, rackCode, reason)
                val isSynced = repository.syncWithWebPanel(serverUrl.value)
                _isSyncing.value = false
                _syncToastMessage.value = "سفارش $orderId به قفسه تمیز انبار ($rackCode) بازگردانده شد و جهت برنامه‌ریزی مجدد به پنل ارسال گردید."
            } catch (e: Exception) {
                _isSyncing.value = false
                _syncToastMessage.value = "خطا در ثبت برگشت به انبار: ${e.localizedMessage ?: "مجدداً تلاش کنید"}"
            }
        }
    }

    fun settlePayment(
        orderId: String,
        paidAmount: Long,
        discountAmount: Long,
        paymentMethod: String
    ) {
        viewModelScope.launch {
            repository.finalizeSettlement(orderId, paidAmount, discountAmount, paymentMethod)
            _syncToastMessage.value = "تسویه حساب سفارش $orderId نهایی شد"
        }
    }

    fun settleWithOffice(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val settledOrders = ordersList.value.filter { it.order.status == "DELIVERED_SETTLED" }
                val totalCash = settledOrders.filter {
                    it.order.paymentMethod.contains("CASH", true) || it.order.paymentMethod.contains("نقدی")
                }.sumOf { it.order.paidAmount }
                val totalPos = settledOrders.filter {
                    it.order.paymentMethod.contains("POS", true) || it.order.paymentMethod.contains("کارتخوان")
                }.sumOf { it.order.paidAmount }
                val totalAmount = settledOrders.sumOf { it.order.paidAmount }

                val settlementEntity = DriverSettlementEntity(
                    id = "STL-${System.currentTimeMillis()}",
                    driverId = "DRV-101",
                    driverName = "مسعود بختیاری",
                    date = FarsiUtils.formatCurrentTimeFarsi().split(" ").firstOrNull() ?: "امروز",
                    totalCash = totalCash,
                    totalPos = totalPos,
                    totalAmount = totalAmount,
                    ordersCount = settledOrders.size,
                    orderIdsJson = settledOrders.map { it.order.id }.toString(),
                    status = "pending_approval",
                    notes = "تسویه روزانه توسط راننده ثبت شد"
                )

                repository.saveDriverSettlement(settlementEntity)
                val success = repository.syncWithWebPanel(serverUrl.value)
                repository.archiveSettledOrders()
                _isSyncing.value = false
                _syncToastMessage.value = "تسویه روزانه ثبت و به سرور panel.yaselectrical.ir ارسال شد."
                onSuccess()
            } catch (e: Exception) {
                _isSyncing.value = false
                _syncToastMessage.value = "خطا در تسویه با دفتر: ${e.localizedMessage ?: "مجدداً تلاش کنید"}"
            }
        }
    }

    fun printDailySettlementReport(
        driverName: String = "مسعود بختیاری",
        date: String = "امروز",
        settledOrders: List<OrderWithItems>
    ) {
        viewModelScope.launch {
            val totalCash = settledOrders.filter {
                it.order.paymentMethod.contains("CASH", true) || it.order.paymentMethod.contains("نقدی")
            }.sumOf { it.order.paidAmount }
            val totalPos = settledOrders.filter {
                it.order.paymentMethod.contains("POS", true) || it.order.paymentMethod.contains("کارتخوان")
            }.sumOf { it.order.paidAmount }
            val totalAmount = settledOrders.sumOf { it.order.paidAmount }

            PrinterManager.printDailySettlementReport(
                driverName = driverName,
                date = date,
                settledCount = settledOrders.size,
                totalCash = totalCash,
                totalPos = totalPos,
                totalCardToCard = 0L,
                totalAmount = totalAmount,
                orderIds = settledOrders.map { it.order.id }
            )
            _syncToastMessage.value = "گزارش تسویه روزانه به پرینتر حرارتی ارسال شد"
        }
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val currentOrder = selectedOrder.value?.order?.id ?: "GENERAL"
        viewModelScope.launch {
            repository.sendChatMessage(currentOrder, text, sender = "DRIVER")
            delay(1500)
            repository.sendChatMessage(
                currentOrder,
                "پیام شما توسط دیسپچر متمرکز panel.yaselectrical.ir دریافت گردید.",
                sender = "DISPATCHER"
            )
        }
    }

    fun syncWithWebPanel() {
        viewModelScope.launch {
            _isSyncing.value = true
            val success = repository.syncWithWebPanel(serverUrl.value)
            _isSyncing.value = false
            if (success) {
                _syncToastMessage.value = "همگام‌سازی واقعی با سرور ${serverUrl.value} با موفقیت انجام شد"
            } else {
                _syncToastMessage.value = "داده‌ها در پایگاه‌داده Room ثبت و آماده همگام‌سازی مجدد با سرور شدند"
            }
        }
    }

    fun clearToastMessage() {
        _syncToastMessage.value = null
    }

    fun scanBluetoothPrinters(context: Context) {
        PrinterManager.scanPrinters(context)
    }

    fun connectPrinter(device: BluetoothPrinterDevice) {
        viewModelScope.launch {
            PrinterManager.connectPrinter(device)
            _syncToastMessage.value = "به پرینتر ${device.name} متصل شدید"
        }
    }

    fun printOrderReceipt(
        title: String,
        orderWithItems: OrderWithItems,
        paymentMethod: String = "نقدی / کارتخوان"
    ) {
        viewModelScope.launch {
            val order = orderWithItems.order
            val itemsSummary = orderWithItems.items.map {
                "${it.carpetType} (${it.lengthMeter}x${it.widthMeter} م) - ${it.requestedServicesJson} - ${it.totalPrice} تومان"
            }
            PrinterManager.printReceipt(
                title = title,
                orderId = order.id,
                customerName = order.customerName,
                customerPhone = order.customerPhone,
                address = order.address,
                carpetDetails = itemsSummary.joinToString("\n"),
                totalPrice = order.totalAmount,
                discount = order.discountAmount,
                finalPrice = order.totalAmount - order.discountAmount,
                paymentStatus = paymentMethod,
                rackCode = order.rackCode
            )
            _syncToastMessage.value = "رسید حرارتی فاکتور ${order.id} ارسال به پرینتر شد"
        }
    }

    val isBackgroundServiceRunning = com.example.data.remote.ZomorrodBackgroundService.isServiceRunning
    val backgroundLastSyncTime = com.example.data.remote.ZomorrodBackgroundService.lastSyncTimestamp

    fun toggleBackgroundService(context: Context, enable: Boolean) {
        if (enable) {
            com.example.data.remote.ZomorrodBackgroundService.startService(context)
            _syncToastMessage.value = "سرویس پس‌زمینه فعال شد (دریافت لحظه‌ای ماموریت و پیام)"
        } else {
            com.example.data.remote.ZomorrodBackgroundService.stopService(context)
            _syncToastMessage.value = "سرویس پس‌زمینه متوقف گردید"
        }
    }

    fun testAlarmAndVibration(context: Context) {
        ZomorrodNotificationManager.testSoundAndVibration(context)
        _syncToastMessage.value = "هشدار صوتی (آلارم) و ویبره پخش شد"
    }

    fun sendTestNotification(context: Context) {
        testAlarmAndVibration(context)
    }

    fun simulateIncomingDispatcherMessage(context: Context) {
        viewModelScope.launch {
            val sampleMessages = listOf(
                "سفیر گرامی، سفارش جدید محدوده پاسداران به شما محول شد. لطفاً بررسی نمایید.",
                "لطفاً پس از اتمام فاکتور مشتری، قالی‌ها را با بارکد جدید در انبار ثبت نمایید.",
                "آدرس مشتری پلاک ۱۲ تغییر کرد به پلاک ۱۴ طبقه دوم.",
                "تسویه حساب امروز شما توسط واحد حسابداری تایید گردید."
            )
            val selectedMsg = sampleMessages.random()
            val chatEntity = ChatMessageEntity(
                id = 0L,
                orderId = "GENERAL",
                sender = "DISPATCHER",
                senderName = "دیسپچینگ مرکزی زمرد",
                messageText = selectedMsg,
                timestamp = System.currentTimeMillis(),
                isSynced = true
            )
            repository.insertChatMessage(chatEntity)
            ZomorrodNotificationManager.sendNewDispatcherMessageNotification(
                context = context,
                senderName = "دیسپچینگ مرکزی زمرد",
                messageText = selectedMsg
            )
            _syncToastMessage.value = "پیام دیسپچر دریافت و با هشدار صوتی و ویبره اطلاع‌رسانی شد."
        }
    }

    fun simulateIncomingServerOrder(context: Context) {
        viewModelScope.launch {
            val randomNum = (1000..9999).random()
            val newOrderId = "ZOM-$randomNum"
            val names = listOf("حمیدرضا زمانی", "مریم کاظمی", "امیرحسین عباسی", "فاطمه شریفی", "سعید نوری", "کامران حسینی")
            val addresses = listOf(
                "تهران، پاسداران، خیابان گلستان پنجم، پلاک ۲۸",
                "تهران، سعادت‌آباد، صراف‌ها شمالی، پلاک ۱۴",
                "تهران، میرداماد، جنب مترو، پلاک ۱۰۲",
                "تهران، نیاوران، خیابان باهنر، پلاک ۷"
            )
            val selectedName = names.random()
            val selectedAddress = addresses.random()

            val newOrder = OrderEntity(
                id = newOrderId,
                orderSequence = (ordersList.value.size + 1),
                trackingCode = "TRK-$randomNum",
                customerName = selectedName,
                customerPhone = "0912${(1000000..9999999).random()}",
                address = selectedAddress,
                notes = "اختصاص داده شده از پنل متمرکز panel.yaselectrical.ir",
                latitude = 35.77 + ((1..50).random() / 1000.0),
                longitude = 51.40 + ((1..50).random() / 1000.0),
                orderType = if (randomNum % 2 == 0) "PICKUP" else "DELIVERY",
                status = "ASSIGNED",
                stage = "pickup_assigned",
                totalAmount = 0L,
                routeOrder = (ordersList.value.size + 1),
                isSynced = true
            )

            repository.insertOrder(newOrder)

            ZomorrodNotificationManager.sendNewOrderNotification(
                context = context,
                orderId = newOrderId,
                customerName = selectedName,
                address = selectedAddress,
                orderType = newOrder.orderType
            )

            _syncToastMessage.value = "ماموریت جدید $newOrderId دریافت شد و هشدار صوتی و ویبره فعال گردید."
        }
    }

    fun simulateServerStatusChange(context: Context) {
        viewModelScope.launch {
            val currentList = ordersList.value
            if (currentList.isEmpty()) {
                _syncToastMessage.value = "هیچ سفارشی جهت تغییر وضعیت یافت نشد."
                return@launch
            }
            val targetOrder = currentList.random().order
            val statuses = mapOf(
                "READY_FOR_DELIVERY" to "آماده تحویل به راننده جهت توزیع",
                "WASHING" to "در حال شستشو در کارگاه زمرد",
                "DELIVERED_TO_WORKSHOP" to "تحویل شده به کارگاه مرکزی",
                "ASSIGNED" to "اختصاص یافته به ناوگان حمل"
            )
            val selectedStatus = statuses.entries.random()

            repository.updateOrderStatus(targetOrder.id, selectedStatus.key)

            ZomorrodNotificationManager.sendOrderStatusChangeNotification(
                context = context,
                orderId = targetOrder.id,
                customerName = targetOrder.customerName,
                newStatusTitle = selectedStatus.value
            )

            _syncToastMessage.value = "تغییر وضعیت سفارش ${targetOrder.id} به «${selectedStatus.value}» ثبت و اعلان ارسال شد."
        }
    }

    fun refreshBackupInfo() {
        _backupInfo.value = DatabaseBackupManager.getBackupInfo(getApplication())
    }

    fun backupDatabase() {
        viewModelScope.launch {
            val (success, msg) = DatabaseBackupManager.createBackup(getApplication(), db)
            _syncToastMessage.value = msg
            refreshBackupInfo()
        }
    }

    fun restoreDatabase() {
        viewModelScope.launch {
            val (success, msg) = DatabaseBackupManager.restoreBackup(getApplication(), db)
            _syncToastMessage.value = msg
            refreshBackupInfo()
        }
    }
}
