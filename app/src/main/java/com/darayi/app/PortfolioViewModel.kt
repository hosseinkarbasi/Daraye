package com.darayi.app

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

// UI state and orchestration only; persistence, validation and price policies live outside the view model.
class PortfolioViewModel(app: Application): AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("portfolio", 0)
    private val repository = PortfolioRepository.create(app)
    private val keys = SecureKeys(app)
    private val client = PriceApiClient()
    private val updater = PriceUpdater(client)
    private val changes = Mutex()
    private val loadedCatalogs = mutableSetOf<PriceSource>()
    private var lastRefreshAttempt: Long? = null
    private var refreshAfterSave = false
    private var refreshOnReady = false
    var isLoading by mutableStateOf(true); private set
    var saving by mutableStateOf(false); private set
    var fileBusy by mutableStateOf(false); private set
    var loadFailed by mutableStateOf(false); private set
    var assetCatalog by mutableStateOf(AssetCatalog.physicalGold); private set
    var catalogBusy by mutableStateOf(false); private set
    var catalogError by mutableStateOf<String?>(null); private set
    var holdings by mutableStateOf(emptyList<Holding>()); private set
    var quotes by mutableStateOf(emptyMap<String, Quote>()); private set
    var sourceStates by mutableStateOf(emptyMap<PriceSource, SourceState>()); private set
    var apiKey by mutableStateOf(""); private set
    var navasanKey by mutableStateOf(""); private set
    var manualFx by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var pendingUndo by mutableStateOf<RemovedHolding?>(null); private set
    var backupPreview by mutableStateOf<PortfolioBackup?>(null); private set
    val refreshError get() = sourceStates.values.mapNotNull { it.error }.takeIf { it.isNotEmpty() }?.joinToString("\n")
    val fx get() = selectFx(quotes, manualFx, System.currentTimeMillis()).rate

    init {
        viewModelScope.launch {
            try {
                val initial = withContext(Dispatchers.IO) { repository.load() }
                apply(initial)
                try {
                    val credentials = withContext(Dispatchers.IO) { keys.load() }
                    apiKey = credentials.stocks; navasanKey = credentials.navasan
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = "کلیدهای منبع قیمت خوانده نشدند؛ دوباره در تنظیمات وارد کن. دارایی‌ها حفظ شده‌اند." }
                try {
                    assetCatalog = withContext(Dispatchers.IO) { loadCatalogs(app) }
                } catch (_: Exception) { catalogError = "فهرست دارایی‌ها کامل خوانده نشد؛ دوباره تلاش کن." }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { loadFailed = true; message = "خواندن دارایی‌های قبلی ناموفق بود؛ اطلاعات قبلی حفظ شده و ذخیرهٔ جدید متوقف است." }
            finally { isLoading = false }
            if (refreshOnReady && !loadFailed) { refreshOnReady = false; refresh(automatic = true) }
        }
    }
    private fun apply(snapshot: PortfolioSnapshot) {
        holdings = snapshot.holdings; quotes = snapshot.quotes; sourceStates = snapshot.states; manualFx = snapshot.manualFx
    }
    private fun loadCatalogs(app: Application): List<AssetOption> {
        fun read(name: String) = app.assets.open(name).bufferedReader().use { it.readText() }
        var result = AssetCatalog.physicalGold + AssetCatalog.stocks(read("stock_catalog.json")) +
            AssetCatalog.localizeMarketAssets(AssetCatalog.decode(read("market_catalog.json"))) +
            AssetCatalog.crypto(read("crypto_catalog.json")) + AssetCatalog.certificates(read("certificate_catalog.json"))
        for ((name, source) in listOf("stockAssetCatalog" to PriceSource.STOCKS, "cryptoAssetCatalog" to PriceSource.CRYPTO, "certificateAssetCatalog" to PriceSource.CERTIFICATES)) {
            val cached = runCatching { prefs.getString(name, null)?.let(AssetCatalog::decode) }.getOrNull()
            if (!cached.isNullOrEmpty()) result = result.filterNot { sourceFor(it.category, it.key) == source } + cached
        }
        return result
    }
    private fun change(error: String, action: () -> Unit, done: () -> Unit = {}) {
        if (isLoading || loadFailed || saving) return
        saving = true
        viewModelScope.launch {
            try {
                changes.withLock {
                    val snapshot = withContext(Dispatchers.IO) { action(); repository.snapshot() }
                    apply(snapshot)
                }
                done()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = error }
            finally { saving = false }
        }
    }
    fun save(h: Holding, done: () -> Unit = {}) = change("دارایی ذخیره نشد؛ دوباره تلاش کن.", { repository.save(h) }) {
        done()
        if (!h.manual) { if (busy) refreshAfterSave = true else refresh(automatic = true) }
    }
    fun remove(h: Holding, done: () -> Unit = {}) {
        var position: Int? = null
        change("حذف انجام نشد؛ دوباره تلاش کن.", { position = repository.remove(h.id) }) {
            position?.let { pendingUndo = RemovedHolding(UUID.randomUUID().toString(), h, it) }; done()
        }
    }
    fun undoRemoval(token: String) {
        val removed = pendingUndo?.takeIf { it.token == token } ?: return
        change("بازگرداندن دارایی انجام نشد؛ دوباره تلاش کن.", { repository.undo(removed.holding, removed.position) }) {
            if (pendingUndo?.token == token) pendingUndo = null
        }
    }
    fun dismissUndo(token: String) { if (pendingUndo?.token == token) pendingUndo = null }
    fun settings(stock: String, navasan: String, fx: String, done: () -> Unit = {}) {
        change("تنظیمات کامل ذخیره نشد؛ دوباره تلاش کن.", {
            keys.save(MarketKeys(stock.trim(), navasan.trim())); repository.settings(fx.trim())
        }) {
            if (apiKey != stock.trim()) { loadedCatalogs.remove(PriceSource.STOCKS); loadedCatalogs.remove(PriceSource.CERTIFICATES) }
            if (navasanKey != navasan.trim()) loadedCatalogs.remove(PriceSource.NAVASAN)
            apiKey = stock.trim(); navasanKey = navasan.trim(); done(); if(busy)refreshAfterSave=true else refresh(automatic=true)
        }
    }
    private fun installCatalog(source: PriceSource, assets: List<AssetOption>) {
        if (assets.isEmpty()) return
        assetCatalog = assetCatalog.filterNot { sourceFor(it.category, it.key) == source } + assets
        val name = when (source) { PriceSource.STOCKS -> "stockAssetCatalog"; PriceSource.CERTIFICATES -> "certificateAssetCatalog"; PriceSource.CRYPTO -> "cryptoAssetCatalog"; else -> return }
        prefs.edit().putString(name, AssetCatalog.encode(assets)).apply()
    }
    private suspend fun cache(prices: Map<String, Quote>, states: Map<PriceSource, SourceState>) {
        changes.withLock {
            val snapshot = withContext(Dispatchers.IO) { repository.cache(prices, states); repository.snapshot() }
            apply(snapshot)
        }
    }
    fun loadAssetCatalog(category: String, force: Boolean = false) {
        if (isLoading || loadFailed || catalogBusy || busy) return
        val sources = when (category) {
            AssetCategory.STOCKS -> listOf(PriceSource.STOCKS, PriceSource.CERTIFICATES)
            AssetCategory.METAL_FUNDS -> listOf(PriceSource.STOCKS)
            AssetCategory.CRYPTO -> listOf(PriceSource.CRYPTO)
            AssetCategory.CURRENCY, AssetCategory.PHYSICAL_GOLD -> listOf(PriceSource.NAVASAN)
            else -> emptyList()
        }.filter { (force || it !in loadedCatalogs) && (it == PriceSource.CRYPTO || if (it == PriceSource.NAVASAN) navasanKey.isNotBlank() else apiKey.isNotBlank()) }
        if (sources.isEmpty()) return
        catalogBusy = true
        viewModelScope.launch {
            val errors = mutableListOf<String>()
            try {
                for (source in sources) {
                    val now = System.currentTimeMillis()
                    try {
                        val snapshot = withContext(Dispatchers.IO) {
                            when (source) {
                                PriceSource.STOCKS -> client.stockCatalogMarket(apiKey)
                                PriceSource.CERTIFICATES -> client.certificateCatalogMarket(apiKey)
                                PriceSource.CRYPTO -> {
                                    val assets = try { client.cryptoAssets() } catch (_: PriceApiException) { emptyList() }
                                    StockMarketData(client.crypto(), assets)
                                }
                                PriceSource.NAVASAN -> StockMarketData(client.navasan(navasanKey), emptyList())
                            }
                        }
                        installCatalog(source, snapshot.assets)
                        if(source == PriceSource.CRYPTO && snapshot.assets.isEmpty()) errors += "فهرست نام رمزارزها تازه نشد؛ از فهرست ذخیره‌شده استفاده می‌شود. قیمت‌ها دریافت شدند."
                        val required = holdings.filter { sourceFor(it) == source }.map { it.key }
                        val missing = required.filter { it !in snapshot.quotes }
                        val warning = if (missing.isEmpty()) null else "${source.label}: قیمت بعضی دارایی‌ها دریافت نشد؛ قیمت‌های قبلی حفظ شدند."
                        cache(snapshot.quotes, mapOf(source to SourceState(source, now, now, warning, snapshot.quotes.keys)))
                        loadedCatalogs += source
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        val warning = "${source.label}: " + (if (e is PriceApiException) e.message else "به‌روزرسانی فهرست انجام نشد؛ دوباره تلاش کن.")
                        errors += warning
                        val old = sourceStates[source] ?: SourceState(source)
                        cache(emptyMap(), mapOf(source to old.copy(lastAttemptAt = now, error = warning)))
                    }
                }
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { errors += "ذخیرهٔ فهرست کامل نشد؛ اطلاعات قبلی حفظ شدند." }
            finally { catalogError = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"); catalogBusy = false }
        }
    }
    fun refresh(automatic: Boolean = false, respectInterval: Boolean = false) {
        if (isLoading) { refreshOnReady = true; return }
        if (busy || loadFailed) return
        val elapsed = SystemClock.elapsedRealtime()
        if (respectInterval && !RefreshPolicy.shouldRefresh(elapsed, lastRefreshAttempt)) return
        lastRefreshAttempt = elapsed; busy = true
        val items = holdings; val oldQuotes = quotes; val oldStates = sourceStates; val stock = apiKey; val nav = navasanKey
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { updater.refresh(items, oldQuotes, stock, nav, oldStates) }
                cache(result.quotes, result.states)
                installCatalog(PriceSource.STOCKS, result.stockAssets); installCatalog(PriceSource.CERTIFICATES, result.certificateAssets)
                val known = assetCatalog.map { it.category to it.key }.toSet()
                assetCatalog = assetCatalog + AssetCatalog.additionsFromQuotes(quotes).filter { (it.category to it.key) !in known }
                // Persistent status carries failures; avoid stacking identical automatic error snackbars.
                if (!automatic) message = if (result.errors.isEmpty()) "قیمت‌ها دریافت شدند." else "دریافت بعضی قیمت‌ها ناموفق بود؛ جزئیات بالای سبد است."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "ذخیرهٔ قیمت‌های جدید کامل نشد؛ اطلاعات قبلی حفظ شده است." }
            finally { busy = false; if (refreshAfterSave) { refreshAfterSave = false; refresh(automatic = true) } }
        }
    }
    fun exportBackup(uri: Uri) {
        if (fileBusy || isLoading || loadFailed) return
        fileBusy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val snapshot = repository.snapshot()
                    val raw = BackupCodec.encode(snapshot.holdings, snapshot.manualFx)
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(raw.toByteArray(Charsets.UTF_8)) }
                        ?: error("Output unavailable")
                }
                message = "فایل پشتیبان ذخیره شد؛ کلیدهای API در آن نیستند."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "فایل پشتیبان ذخیره نشد؛ دوباره تلاش کن." }
            finally { fileBusy = false }
        }
    }
    fun readBackup(uri: Uri) {
        if (fileBusy || isLoading || loadFailed) return
        fileBusy = true
        viewModelScope.launch {
            try {
                backupPreview = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri) ?: error("Input unavailable")
                    val bytes = input.use { stream ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val count = stream.read(buffer); if (count < 0) break; require(out.size() + count <= BackupCodec.MAX_BYTES); out.write(buffer, 0, count) }
                        out.toByteArray()
                    }
                    BackupCodec.decode(String(bytes, Charsets.UTF_8))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "فایل پشتیبان معتبر یا قابل خواندن نیست؛ هیچ دارایی تغییر نکرد." }
            finally { fileBusy = false }
        }
    }
    fun dismissBackup() { backupPreview = null }
    fun restoreBackup() {
        val backup = backupPreview ?: return
        var count = 0
        change("بازیابی انجام نشد؛ دارایی‌های فعلی حفظ شدند.", { count = repository.importBackup(backup) }) {
            backupPreview = null; message = "$count دارایی اضافه شد؛ دارایی‌های موجود تغییر نکردند."
            if (busy) refreshAfterSave = true else refresh(automatic = true)
        }
    }
}

data class RemovedHolding(val token: String, val holding: Holding, val position: Int)
