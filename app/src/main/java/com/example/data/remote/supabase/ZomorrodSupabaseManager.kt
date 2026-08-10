package com.example.data.remote.supabase

import android.util.Log
import com.example.data.local.entities.CarpetItemEntity
import com.example.data.local.entities.ChatMessageEntity
import com.example.data.local.entities.DriverEntity
import com.example.data.local.entities.DriverSettlementEntity
import com.example.data.local.entities.OrderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * مدیر ارتباطی و همگام‌ساز واقعی با Supabase برای اپلیکیشن قالیشویی زمرد.
 *
 * برخلاف نسخه‌ی قبلی، این کلاس دیگر مستقیم به جدول‌های Postgrest وصل
 * نمی‌شود (چون orders/drivers با RLS فقط برای کاربر لاگین‌کرده در پنل وب
 * باز هستند و اپ اندروید چنین لاگینی ندارد). به‌جایش، همه‌ی درخواست‌ها از
 * طریق Edge Function «driver-api» و «otp» (که در پروژه‌ی وب ساخته شدند و
 * با هدر x-driver-api-key احراز هویت می‌شوند) انجام می‌شود.
 *
 * چت با دیسپچر (chat/send، chat/messages) و آپلود امضای دیجیتال مشتری
 * (signature/upload) هم از طریق همین driver-api انجام می‌شود؛ پیام‌ها در
 * همان جدول chat_messages پنل وب ذخیره می‌شوند و امضا در باکت Storage
 * عمومی «signatures» آپلود و لینکش روی ستون customer_signature_url
 * سفارش ثبت می‌شود.
 */
