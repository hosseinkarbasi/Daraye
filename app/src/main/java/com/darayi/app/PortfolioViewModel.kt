package com.darayi.app

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

class PortfolioViewModel(app: Application): AndroidViewModel(app) {
 private val prefs=app.getSharedPreferences("portfolio",0)
 private val priceClient=PriceApiClient()
 private val priceUpdater=PriceUpdater(priceClient)
 private var stockCatalogLoaded=false
 private var certificateCatalogLoaded=false
 private var cryptoCatalogLoaded=false
 private var lastRefreshAttempt:Long?=null
 private var refreshAfterSave=false
 var assetCatalog by mutableStateOf(AssetCatalog.physicalGold);private set
 var catalogBusy by mutableStateOf(false);private set
 var catalogError by mutableStateOf<String?>(null);private set
 var holdings by mutableStateOf(emptyList<Holding>());private set
 var quotes by mutableStateOf(emptyMap<String,Quote>());private set
 var apiKey by mutableStateOf(prefs.getString("apiKey","")?:"");private set
 var navasanKey by mutableStateOf(prefs.getString("navasanApiKey","")?:"");private set
 var manualFx by mutableStateOf(prefs.getString("usd","")?:"");private set
 var busy by mutableStateOf(false);private set
 var message by mutableStateOf<String?>(null)
 var refreshError by mutableStateOf<String?>(null);private set
 var loadFailed by mutableStateOf(false);private set
 val fx get() = quotes["USD"]?.takeIf { it.unit=="تومان" && it.price>zero }?.price ?: number(manualFx) ?: zero
 init {
  try {
   val stocks=AssetCatalog.stocks(app.assets.open("stock_catalog.json").bufferedReader().use { it.readText() })
   val market=AssetCatalog.localizeMarketAssets(AssetCatalog.decode(app.assets.open("market_catalog.json").bufferedReader().use { it.readText() }))
   val crypto=AssetCatalog.crypto(app.assets.open("crypto_catalog.json").bufferedReader().use { it.readText() })
   val certificates=AssetCatalog.certificates(app.assets.open("certificate_catalog.json").bufferedReader().use { it.readText() })
   assetCatalog=assetCatalog+stocks+market+crypto+certificates
  } catch(e:Exception){catalogError="خواندن فهرست دارایی‌ها ناموفق بود؛ دوباره تلاش کن."}
  try {
   prefs.getString("stockAssetCatalog",null)?.let { cached ->
    val stocks=AssetCatalog.decode(cached)
    if(stocks.isNotEmpty())assetCatalog=assetCatalog.filterNot { usesStockPrices(it.category)&&!isCertificate(it.key) }+stocks.filterNot { isCertificate(it.key) }
   }
   for((cache,category) in listOf("cryptoAssetCatalog" to AssetCategory.CRYPTO,"certificateAssetCatalog" to AssetCategory.STOCKS)){
    prefs.getString(cache,null)?.let { raw ->
     val cached=AssetCatalog.decode(raw)
     if(cached.isNotEmpty())assetCatalog=assetCatalog.filterNot { if(cache=="certificateAssetCatalog")isCertificate(it.key) else it.category==category }+cached
    }
   }
  } catch(e:Exception){catalogError="فهرست ذخیره‌شده خوانده نشد؛ از فهرست اولیه استفاده می‌شود."}
  try { holdings=LegacyCodec.holdings(prefs.getString("holdings","[]")?:"[]") } catch(e:Exception){loadFailed=true;message="خواندن دارایی‌های قبلی ناموفق بود؛ اطلاعات قبلی حفظ شده و ذخیرهٔ جدید متوقف است."}
  try { quotes=LegacyCodec.quotes(prefs.getString("quotes","{}")?:"{}") } catch(e:Exception){ message="قیمت‌های ذخیره‌شده خوانده نشد؛ دوباره قیمت‌ها را دریافت کن." }
 }
 fun save(h:Holding){if(loadFailed)return;holdings=if(holdings.any { it.id==h.id })holdings.map { if(it.id==h.id) h else it } else holdings+h;prefs.edit().putString("holdings",LegacyCodec.encode(holdings)).apply();if(!h.manual){if(busy)refreshAfterSave=true else refresh(automatic=true)}}
 fun remove(h:Holding){if(loadFailed)return;holdings=holdings.filterNot { it.id==h.id };prefs.edit().putString("holdings",LegacyCodec.encode(holdings)).apply()}
 fun settings(key:String,navKey:String,fx:String){if(apiKey!=key.trim()){stockCatalogLoaded=false;certificateCatalogLoaded=false};apiKey=key.trim();navasanKey=navKey.trim();manualFx=fx.trim();prefs.edit().putString("apiKey",apiKey).putString("navasanApiKey",navasanKey).putString("usd",manualFx).apply()}
 private fun updateStockCatalog(stocks:List<AssetOption>){
  if(stocks.isEmpty())return
  assetCatalog=assetCatalog.filterNot { usesStockPrices(it.category)&&!isCertificate(it.key) }+stocks
  prefs.edit().putString("stockAssetCatalog",AssetCatalog.encode(stocks)).apply()
  stockCatalogLoaded=true;catalogError=null
 }
 private fun updateCertificateCatalog(assets:List<AssetOption>){
  if(assets.isEmpty())return
  assetCatalog=assetCatalog.filterNot { isCertificate(it.key) }+assets
  prefs.edit().putString("certificateAssetCatalog",AssetCatalog.encode(assets)).apply()
  certificateCatalogLoaded=true
 }
 private fun updateCryptoCatalog(assets:List<AssetOption>){
  assetCatalog=assetCatalog.filterNot { it.category==AssetCategory.CRYPTO }+assets
  prefs.edit().putString("cryptoAssetCatalog",AssetCatalog.encode(assets)).apply()
  cryptoCatalogLoaded=true
 }
 fun loadAssetCatalog(category:String,force:Boolean=false){
  val crypto=category==AssetCategory.CRYPTO
  if(catalogBusy)return
  if(crypto){if(!force&&cryptoCatalogLoaded)return}
  else if(!usesStockPrices(category)||apiKey.isBlank()||(!force&&stockCatalogLoaded&&(category!=AssetCategory.STOCKS||certificateCatalogLoaded)))return
  catalogBusy=true;val key=apiKey
  viewModelScope.launch {
   val errors=mutableListOf<String>()
   suspend fun load(block:suspend ()->Unit){
    try{block()}catch(e:CancellationException){throw e}
    catch(e:PriceApiException){errors+="به‌روزرسانی فهرست دارایی‌ها ناموفق بود. ${e.message}"}
    catch(e:Exception){errors+="به‌روزرسانی فهرست دارایی‌ها ناموفق بود؛ دوباره تلاش کن."}
   }
   try {
    if(crypto)load{updateCryptoCatalog(withContext(Dispatchers.IO){priceClient.cryptoAssets()})}
    else{
     if(force||!stockCatalogLoaded)load{updateStockCatalog(withContext(Dispatchers.IO){priceClient.stockAssets(key)})}
     if(category==AssetCategory.STOCKS&&(force||!certificateCatalogLoaded))load{updateCertificateCatalog(withContext(Dispatchers.IO){priceClient.certificateAssets(key)})}
    }
   }finally{catalogError=errors.takeIf{it.isNotEmpty()}?.distinct()?.joinToString("\n");catalogBusy=false}
  }
 }
 fun refresh(automatic:Boolean=false,respectInterval:Boolean=false){
  if(busy||loadFailed)return
  val now=SystemClock.elapsedRealtime()
  if(respectInterval&&!RefreshPolicy.shouldRefresh(now,lastRefreshAttempt))return
  lastRefreshAttempt=now
  busy=true
  val key=apiKey;val navKey=navasanKey;val items=holdings;val previous=quotes
  viewModelScope.launch {
   try {
    val result=withContext(Dispatchers.IO){priceUpdater.refresh(items,previous,key,navKey)}
    quotes=result.quotes;prefs.edit().putString("quotes",LegacyCodec.encodeQuotes(quotes)).apply()
    updateStockCatalog(result.stockAssets)
    updateCertificateCatalog(result.certificateAssets)
    val known=assetCatalog.map { it.category to it.key }.toSet()
    assetCatalog=assetCatalog+AssetCatalog.additionsFromQuotes(quotes).filter { (it.category to it.key) !in known }
    refreshError=result.errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
    if(!automatic||refreshError!=null)message=if(automatic)refreshError else result.messages.joinToString("\n")
   } catch(e:CancellationException) {
    throw e
   } catch(e:Exception) {
    refreshError="به‌روزرسانی قیمت‌ها کامل نشد؛ قیمت‌های قبلی حفظ شدند. دوباره تلاش کن."
    message=refreshError
   } finally {
    busy=false
    if(refreshAfterSave){refreshAfterSave=false;refresh(automatic=true)}
   }
  }
 }
}
