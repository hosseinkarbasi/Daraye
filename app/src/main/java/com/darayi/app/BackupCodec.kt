package com.darayi.app

import org.json.JSONArray
import org.json.JSONObject

data class PortfolioBackup(val holdings: List<Holding>, val manualFx: String = "")
object BackupCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    fun encode(holdings: List<Holding>, manualFx: String, now: Long = System.currentTimeMillis()): String = JSONObject()
        .put("format", "darayi.portfolio").put("version", 1).put("exportedAt", now)
        .put("holdings", JSONArray(LegacyCodec.encode(holdings))).put("manualUsd", manualFx).toString(2)

    fun decode(raw: String): PortfolioBackup {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "فایل پشتیبان بیش از حد بزرگ است." }
        val root = JSONObject(raw)
        require(root.optString("format") == "darayi.portfolio" && root.optInt("version") == 1) { "قالب یا نسخهٔ فایل پشتیبان پشتیبانی نمی‌شود." }
        val array = root.getJSONArray("holdings")
        require(array.length() <= 10000)
        val holdings = LegacyCodec.holdings(array.toString())
        require(holdings.distinctBy { it.id }.size == holdings.size)
        holdings.forEach {
            require(it.id.isNotBlank() && it.id.length <= 256 && it.name.isNotBlank() && it.name.length <= 512 && it.key.length <= 256)
            require(it.qty > zero && it.qty.precision() <= 60 && it.qty.scale() in -12..24)
            require(it.price >= zero && it.price.precision() <= 60 && it.price.scale() in -12..24)
            require(it.type in categories && it.unit in setOf("تومان", "دلار"))
            require(!it.manual || it.price > zero)
        }
        val fx = root.optString("manualUsd").takeIf { it.isBlank() || number(it)?.let { n -> n > zero } == true } ?: error("نرخ دلار فایل نامعتبر است.")
        return PortfolioBackup(holdings, fx)
    }
    fun merge(current: List<Holding>, backup: PortfolioBackup): List<Holding> {
        val existing = current.map { it.id }.toSet()
        return current + backup.holdings.filter { it.id !in existing }
    }
}
