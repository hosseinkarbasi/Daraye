package com.darayi.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigDecimal

fun certificateRows(raw: String): JSONArray {
    val root = JSONTokener(raw).nextValue()
    if (root is JSONArray) return root
    root as JSONObject
    require(!root.has("successful") || root.optBoolean("successful"))
    return root.getJSONArray("data")
}

object CertificatePriceParser {
    fun prices(raw: String): Map<String, Quote> {
        val rows = certificateRows(raw)
        val now = System.currentTimeMillis()
        val result = (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val code = row.optString("contract_code").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val amount = number(row.optString("pl"))?.takeIf { it > zero } ?: return@mapNotNull null
            val price = when (row.optString("contract_currency")) {
                "ریال" -> amount.divide(BigDecimal.TEN)
                "تومان" -> amount
                else -> return@mapNotNull null
            }
            "IME_${symbol(code)}" to Quote(price, "تومان", "بورس کالا · ${row.optString("date_update")} ${row.optString("time_update")}",
                number(row.optString("plp")), "آخرین جلسه", fetchedAt = now)
        }.toMap()
        require(result.isNotEmpty())
        // Price is per certificate. Contract conversion factors are not a holding's gram quantity.
        return result
    }
}
