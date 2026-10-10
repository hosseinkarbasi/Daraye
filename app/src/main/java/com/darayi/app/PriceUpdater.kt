package com.darayi.app

data class PriceUpdateResult(val quotes: Map<String, Quote>, val messages: List<String>)

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
        fun update(name: String, required: List<Holding>, fetch: () -> Map<String, Quote>) {
            try {
                val prices = fetch()
                updated.putAll(prices)
                messages += "قیمت‌های $name دریافت شد."
                val missing = required.map { it.key }.distinct().filter { it !in prices }
                if (missing.isNotEmpty()) {
                    messages += "$name: قیمت معتبر برای ${missing.joinToString("، ")} دریافت نشد؛ قیمت‌های قبلی این نمادها به‌روزرسانی نشدند."
                }
            } catch (e: PriceApiException) {
                messages += "$name: ${e.message} قیمت‌های قبلی حفظ شد."
            }
        }
        val stocks = automatic.filter { it.type == categories[2] }
        val crypto = automatic.filter { it.type == categories[3] }
        val market = automatic.filter { it.type == categories[0] || it.type == categories[1] }
        if (stocks.isNotEmpty()) update("بورس", stocks) { client.stocks(stockKey) }
        if (crypto.isNotEmpty()) update("نوبیتکس", crypto) { client.crypto() }
        // A configured Navasan key also refreshes USD for the portfolio's dollar total.
        if (market.isNotEmpty() || (holdings.isNotEmpty() && navasanKey.isNotBlank())) {
            update("نوسان", market) { client.navasan(navasanKey) }
        }
        if (automatic.any { it.type !in categories.take(4) }) {
            messages += "برای سایر دارایی‌ها قیمت دستی ثبت کن؛ منبع قیمت خودکار ندارند."
        }
        if (messages.isEmpty()) messages += "دارایی با قیمت خودکار ثبت نشده است."
        return PriceUpdateResult(updated.toMap(), messages)
    }
}
