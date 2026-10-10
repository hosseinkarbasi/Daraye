package com.darayi.app

data class PriceUpdateResult(val quotes: Map<String, Quote>, val messages: List<String>, val errors: List<String>, val stockAssets: List<AssetOption> = emptyList(), val certificateAssets: List<AssetOption> = emptyList())

class PriceUpdater(private val client: PriceApiClient = PriceApiClient()) {
    fun refresh(
        holdings: List<Holding>,
        previous: Map<String, Quote>,
        stockKey: String,
        navasanKey: String
    ): PriceUpdateResult {
        val automatic = holdings.filter { !it.manual }
        val updated = previous.toMutableMap()
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var stockAssets = emptyList<AssetOption>()
        var certificateAssets = emptyList<AssetOption>()
        fun reportError(message: String) {
            messages += message
            errors += message
        }
        fun update(name: String, required: List<Holding>, fetch: () -> Map<String, Quote>) {
            try {
                val prices = fetch()
                updated.putAll(prices)
                messages += "قیمت‌های $name دریافت شد."
                val missing = required.map { it.key }.distinct().filter { it !in prices }
                if (missing.isNotEmpty()) {
                    reportError("$name: قیمت معتبر برای ${missing.joinToString("، ")} دریافت نشد؛ قیمت‌های قبلی این نمادها به‌روزرسانی نشدند.")
                }
            } catch (e: PriceApiException) {
                reportError("$name: ${e.message} قیمت‌های قبلی حفظ شد.")
            }
        }
        val stocks = automatic.filter { usesStockPrices(it.type) && !isCertificate(it.key) }
        val certificates = automatic.filter { usesStockPrices(it.type) && isCertificate(it.key) }
        val crypto = automatic.filter { it.type == AssetCategory.CRYPTO }
        val market = automatic.filter { it.type == AssetCategory.PHYSICAL_GOLD || it.type == AssetCategory.CURRENCY }
        if (stocks.isNotEmpty()) update("سهام و صندوق‌ها", stocks) {
            client.stockMarket(stockKey).also { stockAssets = it.assets }.quotes
        }
        if (certificates.isNotEmpty()) update("گواهی‌های بورس کالا", certificates) {
            client.certificateMarket(stockKey).also { certificateAssets = it.assets }.quotes
        }
        if (crypto.isNotEmpty()) update("نوبیتکس", crypto) { client.crypto() }
        // A configured Navasan key also refreshes USD for the portfolio's dollar total.
        if (market.isNotEmpty() || (holdings.isNotEmpty() && navasanKey.isNotBlank())) {
            update("نوسان", market) { client.navasan(navasanKey) }
        }
        if (automatic.any { it.type !in categories.filter { category -> category != AssetCategory.OTHER } }) {
            reportError("برای سایر دارایی‌ها قیمت دستی ثبت کن؛ منبع قیمت خودکار ندارند.")
        }
        if (messages.isEmpty()) messages += "دارایی با قیمت خودکار ثبت نشده است."
        return PriceUpdateResult(updated.toMap(), messages, errors, stockAssets, certificateAssets)
    }
}
