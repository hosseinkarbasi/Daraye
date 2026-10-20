package com.darayi.app

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.darayi.app.storage.*
import org.json.JSONArray

data class PortfolioSnapshot(val holdings: List<Holding>, val quotes: Map<String, Quote>, val states: Map<PriceSource, SourceState>, val manualFx: String)
class PortfolioRepository(private val db: PortfolioDatabase, private val legacy: SharedPreferences) {
    companion object {
        @Volatile private var database: PortfolioDatabase? = null
        fun create(context: Context): PortfolioRepository {
            val db = database ?: synchronized(this) { database ?: Room.databaseBuilder(context.applicationContext, PortfolioDatabase::class.java, "portfolio.db").build().also { database = it } }
            return PortfolioRepository(db, context.getSharedPreferences("portfolio", 0))
        }
    }
    private val dao get() = db.portfolio()
    fun load(): PortfolioSnapshot {
        db.runInTransaction {
            if (dao.meta("legacy_imported") == null) {
                // Parse everything before touching the database. A malformed legacy portfolio stays recoverable.
                val holdings = LegacyCodec.holdings(legacy.getString("holdings", "[]") ?: "[]")
                val quotes = runCatching { LegacyCodec.quotes(legacy.getString("quotes", "{}") ?: "{}") }.getOrDefault(emptyMap())
                check(holdings.distinctBy { it.id }.size == holdings.size)
                holdings.forEachIndexed { i, h -> dao.putHolding(entity(h, i)) }
                quotes.forEach { (key, quote) -> dao.putQuote(entity(key, quote)) }
                putMeta("manual_usd", legacy.getString("usd", "") ?: "")
                putMeta("legacy_imported", "1")
            }
        }
        return snapshot()
    }
    fun snapshot(): PortfolioSnapshot = db.runInTransaction(java.util.concurrent.Callable { snapshotUnsafe() })
    private fun snapshotUnsafe(): PortfolioSnapshot = PortfolioSnapshot(dao.holdings().map(::holding), dao.quotes().associate { it.symbol to quote(it) },
        dao.sources().associate { it.id.let(PriceSource::valueOf) to SourceState(PriceSource.valueOf(it.id), it.lastAttemptAt, it.lastSuccessAt, it.error,
            JSONArray(it.availableKeys).let { a -> (0 until a.length()).map(a::getString).toSet() }) }, dao.meta("manual_usd")?.value ?: "")

    fun save(h: Holding) { db.runInTransaction { dao.putHolding(entity(h, dao.holding(h.id)?.position ?: ((dao.holdings().maxOfOrNull { it.position } ?: -1) + 1))) } }
    fun remove(id: String): Int? {
        var position: Int? = null
        db.runInTransaction { position = dao.holding(id)?.position; dao.deleteHolding(id) }
        return position
    }
    fun undo(h: Holding, position: Int) { db.runInTransaction { if (dao.holding(h.id) == null) dao.putHolding(entity(h, position)) } }
    fun cache(quotes: Map<String, Quote>, states: Map<PriceSource, SourceState>) {
        db.runInTransaction {
            val oldQuotes = dao.quotes().associateBy { it.symbol }
            val oldStates = dao.sources().associateBy { it.id }
            quotes.forEach { (key, q) ->
                val source = when {
                    q.stamp.startsWith("نوبیتکس") -> PriceSource.CRYPTO
                    q.stamp.startsWith("نوسان") -> PriceSource.NAVASAN
                    isCertificate(key) -> PriceSource.CERTIFICATES
                    else -> states.values.firstOrNull { key in it.availableKeys }?.source
                }
                val generation = source?.let(states::get)?.lastAttemptAt
                val oldGeneration = source?.let { oldStates[it.name]?.lastAttemptAt }
                val previous = oldQuotes[key]
                val olderRequest = generation != null && oldGeneration != null && generation < oldGeneration
                val olderSource = previous != null && q.sourceTime > 0 && previous.sourceTime > 0 && sourceTimeMillis(q) < sourceTimeMillis(quote(previous))
                if (!olderRequest && !olderSource && q.fetchedAt >= (previous?.fetchedAt ?: 0)) dao.putQuote(entity(key, q))
            }
            states.values.filter { it.lastAttemptAt >= (oldStates[it.source.name]?.lastAttemptAt ?: 0) }.forEach { s -> dao.putSource(SourceEntity().apply {
                id = s.source.name; lastAttemptAt = s.lastAttemptAt; lastSuccessAt = s.lastSuccessAt; error = s.error; availableKeys = JSONArray(s.availableKeys.toList()).toString()
            }) }
        }
    }
    fun settings(fx: String) = putMeta("manual_usd", fx)
    fun importBackup(backup: PortfolioBackup): Int {
        var added = 0
        db.runInTransaction {
            val existing = dao.holdings(); val keys = existing.map { it.id }.toSet()
            var position = (existing.maxOfOrNull { it.position } ?: -1) + 1
            backup.holdings.filter { it.id !in keys }.forEach { dao.putHolding(entity(it, position++)); added++ }
            if (dao.meta("manual_usd")?.value.isNullOrBlank() && backup.manualFx.isNotBlank()) putMeta("manual_usd", backup.manualFx)
        }
        return added
    }
    private fun putMeta(key: String, text: String) = dao.putMeta(MetaEntity().apply { id = key; value = text })
    private fun entity(h: Holding, index: Int) = HoldingEntity().apply {
        id = h.id; name = h.name; symbol = h.key; quantity = h.qty.toPlainString(); measure = h.measure; category = h.type
        manual = h.manual; price = h.price.toPlainString(); priceUnit = h.unit; position = index
    }
    private fun holding(h: HoldingEntity) = Holding(h.id, h.name, h.symbol, h.quantity.toBigDecimal(), h.measure, h.category, h.manual, h.price.toBigDecimal(), h.priceUnit)
    private fun entity(key: String, q: Quote) = QuoteEntity().apply {
        symbol = key; price = q.price.toPlainString(); unit = q.unit; stamp = q.stamp; changePercent = q.change?.toPlainString()
        period = q.period; sourceTime = q.sourceTime; fetchedAt = q.fetchedAt
    }
    private fun quote(q: QuoteEntity) = Quote(q.price.toBigDecimal(), q.unit, q.stamp, q.changePercent?.toBigDecimal(), q.period, q.sourceTime, q.fetchedAt)
}
