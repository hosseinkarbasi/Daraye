package com.darayi.app

data class PriceUpdateResult(val quotes: Map<String, Quote>, val messages: List<String>, val errors: List<String>, val stockAssets: List<AssetOption> = emptyList(), val certificateAssets: List<AssetOption> = emptyList(), val states: Map<PriceSource, SourceState> = emptyMap())

class PriceUpdater(private val client: PriceApiClient = PriceApiClient()) {
    fun refresh(
        holdings: List<Holding>,
        previous: Map<String, Quote>,
        stockKey: String,
        navasanKey: String,
        previousStates: Map<PriceSource, SourceState> = emptyMap(),
        now: Long = System.currentTimeMillis()
    ): PriceUpdateResult {
        val automatic = holdings.filter { !it.manual }
        val updated = previous.toMutableMap()
        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val states = previousStates.toMutableMap()
        var stockAssets = emptyList<AssetOption>()
        var certificateAssets = emptyList<AssetOption>()
        fun reportError(message: String) {
            messages += message
            errors += message
        }
        fun update(source: PriceSource, required: List<Holding>, fetch: () -> Map<String, Quote>) {
            val name = source.label
            val oldState = states[source] ?: SourceState(source)
            try {
                val prices = fetch()
                updated.putAll(prices)
                messages += "قیمت‌های $name دریافت شد."
                val requiredKeys = required.map { it.key } + if (source == PriceSource.NAVASAN && holdings.isNotEmpty()) listOf("USD") else emptyList()
                val missing = requiredKeys.distinct().filter { it !in prices }
                var warning: String? = null
                if (missing.isNotEmpty()) {
                    warning = "$name: قیمت معتبر برای ${missing.joinToString("، ")} دریافت نشد؛ قیمت‌های قبلی این نمادها به‌روزرسانی نشدند."
                    reportError(warning)
                }
                states[source] = SourceState(source, now, now, warning, prices.keys)
            } catch (e: PriceApiException) {
                val warning = "$name: ${e.message} قیمت‌های قبلی حفظ شد."
                reportError(warning)
                states[source] = oldState.copy(lastAttemptAt = now, error = warning)
            }
        }
        val stocks = automatic.filter { usesStockPrices(it.type) && !isCertificate(it.key) }
        val certificates = automatic.filter { usesStockPrices(it.type) && isCertificate(it.key) }
        val crypto = automatic.filter { it.type == AssetCategory.CRYPTO }
        val market = automatic.filter { it.type == AssetCategory.PHYSICAL_GOLD || it.type == AssetCategory.CURRENCY }
        if (stocks.isNotEmpty()) update(PriceSource.STOCKS, stocks) {
            client.stockMarket(stockKey).also { stockAssets = it.assets }.quotes
        }
        if (certificates.isNotEmpty()) update(PriceSource.CERTIFICATES, certificates) {
            client.certificateMarket(stockKey).also { certificateAssets = it.assets }.quotes
        }
        if (crypto.isNotEmpty()) update(PriceSource.CRYPTO, crypto) { client.crypto() }
        // A configured Navasan key also refreshes USD for the portfolio's dollar total.
        if (market.isNotEmpty() || (holdings.isNotEmpty() && navasanKey.isNotBlank())) {
            update(PriceSource.NAVASAN, market) { client.navasan(navasanKey) }
        }
        if (automatic.any { it.type !in categories.filter { category -> category != AssetCategory.OTHER } }) {
            reportError("برای سایر دارایی‌ها قیمت دستی ثبت کن؛ منبع قیمت خودکار ندارند.")
        }
        if (messages.isEmpty()) messages += "دارایی با قیمت خودکار ثبت نشده است."
        return PriceUpdateResult(updated.toMap(), messages, errors, stockAssets, certificateAssets, states)
    }
}
