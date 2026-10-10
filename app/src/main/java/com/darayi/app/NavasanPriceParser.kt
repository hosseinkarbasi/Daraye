package com.darayi.app

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

object NavasanPriceParser {
    // Coins are quoted in thousands of toman; gold per gram and currencies in toman.
    // Verified against both the live API and Navasan's official toman price table.
    private val precious = mapOf(
        "18ayar" to ("IR_GOLD_18K" to BigDecimal.ONE),
        "sekkeh" to ("IR_COIN_EMAMI" to BigDecimal(1000)),
        "bahar" to ("IR_COIN_BAHAR" to BigDecimal(1000)),
        "nim" to ("IR_COIN_HALF" to BigDecimal(1000)),
        "rob" to ("IR_COIN_QUARTER" to BigDecimal(1000))
    )
    private val currencies = Currency.getAvailableCurrencies()
        .map { it.currencyCode.lowercase(Locale.ROOT) }
        .filterNot { it in setOf("xau", "xag", "xpt", "xpd", "xxx", "xts") }
        .toSet()

    fun latest(raw: String): Map<String, Quote> {
        val root = JSONObject(raw)
        val now = System.currentTimeMillis()
        val result = mutableMapOf<String, Quote>()
        fun read(item: String, key: String, multiplier: BigDecimal) {
            val row = root.optJSONObject(item) ?: return
            val value = number(row.optString("value"))?.takeIf { it > zero } ?: return
            val delta = number(row.optString("change"))
            val previous = delta?.let { value - it }
            val percent = if (delta != null && previous != null && previous > zero) {
                delta.multiply(BigDecimal(100)).divide(previous, 4, RoundingMode.HALF_UP)
            } else null
            result[key] = Quote(
                price = value.multiply(multiplier),
                unit = "تومان",
                stamp = "نوسان · ${row.optString("date").ifBlank { "زمان منبع نامشخص" }}",
                change = percent,
                period = "روزانه",
                sourceTime = row.optLong("timestamp"),
                fetchedAt = now
            )
        }
        for (item in currencies) read(item, symbol(item), BigDecimal.ONE)
        read("usd_sell", "USD", BigDecimal.ONE)
        read("aed_sell", "AED", BigDecimal.ONE)
        for ((item, mapping) in precious) read(item, mapping.first, mapping.second)
        require(result.isNotEmpty())
        return result
    }
}
