package com.darayi.app

import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

object FiatCurrencies {
    private val names = linkedMapOf(
        "USD" to "دلار آمریکا", "EUR" to "یورو", "GBP" to "پوند انگلیس", "IQD" to "دینار عراق",
        "AED" to "درهم امارات", "TRY" to "لیر ترکیه", "CAD" to "دلار کانادا", "AUD" to "دلار استرالیا",
        "CHF" to "فرانک سوئیس", "CNY" to "یوان چین", "JPY" to "ین ژاپن", "RUB" to "روبل روسیه",
        "SAR" to "ریال عربستان", "QAR" to "ریال قطر", "KWD" to "دینار کویت", "BHD" to "دینار بحرین",
        "OMR" to "ریال عمان", "AFN" to "افغانی افغانستان", "PKR" to "روپیه پاکستان", "INR" to "روپیه هند",
        "AZN" to "منات آذربایجان", "AMD" to "درام ارمنستان", "GEL" to "لاری گرجستان",
        "SEK" to "کرون سوئد", "NOK" to "کرون نروژ", "DKK" to "کرون دانمارک", "NZD" to "دلار نیوزیلند",
        "SGD" to "دلار سنگاپور", "HKD" to "دلار هنگ‌کنگ", "KRW" to "وون کره جنوبی",
        "MYR" to "رینگیت مالزی", "THB" to "بات تایلند", "BRL" to "رئال برزیل", "ZAR" to "رند آفریقای جنوبی",
        "EGP" to "پوند مصر", "JOD" to "دینار اردن", "LBP" to "لیره لبنان", "SYP" to "لیره سوریه",
        "TND" to "دینار تونس", "DZD" to "دینار الجزایر", "MAD" to "درهم مراکش", "LYD" to "دینار لیبی",
        "PLN" to "زلوتی لهستان", "CZK" to "کرون چک", "HUF" to "فورینت مجارستان",
        "RON" to "لئو رومانی", "IDR" to "روپیه اندونزی", "PHP" to "پزو فیلیپین", "VND" to "دونگ ویتنام",
        "KZT" to "تنگه قزاقستان", "UZS" to "سوم ازبکستان", "TJS" to "سامانی تاجیکستان", "TMT" to "منات ترکمنستان"
    )
    // Only currencies currently used by countries: no metals, fund units or obsolete ISO codes.
    val codes: Set<String> = Locale.getISOCountries().mapNotNull { country ->
        try { Currency.getInstance(Locale("", country))?.currencyCode } catch (_: IllegalArgumentException) { null }
    }.toSet() + names.keys

    fun name(code: String): String = names[code] ?: Currency.getInstance(code).getDisplayName(Locale("fa"))
    fun measure(code: String): String = when (code) {
        "USD" -> "دلار"; "EUR" -> "یورو"; "GBP" -> "پوند"; "AED" -> "درهم"; "IQD" -> "دینار"
        else -> name(code)
    }
    fun priority(code: String): Int = names.keys.indexOf(code).takeIf { it >= 0 } ?: names.size
}

data class CryptoSymbol(val key: String, val scale: BigDecimal)
object CryptoSymbols {
    fun resolve(ticker: String): CryptoSymbol? {
        val raw = ticker.lowercase(Locale.ROOT)
        if (raw.matches(Regex("[a-z0-9]+"))) return CryptoSymbol(symbol(raw), BigDecimal.ONE)
        val match = Regex("^(1k|100k|1m|1b)_([a-z0-9]+)$").matchEntire(raw) ?: return null
        val scale = when (match.groupValues[1]) {
            "1k" -> "1000"; "100k" -> "100000"; "1m" -> "1000000"; else -> "1000000000"
        }
        return CryptoSymbol(symbol(match.groupValues[2]), BigDecimal(scale))
    }
}

fun isCertificate(key: String) = key.startsWith("IME_")
fun isGoldWeight(key: String) = key == "IR_GOLD_18K" || key == "IR_GOLD_24K"
fun goldGrams(milligrams: String): BigDecimal? = number(milligrams)?.takeIf { it > zero }?.divide(BigDecimal(1000))

object RefreshPolicy {
    const val INTERVAL_MS = 15 * 60 * 1000L
    fun shouldRefresh(now: Long, lastAttempt: Long?): Boolean = lastAttempt == null || now - lastAttempt >= INTERVAL_MS
}
