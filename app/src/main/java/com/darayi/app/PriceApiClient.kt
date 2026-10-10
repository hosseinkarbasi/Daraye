package com.darayi.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

enum class PriceApiFailure {
    MISSING_KEY, ACCESS_DENIED, ENDPOINT_NOT_FOUND, RATE_LIMITED, SERVER_ERROR, TIMEOUT,
    CONNECTION_FAILED, INVALID_RESPONSE, RESPONSE_TOO_LARGE, HTTP_ERROR
}

class PriceApiException(
    val failure: PriceApiFailure,
    val httpStatus: Int? = null
) : Exception(when (failure) {
    PriceApiFailure.MISSING_KEY -> "کلید این منبع قیمت را در تنظیمات وارد کن."
    PriceApiFailure.ACCESS_DENIED -> "دسترسی به منبع قیمت رد شد؛ اعتبار کلید و دسترسی سرویس را بررسی کن."
    PriceApiFailure.ENDPOINT_NOT_FOUND -> "آدرس سرویس قیمت پیدا نشد (HTTP 404)؛ مسیر سرویس باید بررسی شود."
    PriceApiFailure.RATE_LIMITED -> "محدودیت تعداد درخواست‌های منبع قیمت؛ کمی بعد دوباره تلاش کن."
    PriceApiFailure.SERVER_ERROR -> "سرویس پاسخ مناسب نداد؛ وضعیت سرویس، دسترسی و سهمیهٔ درخواست را بررسی کن."
    PriceApiFailure.TIMEOUT -> "زمان دریافت قیمت تمام شد؛ اتصال اینترنت را بررسی کن."
    PriceApiFailure.CONNECTION_FAILED -> "اتصال امن به منبع قیمت برقرار نشد؛ اینترنت را بررسی کن."
    PriceApiFailure.INVALID_RESPONSE -> "پاسخ منبع قیمت قابل خواندن نیست؛ قالب پاسخ یا دسترسی کلید را بررسی کن."
    PriceApiFailure.RESPONSE_TOO_LARGE -> "حجم پاسخ منبع قیمت بیش از حد مجاز است."
    PriceApiFailure.HTTP_ERROR -> "منبع قیمت درخواست را نپذیرفت (HTTP $httpStatus)."
})

class PriceApiClient(
    private val connectionFactory: (URL) -> HttpsURLConnection = {
        it.openConnection() as HttpsURLConnection
    }
) {
    fun stocks(key: String): Map<String, Quote> = stockMarket(key).quotes

    fun stockMarket(key: String): StockMarketData {
        val raw = stockResponse(key)
        return decode(raw) { StockMarketData(PriceParser.stocks(it), AssetCatalog.stocks(it)) }
    }

    fun stockAssets(key: String): List<AssetOption> = decode(stockResponse(key), AssetCatalog::stocks)

    private fun stockResponse(key: String): String {
        if (key.isBlank()) throw PriceApiException(PriceApiFailure.MISSING_KEY)
        val encoded = URLEncoder.encode(key.trim(), "UTF-8")
        return request("https://api.brsapi.ir/Tsetmc/AllSymbols.php?key=$encoded&type=1")
    }

    fun crypto(): Map<String, Quote> = decode(
        request("https://apiv2.nobitex.ir/market/stats?dstCurrency=rls"), CryptoPriceParser::nobitex
    )

    fun navasan(key: String): Map<String, Quote> {
        if (key.isBlank()) throw PriceApiException(PriceApiFailure.MISSING_KEY)
        val encoded = URLEncoder.encode(key.trim(), "UTF-8")
        return decode(request("https://api.navasan.tech/latest/?api_key=$encoded"), NavasanPriceParser::latest)
    }

    private fun <T> decode(raw: String, parser: (String) -> T): T {
        return try {
            parser(raw)
        } catch (_: Exception) {
            // Do not expose response bodies: a provider may echo the API key.
            throw PriceApiException(PriceApiFailure.INVALID_RESPONSE)
        }
    }

    private fun request(url: String): String {
        try {
            val connection = connectionFactory(URI(url).toURL())
            try {
                connection.connectTimeout = 18_000
                connection.readTimeout = 18_000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 Darayi/2.3")
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                if (status != 200) {
                    val failure = when (status) {
                        401, 403 -> PriceApiFailure.ACCESS_DENIED
                        404 -> PriceApiFailure.ENDPOINT_NOT_FOUND
                        429 -> PriceApiFailure.RATE_LIMITED
                        in 500..599 -> PriceApiFailure.SERVER_ERROR
                        else -> PriceApiFailure.HTTP_ERROR
                    }
                    throw PriceApiException(failure, status)
                }
                return connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > 12_000_000) {
                            throw PriceApiException(PriceApiFailure.RESPONSE_TOO_LARGE)
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: SocketTimeoutException) {
            throw PriceApiException(PriceApiFailure.TIMEOUT)
        } catch (_: IOException) {
            throw PriceApiException(PriceApiFailure.CONNECTION_FAILED)
        }
    }
}
