package com.darayi.app

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale
import java.util.UUID

object AssetCategory {
 const val PHYSICAL_GOLD = "طلا (انواع فیزیکی)"
 const val CURRENCY = "ارز"
 const val STOCKS = "سهام"
 const val CRYPTO = "رمزارز"
 const val METAL_FUNDS = "صندوق طلا و نقره و مس"
 const val OTHER = "سایر دارایی‌ها"
}
val categories = listOf(AssetCategory.PHYSICAL_GOLD, AssetCategory.CURRENCY, AssetCategory.STOCKS, AssetCategory.CRYPTO, AssetCategory.METAL_FUNDS, AssetCategory.OTHER)
fun migrateCategory(type: String) = when(type) {
 "طلا و سکه" -> AssetCategory.PHYSICAL_GOLD
 "بورس ایران" -> AssetCategory.STOCKS
 else -> type
}
fun usesStockPrices(type: String) = type == AssetCategory.STOCKS || type == AssetCategory.METAL_FUNDS
val zero: BigDecimal = BigDecimal.ZERO
fun number(s: String): BigDecimal? = s.map { c -> Character.digit(c, 10).takeIf { it >= 0 }?.digitToChar() ?: c }.joinToString("").replace(",", "").replace("٬", "").replace('٫', '.').toBigDecimalOrNull()
fun symbol(s: String) = s.trim().replace('ي','ی').replace('ك','ک').replace("‌","").replace(" ","").uppercase(Locale.ROOT)
fun format(n: BigDecimal) = DecimalFormat("#,##0.########").format(n)
fun money(n: BigDecimal) = format(n.setScale(0, RoundingMode.HALF_UP))
data class Holding(val id: String = UUID.randomUUID().toString(), val name: String, val key: String, val qty: BigDecimal, val measure: String, val type: String, val manual: Boolean, val price: BigDecimal, val unit: String)
fun holdingTitle(h: Holding): String {
 val name = h.name.trim()
 val measure = h.measure.trim()
 val label = if(measure == "عدد" || measure == name || name.startsWith("$measure ")) name else "$measure $name"
 return "${format(h.qty)} $label"
}
data class Quote(val price: BigDecimal, val unit: String, val stamp: String, val change: BigDecimal? = null, val period: String = "روزانه", val sourceTime: Long = 0, val fetchedAt: Long = 0)
fun quoteFor(h: Holding, quotes: Map<String, Quote>): Quote? = if(h.manual) Quote(h.price,h.unit,"قیمت دستی") else quotes[h.key]
fun tomanValue(h: Holding, q: Quote?, fx: BigDecimal): BigDecimal? {
 if(q == null || q.price <= zero || h.qty <= zero) return null
 if(q.unit == "دلار" && fx <= zero) return null
 return h.qty * q.price * if(q.unit == "دلار") fx else BigDecimal.ONE
}
fun share(value: BigDecimal, total: BigDecimal): BigDecimal = if(total <= zero) zero else value.multiply(BigDecimal(100)).divide(total,1,RoundingMode.HALF_UP)
data class Valued(val holding: Holding, val quote: Quote?, val toman: BigDecimal?)
fun valued(items: List<Holding>, quotes: Map<String, Quote>, fx: BigDecimal) = items.map { Valued(it,quoteFor(it,quotes),tomanValue(it,quoteFor(it,quotes),fx)) }.sortedWith(compareByDescending<Valued> { it.toman != null }.thenByDescending { it.toman })
object LegacyCodec {
 fun holdings(raw: String): List<Holding> { val a=JSONArray(raw);return (0 until a.length()).map { i -> val o=a.getJSONObject(i); Holding(o.optString("id").ifBlank { "legacy-$i" },o.getString("name"),symbol(o.optString("key")),number(o.getString("qty")) ?: error("Invalid quantity"),o.optString("measure","عدد"),migrateCategory(o.optString("type",AssetCategory.OTHER)),o.optBoolean("manual"),number(o.optString("price","0"))?:zero,o.optString("unit","تومان")) } }
 fun encode(items: List<Holding>) = JSONArray().apply { items.forEach { h -> put(JSONObject().put("id",h.id).put("name",h.name).put("key",h.key).put("qty",h.qty.toPlainString()).put("measure",h.measure).put("type",h.type).put("manual",h.manual).put("price",h.price.toPlainString()).put("unit",h.unit)) } }.toString()
 fun quotes(raw:String):Map<String,Quote> { val o=JSONObject(raw);return o.keys().asSequence().associateWith { k -> val q=o.getJSONObject(k);Quote(number(q.optString("price"))?:zero,q.optString("unit"),q.optString("stamp"),number(q.optString("changePercent")),q.optString("changePeriod","روزانه"),q.optLong("sourceTime"),q.optLong("fetchedAt")) } }
 fun encodeQuotes(quotes:Map<String,Quote>) = JSONObject().apply { quotes.forEach { (key,q)->put(key,JSONObject().put("price",q.price.toPlainString()).put("unit",q.unit).put("stamp",q.stamp).put("changePercent",q.change?.toPlainString()).put("changePeriod",q.period).put("sourceTime",q.sourceTime).put("fetchedAt",q.fetchedAt)) } }.toString()
}
object PriceParser {
 fun market(raw: String):Map<String,Quote> { val o=JSONObject(raw);val result=mutableMapOf<String,Quote>();for(section in listOf("gold","currency","cryptocurrency")){val a=o.optJSONArray(section)?:continue;for(i in 0 until a.length()){val q=a.getJSONObject(i);var p=number(q.optString("price"))?:continue;val unit=when(q.optString("unit")){"ریال"->{p=p.divide(BigDecimal.TEN);"تومان"};"تومان"->"تومان";"دلار","USD","$"->"دلار";else->continue};if(p<=zero)continue;result[symbol(q.getString("symbol"))]=Quote(p,unit,"${q.optString("date")} ${q.optString("time")}",number(q.optString("change_percent")),if(section=="cryptocurrency")"۲۴ ساعت" else "روزانه",q.optLong("time_unix"),System.currentTimeMillis())}};require(result.isNotEmpty());return result }
 fun stocks(raw:String):Map<String,Quote>{val parsed=org.json.JSONTokener(raw).nextValue();val a=if(parsed is JSONArray)parsed else (parsed as JSONObject).getJSONArray("data");return (0 until a.length()).mapNotNull { i->val q=a.getJSONObject(i);val p=number(q.optString("pl"))?.divide(BigDecimal.TEN)?:return@mapNotNull null;if(p<=zero)return@mapNotNull null;symbol(q.getString("l18")) to Quote(p,"تومان","${q.optString("date","تاریخ منبع نامشخص")} ${q.optString("time")}",number(q.optString("plp")),"آخرین جلسه",fetchedAt=System.currentTimeMillis()) }.toMap().also { require(it.isNotEmpty()) }}
}
