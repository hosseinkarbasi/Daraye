package com.darayi.app

import java.math.BigDecimal

enum class PriceSource(val label: String) {
    STOCKS("سهام و صندوق‌ها"), CERTIFICATES("گواهی‌های بورس کالا"), CRYPTO("نوبیتکس"), NAVASAN("نوسان")
}

data class SourceState(
    val source: PriceSource,
    val lastAttemptAt: Long = 0,
    val lastSuccessAt: Long = 0,
    val error: String? = null,
    val availableKeys: Set<String> = emptySet()
)

fun sourceFor(h: Holding): PriceSource? = if (h.manual) null else sourceFor(h.type, h.key)
fun sourceFor(category: String, key: String): PriceSource? = when {
    usesStockPrices(category) && isCertificate(key) -> PriceSource.CERTIFICATES
    usesStockPrices(category) -> PriceSource.STOCKS
    category == AssetCategory.CRYPTO -> PriceSource.CRYPTO
    category == AssetCategory.PHYSICAL_GOLD || category == AssetCategory.CURRENCY -> PriceSource.NAVASAN
    else -> null
}

const val PRICE_FRESH_MS = 15 * 60 * 1000L
fun isFresh(time: Long, now: Long) = time > 0 && now >= time && now - time <= PRICE_FRESH_MS
fun sourceTimeMillis(q: Quote): Long = if (q.sourceTime in 1..999_999_999_999L) q.sourceTime * 1000 else q.sourceTime
fun quoteIsFresh(q: Quote, now: Long): Boolean {
    val sourceTime = sourceTimeMillis(q)
    // Known source timestamps must also be recent; allow the provider's two-hour update cadence.
    return isFresh(q.fetchedAt, now) && (sourceTime <= 0 || (sourceTime <= now + 300_000 && now - sourceTime <= 3 * 60 * 60 * 1000L))
}
enum class Availability { ACTIVE, UNCHECKED, UNAVAILABLE }
fun availability(asset: AssetOption, states: Map<PriceSource, SourceState>, now: Long): Availability {
    if (asset.manualOnly) return Availability.UNAVAILABLE
    val state = states[sourceFor(asset.category, asset.key)] ?: return Availability.UNCHECKED
    if (state.error != null || !isFresh(state.lastSuccessAt, now)) return Availability.UNCHECKED
    return if (asset.key in state.availableKeys) Availability.ACTIVE else Availability.UNAVAILABLE
}

enum class FxKind { FRESH, CACHED, MANUAL, MISSING }
data class FxSelection(val rate: BigDecimal, val kind: FxKind, val fetchedAt: Long = 0)
fun selectFx(quotes: Map<String, Quote>, manual: String, now: Long): FxSelection {
    val cached = quotes["USD"]?.takeIf { it.unit == "تومان" && it.price > zero }
    if (cached != null && quoteIsFresh(cached, now)) return FxSelection(cached.price, FxKind.FRESH, cached.fetchedAt)
    number(manual)?.takeIf { it > zero }?.let { return FxSelection(it, FxKind.MANUAL) }
    if (cached != null) return FxSelection(cached.price, FxKind.CACHED, cached.fetchedAt)
    return FxSelection(zero, FxKind.MISSING)
}

enum class PriceHealth { FRESH, CACHED, PARTIAL, MANUAL }
fun priceHealth(holdings: List<Holding>, quotes: Map<String, Quote>, states: Map<PriceSource, SourceState>, fx: FxSelection, now: Long, includeFxSource: Boolean = false): PriceHealth {
    val automatic = holdings.filterNot { it.manual }
    if (automatic.any { sourceFor(it) == null }) return PriceHealth.PARTIAL
    if (includeFxSource && holdings.isNotEmpty() && states[PriceSource.NAVASAN]?.error != null) return PriceHealth.PARTIAL
    if (automatic.any { quotes[it.key] == null || quotes[it.key]!!.price <= zero || states[sourceFor(it)]?.error != null }) return PriceHealth.PARTIAL
    if (automatic.any { quotes[it.key]?.let { q -> !quoteIsFresh(q, now) } != false } || fx.kind == FxKind.CACHED) return PriceHealth.CACHED
    return if (automatic.isEmpty()) PriceHealth.MANUAL else PriceHealth.FRESH
}

enum class HoldingSort(val label: String) { VALUE("بیشترین ارزش"), NAME("نام دارایی"), QUANTITY("بیشترین مقدار") }
fun filterHoldings(rows: List<Valued>, query: String, category: String?, sort: HoldingSort): List<Valued> {
    val term = symbol(query)
    val filtered = rows.filter { (category == null || it.holding.type == category) &&
        (term.isBlank() || symbol(it.holding.name).contains(term) || symbol(it.holding.key).contains(term)) }
    return when (sort) {
        HoldingSort.VALUE -> filtered
        HoldingSort.NAME -> filtered.sortedBy { it.holding.name }
        HoldingSort.QUANTITY -> filtered.sortedByDescending { it.holding.qty }
    }
}
