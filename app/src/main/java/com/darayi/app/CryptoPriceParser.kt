package com.darayi.app

import org.json.JSONObject
import java.math.BigDecimal
import java.text.DateFormat
import java.util.Date

object CryptoPriceParser {
    fun nobitex(raw: String): Map<String, Quote> {
        val root = JSONObject(raw)
        require(root.optString("status") == "ok")
        val stats = root.getJSONObject("stats")
        val now = System.currentTimeMillis()
        val received = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(now))
        val result = mutableMapOf<String, Quote>()
        for (market in stats.keys()) {
            val parts = market.split('-')
            if (parts.size != 2 || parts[1] != "rls") continue
            val asset = CryptoSymbols.resolve(parts[0]) ?: continue
            val row = stats.optJSONObject(market) ?: continue
            if (row.optBoolean("isClosed")) continue
            val rial = number(row.optString("latest"))?.takeIf { it > zero } ?: continue
            result[asset.key] = Quote(
                price = rial.divide(BigDecimal.TEN).divide(asset.scale),
                unit = "تومان",
                stamp = "نوبیتکس · زمان دریافت: $received",
                change = number(row.optString("dayChange")),
                period = "۲۴ ساعت",
                fetchedAt = now
            )
        }
        require(result.isNotEmpty())
        return result
    }
}
