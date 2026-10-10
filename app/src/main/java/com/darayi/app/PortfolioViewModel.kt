package com.darayi.app

import android.app.Application
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
   assetCatalog=assetCatalog+stocks+market
  } catch(e:Exception){catalogError="خواندن فهرست دارایی‌ها ناموفق بود؛ دوباره تلاش کن."}
  try {
   prefs.getString("stockAssetCatalog",null)?.let { cached ->
    val stocks=AssetCatalog.decode(cached)
    if(stocks.isNotEmpty())assetCatalog=assetCatalog.filterNot { usesStockPrices(it.category) }+stocks
   }
  } catch(e:Exception){catalogError="فهرست ذخیره‌شده خوانده نشد؛ از فهرست اولیه استفاده می‌شود."}
  try { holdings=LegacyCodec.holdings(prefs.getString("holdings","[]")?:"[]") } catch(e:Exception){loadFailed=true;message="خواندن دارایی‌های قبلی ناموفق بود؛ اطلاعات قبلی حفظ شده و ذخیرهٔ جدید متوقف است."}
  try { quotes=LegacyCodec.quotes(prefs.getString("quotes","{}")?:"{}") } catch(e:Exception){ message="قیمت‌های ذخیره‌شده خوانده نشد؛ دوباره قیمت‌ها را دریافت کن." }
 }
 fun save(h:Holding){if(loadFailed)return;holdings=if(holdings.any { it.id==h.id })holdings.map { if(it.id==h.id) h else it } else holdings+h;prefs.edit().putString("holdings",LegacyCodec.encode(holdings)).apply();if(!h.manual){if(busy)refreshAfterSave=true else refresh(automatic=true)}}
 fun remove(h:Holding){if(loadFailed)return;holdings=holdings.filterNot { it.id==h.id };prefs.edit().putString("holdings",LegacyCodec.encode(holdings)).apply()}
 fun settings(key:String,navKey:String,fx:String){if(apiKey!=key.trim())stockCatalogLoaded=false;apiKey=key.trim();navasanKey=navKey.trim();manualFx=fx.trim();prefs.edit().putString("apiKey",apiKey).putString("navasanApiKey",navasanKey).putString("usd",manualFx).apply()}
 private fun updateStockCatalog(stocks:List<AssetOption>){
  if(stocks.isEmpty())return
  assetCatalog=assetCatalog.filterNot { usesStockPrices(it.category) }+stocks
  prefs.edit().putString("stockAssetCatalog",AssetCatalog.encode(stocks)).apply()
  stockCatalogLoaded=true;catalogError=null
 }
 fun loadAssetCatalog(category:String,force:Boolean=false){
  if(!usesStockPrices(category)||catalogBusy||apiKey.isBlank()||(!force&&stockCatalogLoaded))return
  if(busy&&holdings.any { !it.manual&&usesStockPrices(it.type) })return
  catalogBusy=true;val key=apiKey
  viewModelScope.launch {
   try {
    val stocks=withContext(Dispatchers.IO){priceClient.stockAssets(key)}
    updateStockCatalog(stocks)
   } catch(e:CancellationException){throw e}
   catch(e:PriceApiException){catalogError="به‌روزرسانی فهرست دارایی‌ها ناموفق بود. ${e.message}"}
   catch(e:Exception){catalogError="به‌روزرسانی فهرست دارایی‌ها ناموفق بود؛ دوباره تلاش کن."}
   finally{catalogBusy=false}
  }
 }
 fun refresh(automatic:Boolean=false){
  if(busy||loadFailed)return
  busy=true
  val key=apiKey;val navKey=navasanKey;val items=holdings;val previous=quotes
  viewModelScope.launch {
   try {
    val result=withContext(Dispatchers.IO){priceUpdater.refresh(items,previous,key,navKey)}
    quotes=result.quotes;prefs.edit().putString("quotes",LegacyCodec.encodeQuotes(quotes)).apply()
    updateStockCatalog(result.stockAssets)
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
