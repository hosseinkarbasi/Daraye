package com.darayi.app

import java.math.BigDecimal
import java.util.UUID

data class HoldingDraft(
    val id: String = UUID.randomUUID().toString(), val name: String = "", val key: String = "",
    val category: String = AssetCategory.PHYSICAL_GOLD, val measure: String = "", val quantity: String = "",
    val grams: String = "", val milligrams: String = "", val manual: Boolean = false,
    val price: String = "", val priceUnit: String = "تومان"
) {
    fun changeCategory(value: String) = if (category == value) this else HoldingDraft(id = id, category = value, manual = value == AssetCategory.OTHER)
    fun search(value: String) = copy(name = value, key = "", measure = "")
    fun select(asset: AssetOption) = copy(name = asset.name, key = asset.key, category = asset.category,
        measure = asset.measure, manual = asset.manualOnly || (key == asset.key && manual),
        quantity = if (isGoldWeight(key) == isGoldWeight(asset.key)) quantity else "", price = if (key == asset.key) price else "")
    fun withPreviousMilligrams(old: Holding): HoldingDraft {
        val converted = old.qty.divide(BigDecimal(1000))
        return copy(grams = converted.toBigInteger().toString(), milligrams = converted.remainder(BigDecimal.ONE).multiply(BigDecimal(1000)).stripTrailingZeros().toPlainString())
    }
    fun validate(): DraftResult {
        if (name.isBlank()) return DraftResult(error = "نام دارایی را وارد کن.")
        if (category != AssetCategory.OTHER && key.isBlank()) return DraftResult(error = "دارایی را از نتایج جست‌وجو انتخاب کن.")
        val amount = if (isGoldWeight(key)) combinedGoldGrams(grams, milligrams) else number(quantity)?.takeIf { it > zero }
        if (amount == null) return DraftResult(error = if (isGoldWeight(key)) "گرم را صحیح و میلی‌گرم را بین صفر تا ۹۹۹ وارد کن؛ وزن باید بیشتر از صفر باشد." else "مقدار دارایی باید بیشتر از صفر باشد.")
        val manualPrice = if (manual || category == AssetCategory.OTHER || key == "IR_GOLD_24K") number(price)?.takeIf { it > zero } else zero
        if (manualPrice == null) return DraftResult(error = "قیمت معتبر و بیشتر از صفر وارد کن.")
        return DraftResult(holding = Holding(id, name.trim(), symbol(key), amount, if (isGoldWeight(key)) "گرم" else measure.ifBlank { "عدد" },
            category, manual || category == AssetCategory.OTHER || key == "IR_GOLD_24K", manualPrice, priceUnit))
    }
    companion object {
        fun from(h: Holding?): HoldingDraft {
            if (h == null) return HoldingDraft()
            val gold = isGoldWeight(h.key)
            return HoldingDraft(h.id, h.name, h.key, h.type, h.measure, if (gold) "" else h.qty.toPlainString(),
                if (gold) h.qty.toBigInteger().toString() else "", if (gold) h.qty.remainder(BigDecimal.ONE).multiply(BigDecimal(1000)).stripTrailingZeros().toPlainString() else "",
                h.manual, h.price.takeIf { it > zero }?.toPlainString() ?: "", h.unit)
        }
    }
}
data class DraftResult(val holding: Holding? = null, val error: String? = null)
fun combinedGoldGrams(grams: String, milligrams: String): BigDecimal? {
    val g = if (grams.isBlank()) zero else number(grams) ?: return null
    val mg = if (milligrams.isBlank()) zero else number(milligrams) ?: return null
    if (g < zero || g.stripTrailingZeros().scale() > 0 || mg < zero || mg >= BigDecimal(1000)) return null
    return (g + mg.divide(BigDecimal(1000))).takeIf { it > zero }
}
