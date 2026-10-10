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
 private val priceUpdater=PriceUpdater()
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
  try { holdings=LegacyCodec.holdings(prefs.getString("holdings","[]")?:"[]") } catch(e:Exception){loadFailed=true;message="خواندن دارایی‌های قبلی ناموفق بود؛ اطلاعات قبلی حفظ شده و ذخیرهٔ جدید متوقف است."}
  try { quotes=LegacyCodec.quotes(prefs.getString("quotes","{}")?:"{}") } catch(e:Exception){ message="قیمت‌های ذخیره‌شده خوانده نشد؛ دوباره قیمت‌ها را دریافت کن." }
 }
 fun save(h:Holding){if(loadFailed)return;holdings=if(holdings.any { it.id==h.id })holdings.map { if(it.id==h.id) h else it } else holdings+h;prefs.edit().putString("holdings",LegacyCodec.encode(holdings)).apply()}
 fun remove(h:Holding){if(loadFailed)return;holdings=holdings.filterNot { it.id==h.id };prefs.edit().putString("holdings",LegacyCodec.encode(holdings)).apply()}
 fun settings(key:String,navKey:String,fx:String){apiKey=key.trim();navasanKey=navKey.trim();manualFx=fx.trim();prefs.edit().putString("apiKey",apiKey).putString("navasanApiKey",navasanKey).putString("usd",manualFx).apply()}
 fun refresh(automatic:Boolean=false){
  if(busy||loadFailed)return
  busy=true
  val key=apiKey;val navKey=navasanKey;val items=holdings;val previous=quotes
  viewModelScope.launch {
   try {
    val result=withContext(Dispatchers.IO){priceUpdater.refresh(items,previous,key,navKey)}
    quotes=result.quotes;prefs.edit().putString("quotes",LegacyCodec.encodeQuotes(quotes)).apply()
    refreshError=result.errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
    if(!automatic||refreshError!=null)message=if(automatic)refreshError else result.messages.joinToString("\n")
   } catch(e:CancellationException) {
    throw e
   } catch(e:Exception) {
    refreshError="به‌روزرسانی قیمت‌ها کامل نشد؛ قیمت‌های قبلی حفظ شدند. دوباره تلاش کن."
    message=refreshError
   } finally {
    busy=false
   }
  }
 }
}