class ZomorrodSupabaseManager(
    private var supabaseUrl: String = ZomorrodSupabaseConfig.DEFAULT_SUPABASE_URL,
    private var driverApiKey: String = ZomorrodSupabaseConfig.DRIVER_API_KEY
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun functionsBase(): String = "${supabaseUrl.trim().removeSuffix("/")}/functions/v1"

    fun updateCredentials(url: String, key: String = driverApiKey) {
        this.supabaseUrl = url.trim().removeSuffix("/")
        if (key.isNotBlank()) this.driverApiKey = key.trim()
    }

    private fun baseRequest(url: String) = Request.Builder()
        .url(url)
        .addHeader("x-driver-api-key", driverApiKey)
        .addHeader("Content-Type", "application/json")
        .addHeader("Accept", "application/json")

    // ==========================================================================
    // سلامت اتصال
    // ==========================================================================

    /**
     * تست برقراری ارتباط با Edge Function driver-api (مسیر health که
     * صرفاً یک پاسخ ok برمی‌گرداند و به هیچ داده‌ای دسترسی ندارد، پس بدون
     * نیاز به کلید برای پینگ ساده مناسب است)
     */
    suspend fun checkHealth(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val endpoint = "${functionsBase()}/driver-api/health"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    Pair(true, "ارتباط موفق با Supabase ($supabaseUrl) | تأخیر: ${duration}ms")
                } else {
                    Pair(false, "پاسخ نامعتبر از سرور (HTTP ${response.code} | تأخیر: ${duration}ms)")
                }
            }
        } catch (e: Exception) {
            Pair(false, "عدم برقراری ارتباط با $supabaseUrl: ${e.localizedMessage ?: "Timeout"}")
        }
    }

    // ==========================================================================
    // موقعیت زنده راننده (GPS)
    // ==========================================================================

    /**
     * ارسال موقعیت مکانی زنده‌ی راننده به driver-api/driver/location.
     * توجه: این دیگر جدول drivers را در Supabase به‌روزرسانی نمی‌کند (چون
     * ستون‌هایی مثل current_lat/battery_level آنجا وجود ندارند) — مقصد
     * واقعی جدول جداگانه‌ی live_locations است، دقیقاً همانی که نقشه‌ی
     * زنده‌ی پنل وب از آن می‌خواند.
     */
    suspend fun syncDriverStatus(driver: DriverEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("driverId", driver.id)
                put("latitude", driver.currentLat)
                put("longitude", driver.currentLng)
                // driver.speed در این اپ بر حسب km/h ذخیره می‌شود؛ سرور بر
                // حسب m/s کار می‌کند (مطابق همان چیزی که پنل وب هم استفاده می‌کند)
                put("speedMetersPerSecond", driver.speed / 3.6)
            }.toString()

            val request = baseRequest("${functionsBase()}/driver-api/driver/location")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error syncing driver location", e)
            false
        }
    }

    // ==========================================================================
    // سفارشات
    // ==========================================================================

    /**
     * بسته به وضعیت فعلی سفارش، درخواست را به مسیر درست از driver-api
     * می‌فرستد (چون بر خلاف نسخه‌ی قبلی، یک endpoint عمومی «آپدیت کلی
     * سفارش» در سرور وجود ندارد — هر مرحله مسیر Edge Function خودش را دارد).
     */
    suspend fun upsertOrder(order: OrderEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            when (order.status) {
                "RETURNED_TO_CLEAN_WAREHOUSE" -> pushReturnToWarehouse(order)
                "DELIVERED_SETTLED" -> pushSettle(order)
                else -> pushStatusUpdate(order)
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error pushing order update", e)
            false
        }
    }

    private suspend fun pushStatusUpdate(order: OrderEntity): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("status", localStatusToDriverApiStatus(order.status))
            put("rackCode", order.rackCode)
            put("notes", order.notes)
        }.toString()

        val request = baseRequest("${functionsBase()}/driver-api/orders/${order.id}/status")
            .put(payload.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { it.isSuccessful }
    }

    private suspend fun pushReturnToWarehouse(order: OrderEntity): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("cleanRackCode", order.cleanRackCode)
            put("returnReason", order.returnReason)
            put("driverId", order.driverId)
        }.toString()

        val request = baseRequest("${functionsBase()}/driver-api/orders/${order.id}/return-to-warehouse")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { it.isSuccessful }
    }

    private suspend fun pushSettle(order: OrderEntity): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("paymentType", localPaymentMethodToDriverApi(order.paymentMethod))
            put("paidAmount", order.paidAmount)
            put("remainingAmount", (order.finalPayable - order.paidAmount).coerceAtLeast(0L))
            put("verifiedBarcodes", JSONArray())
        }.toString()

        val request = baseRequest("${functionsBase()}/driver-api/orders/${order.id}/settle")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { it.isSuccessful }
    }

    /**
     * ثبت اقلام فرش یک سفارش با POST به driver-api/orders/:id/items.
     * آرایه‌ی items دقیقاً با همان شکلی که پنل وب برای carpets انتظار
     * دارد ساخته می‌شود، وگرنه فرش‌های ثبت‌شده توسط راننده در پنل وب
     * درست نمایش داده نمی‌شوند.
     */
    suspend fun upsertCarpetItems(items: List<CarpetItemEntity>): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext true
        try {
            val orderId = items.first().orderId
            val itemsArray = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.barcodeTag.ifBlank { "ITEM-${item.id}" })
                    put("carpetType", item.carpetType)
                    put("length", item.lengthMeter)
                    put("width", item.widthMeter)
                    put("area", item.areaSqMeter)
                    put("unitPricePerMeter", item.unitPricePerMeter)
                    put("totalPrice", item.totalPrice)
                    put("services", JSONArray(item.requestedServicesJson.split("،", ",").map { it.trim() }.filter { it.isNotBlank() }))
                    put("hasStain", item.defectsJson.isNotBlank())
                    put("stainDetails", item.defectsJson)
                    put("notes", item.notes)
                    put("barcodeTag", item.barcodeTag)
                    put("rackLocation", "")
                }
                itemsArray.put(obj)
            }

            val payload = JSONObject().apply {
                put("items", itemsArray)
                put("prepaidAmount", 0)
            }.toString()

            val request = baseRequest("${functionsBase()}/driver-api/orders/$orderId/items")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error upserting carpet items", e)
            false
        }
    }

    /**
     * دریافت مسیر جمع‌آوری (routes/collection) و مسیر تحویل
     * (routes/delivery) از driver-api و ترکیب هر دو در یک لیست.
     */
    suspend fun fetchDriverOrders(driverId: String): List<SupabaseOrderDto> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SupabaseOrderDto>()
        try {
            result += fetchRoute("${functionsBase()}/driver-api/routes/collection?driverId=$driverId", "PICKUP", driverId)
            result += fetchRoute("${functionsBase()}/driver-api/routes/delivery?driverId=$driverId", "DELIVERY", driverId)
        } catch (e: Exception) {
            Log.d("SupabaseManager", "Notice: Server response for orders: ${e.message}")
        }
        result
    }

    private fun fetchRoute(url: String, orderType: String, driverId: String): List<SupabaseOrderDto> {
        val request = baseRequest(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string()?.trim() ?: return emptyList()
            val root = JSONObject(body)
            val ordersArray = root.optJSONArray("orders") ?: return emptyList()
            val list = mutableListOf<SupabaseOrderDto>()
            for (i in 0 until ordersArray.length()) {
                val obj = ordersArray.optJSONObject(i) ?: continue
                val totalAmount = obj.optLong("totalAmount", 0L)
                val remaining = obj.optLong("remainingAmount", 0L)
                list.add(
                    SupabaseOrderDto(
                        id = obj.optString("id"),
                        tracking_code = obj.optString("id"),
                        customer_name = obj.optString("customerName"),
                        customer_phone = obj.optString("customerPhone"),
                        customer_address = obj.optString("address"),
                        lat = obj.optDouble("latitude", 35.779),
                        lng = obj.optDouble("longitude", 51.405),
                        stage = driverApiStatusToLocalStage(obj.optString("status", "ASSIGNED")),
                        status = driverApiStatusToLocalStatus(obj.optString("status", "ASSIGNED")),
                        order_type = orderType,
                        driver_id = driverId,
                        driver_name = "",
                        total_amount = totalAmount,
                        discount_amount = 0L,
                        final_payable = totalAmount,
                        paid_amount = obj.optLong("prepaidAmount", 0L),
                        payment_method = driverApiPaymentMethodToLocal(obj.optString("paymentMethod", "PENDING")),
                        payment_status = if (remaining <= 0L && totalAmount > 0L) "paid" else "unpaid",
                        rack_code = obj.optString("rackCode", ""),
                        clean_rack_code = "",
                        return_reason = "",
                        customer_signature_url = "",
                        notes = obj.optString("notes", "")
                    )
                )
            }
            return list
        }
    }

    // ==========================================================================
    // تسویه‌حساب پایان روز راننده با دفتر
    // ==========================================================================

    suspend fun upsertDriverSettlement(settlement: DriverSettlementEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val orderIds = try {
                val arr = JSONArray(settlement.orderIdsJson)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) {
                emptyList()
            }

            val payload = JSONObject().apply {
                put("driverId", settlement.driverId)
                put("totalCash", settlement.totalCash)
                put("totalPos", settlement.totalPos)
                put("totalCardToCard", settlement.totalCardToCard)
                put("totalOnline", settlement.totalOnline)
                put("settledOrderIds", JSONArray(orderIds))
            }.toString()

            val request = baseRequest("${functionsBase()}/driver-api/driver/office-settlement")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error saving driver settlement", e)
            false
        }
    }

    // ==========================================================================
    // ورود راننده با کد پیامکی (OTP) — از طریق Edge Function واقعی otp
    // ==========================================================================

    /** درخواست ارسال کد. پیام قابل‌نمایش به کاربر را هم برمی‌گرداند. */
    suspend fun requestOtp(phone: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply { put("mobile", phone) }.toString()
            val request = Request.Builder()
                .url("${functionsBase()}/otp/request")
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val json = JSONObject(body)
                Pair(json.optBoolean("success", false), json.optString("message", "کد ارسال شد."))
            }
        } catch (e: Exception) {
            Pair(false, "خطا در ارسال کد: ${e.localizedMessage ?: "بدون اتصال"}")
        }
    }

    /** تایید کد. در صورت موفقیت، شناسه‌ی واقعی راننده (driverId) را برمی‌گرداند. */
    suspend fun verifyOtp(phone: String, code: String): String? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("mobile", phone)
                put("code", code)
            }.toString()
            val request = Request.Builder()
                .url("${functionsBase()}/otp/verify")
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val json = JSONObject(body)
                if (json.optBoolean("success", false)) json.optString("driverId").ifBlank { null } else null
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error verifying OTP", e)
            null
        }
    }

    // ==========================================================================
    // چت با دیسپچر و آپلود امضای دیجیتال
    // (سه مسیر chat/send، chat/messages و signature/upload در driver-api)
    // ==========================================================================

    suspend fun sendChatMessage(message: ChatMessageEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("senderName", message.senderName)
                put("text", message.messageText)
            }.toString()

            val request = baseRequest("${functionsBase()}/driver-api/chat/send")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error sending chat message", e)
            false
        }
    }

    suspend fun fetchChatMessages(driverId: String): List<SupabaseChatMessageDto> = withContext(Dispatchers.IO) {
        try {
            val request = baseRequest("${functionsBase()}/driver-api/chat/messages").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string()?.trim() ?: return@use emptyList()
                val root = JSONObject(body)
                val arr = root.optJSONArray("messages") ?: return@use emptyList()
                val list = mutableListOf<SupabaseChatMessageDto>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(
                        SupabaseChatMessageDto(
                            id = obj.optString("id"),
                            driver_id = obj.optString("driver_id", driverId),
                            sender = obj.optString("sender"),
                            sender_name = obj.optString("sender_name"),
                            text = obj.optString("text"),
                            timestamp = obj.optString("timestamp")
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error fetching chat messages", e)
            emptyList()
        }
    }

    suspend fun uploadSignature(orderId: String, signatureBase64: String): String? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("orderId", orderId)
                put("signatureBase64", signatureBase64)
            }.toString()

            val request = baseRequest("${functionsBase()}/driver-api/signature/upload")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                if (json.optBoolean("success", false)) json.optString("url").ifBlank { null } else null
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Error uploading signature", e)
            null
        }
    }

    // ==========================================================================
    // نگاشت مقادیر بین اپ اندروید و قرارداد JSON واقعی driver-api
    // ==========================================================================

    private fun localStatusToDriverApiStatus(status: String): String =
        if (status == "COLLECTED_IN_INSPECTION") "COLLECTED" else status

    private fun driverApiStatusToLocalStatus(status: String): String =
        if (status == "COLLECTED") "COLLECTED_IN_INSPECTION" else status

    private fun driverApiStatusToLocalStage(status: String): String = when (status) {
        "ASSIGNED" -> "pickup_assigned"
        "COLLECTED" -> "collected"
        "DELIVERED_TO_WORKSHOP" -> "factory_received"
        "WASHING" -> "washing"
        "READY_FOR_DELIVERY" -> "ready_for_delivery"
        "DELIVERED_SETTLED" -> "delivered"
        "RETURNED_TO_CLEAN_WAREHOUSE" -> "returned_to_clean_warehouse"
        "OFFICE_SETTLED" -> "office_settled"
        else -> "pickup_assigned"
    }

    private fun localPaymentMethodToDriverApi(method: String): String = when (method) {
        "cash" -> "CASH"
        "pos" -> "POS"
        "card_to_card", "online" -> "CREDIT"
        else -> "PENDING"
    }

    private fun driverApiPaymentMethodToLocal(method: String): String = when (method) {
        "CASH" -> "cash"
        "POS" -> "pos"
        "CREDIT" -> "card_to_card"
        else -> "unpaid"
    }
}
