package com.darayi.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Currency
import java.util.Locale

data class AssetOption(
    val key: String,
    val name: String,
    val category: String,
    val measure: String,
    val details: String = "",
    val manualOnly: Boolean = false
)

data class StockMarketData(val quotes: Map<String, Quote>, val assets: List<AssetOption>)

object AssetCatalog {
    val physicalGold = listOf(
        AssetOption("IR_GOLD_18K", "طلای ۱۸ عیار", AssetCategory.PHYSICAL_GOLD, "گرم"),
        AssetOption("IR_GOLD_24K", "طلای ۲۴ عیار", AssetCategory.PHYSICAL_GOLD, "گرم", manualOnly = true),
        AssetOption("IR_COIN_EMAMI", "سکه امامی", AssetCategory.PHYSICAL_GOLD, "عدد"),
        AssetOption("IR_COIN_BAHAR", "سکه بهار آزادی", AssetCategory.PHYSICAL_GOLD, "عدد"),
        AssetOption("IR_COIN_HALF", "نیم سکه", AssetCategory.PHYSICAL_GOLD, "عدد"),
        AssetOption("IR_COIN_QUARTER", "ربع سکه", AssetCategory.PHYSICAL_GOLD, "عدد")
    )

    fun stocks(raw: String): List<AssetOption> {
        val root = JSONTokener(raw).nextValue()
        val rows = if (root is JSONArray) root else (root as JSONObject).getJSONArray("data")
        val result = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val ticker = row.optString("l18").replace("\\u200c", "‌").trim()
            val key = symbol(ticker)
            if (key.isBlank()) return@mapNotNull null
            val fullName = row.optString("l30").replace("\\u200c", "‌").trim()
            val sector = row.optString("cs")
            val fund = sector.contains("صندوق") || fullName.contains("صندوق")
            val isin = row.optString("isin")
            // IRTK is the commodity ETF prefix; the known saffron funds are not metals.
            val commodityFund = isin.startsWith("IRTK") && key.trimEnd { it.isDigit() } !in setOf("نهال", "سافرون")
            val namedMetalFund = fullName.contains("طلا") || fullName.contains("نقره") || Regex("(^|\\s)مس($|\\s)").containsMatchIn(fullName)
            val metalFund = fund && (commodityFund || namedMetalFund)
            val measure = when {
                fund -> "واحد"
                sector.contains("اختیار") -> "قرارداد"
                sector.contains("اوراق") -> "ورقه"
                else -> "سهم"
            }
            AssetOption(key, ticker, if (metalFund) AssetCategory.METAL_FUNDS else AssetCategory.STOCKS, measure, fullName)
        }.distinctBy { it.key }
        require(result.isNotEmpty())
        return result
    }

    fun search(assets: List<AssetOption>, category: String, query: String, limit: Int = 20): List<AssetOption> {
        val term = symbol(query)
        if (term.isEmpty()) return assets.asSequence().filter { it.category == category }.take(limit).toList()
        fun rank(asset: AssetOption): Int = when {
            symbol(asset.key) == term || symbol(asset.name) == term -> 0
            symbol(asset.key).startsWith(term) || symbol(asset.name).startsWith(term) -> 1
            else -> 2
        }
        return assets.asSequence().filter { it.category == category }
            .filter { term.isEmpty() || symbol(it.key).contains(term) || symbol(it.name).contains(term) || symbol(it.details).contains(term) }
            .sortedWith(compareBy<AssetOption> { rank(it) }.thenBy { it.key.length }.thenBy { it.name })
            .take(limit).toList()
    }

    fun encode(assets: List<AssetOption>): String = JSONArray().apply {
        assets.forEach { asset ->
            put(JSONObject().put("key", asset.key).put("name", asset.name).put("category", asset.category)
                .put("measure", asset.measure).put("details", asset.details).put("manualOnly", asset.manualOnly))
        }
    }.toString()

    fun decode(raw: String): List<AssetOption> {
        val rows = JSONArray(raw)
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            AssetOption(symbol(row.getString("key")), row.getString("name"), row.getString("category"),
                row.getString("measure"), row.optString("details"), row.optBoolean("manualOnly"))
        }
    }

    fun localizeMarketAssets(assets: List<AssetOption>): List<AssetOption> = assets.mapNotNull { asset ->
        if (asset.category != AssetCategory.CURRENCY) return@mapNotNull asset
        if (asset.key !in FiatCurrencies.codes) return@mapNotNull null
        val currency = Currency.getInstance(asset.key)
        asset.copy(name = FiatCurrencies.name(asset.key), measure = FiatCurrencies.measure(asset.key),
            details = "${asset.key} ${currency.getDisplayName(Locale.ENGLISH)}")
    }.sortedWith(compareBy<AssetOption> { if (it.category == AssetCategory.CURRENCY) 0 else 1 }
        .thenBy { if (it.category == AssetCategory.CURRENCY) FiatCurrencies.priority(it.key) else 0 })

    fun crypto(raw: String): List<AssetOption> {
        val root = JSONObject(raw)
        require(root.optString("status") == "ok")
        val coins = root.getJSONArray("coins")
        val familiar = mapOf("BTC" to "بیت‌کوین", "ETH" to "اتریوم", "USDT" to "تتر", "SOL" to "سولانا",
            "DOGE" to "دوج‌کوین", "SHIB" to "شیبا اینو", "PEPE" to "پپه", "BTT" to "بیت‌تورنت",
            "BABYDOGE" to "بیبی دوج", "FLOKI" to "فلوکی", "BONK" to "بونک", "XRP" to "ریپل",
            "ADA" to "کاردانو", "TRX" to "ترون", "TON" to "تون کوین", "BNB" to "بایننس کوین",
            "LTC" to "لایت‌کوین", "BCH" to "بیت‌کوین کش", "AVAX" to "آوالانچ", "DOT" to "پولکادات",
            "LINK" to "چین‌لینک", "XLM" to "استلار", "XMR" to "مونرو", "ETC" to "اتریوم کلاسیک")
        val result = (0 until coins.length()).mapNotNull { i ->
            val row = coins.optJSONObject(i) ?: return@mapNotNull null
            val ticker = row.optString("coin")
            if (ticker in setOf("rls", "irt", "usd")) return@mapNotNull null
            val asset = CryptoSymbols.resolve(ticker) ?: return@mapNotNull null
            val name = familiar[asset.key] ?: row.optString("name").ifBlank { asset.key }
            AssetOption(asset.key, name, AssetCategory.CRYPTO, "عدد", "${asset.key} ${row.optString("name")}")
        }.distinctBy { it.key }
        require(result.isNotEmpty())
        return result.sortedWith(compareBy<AssetOption> { familiar.keys.indexOf(it.key).takeIf { n -> n >= 0 } ?: familiar.size }.thenBy { it.key })
    }

    fun certificates(raw: String): List<AssetOption> {
        val rows = certificateRows(raw)
        val assets = (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val code = row.optString("contract_code").trim()
            if (code.isBlank()) return@mapNotNull null
            val description = row.optString("contract_description")
            AssetOption("IME_${symbol(code)}", "گواهی ${row.optString("commodity").ifBlank { code }}",
                AssetCategory.STOCKS, "گواهی", "$description · $code")
        }.distinctBy { it.key }
        require(assets.isNotEmpty())
        return assets
    }

    fun additionsFromQuotes(quotes: Map<String, Quote>): List<AssetOption> = quotes.mapNotNull { (key, quote) ->
        when {
            quote.stamp.startsWith("نوبیتکس") -> AssetOption(key, key, AssetCategory.CRYPTO, "عدد")
            quote.stamp.startsWith("نوسان") && !key.startsWith("IR_") ->
                localizeMarketAssets(listOf(AssetOption(key, key, AssetCategory.CURRENCY, key))).firstOrNull()
            else -> null
        }
    }
}
