package com.darayi.app

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

class PortfolioViewModel(app: Application): AndroidViewModel(app) {
 private val prefs=app.getSharedPreferences("portfolio",0)
 var holdings by mutableStateOf(emptyList<Holding>());private set
 var quotes by mutableStateOf(emptyMap<String,Quote>());private set
 var apiKey by mutableStateOf(prefs.getString("apiKey","")?:"");private set
 var manualFx by mutableStateOf(prefs.getString("usd","")?:"");private set
 var busy by mutableStateOf(false);private set
 var message by mutableStateOf<String?>(null)
 var loadFailed by mutableStateOf(false);private set
 val fx get() = quotes["USD"]?.takeIf { it.unit=="تومان" && it.price>zero }?.price ?: number(manualFx) ?: zero
 init {
  try { holdings=LegacyCodec.holdings(prefs.getString("holdings","[]")?:"[]") } catch(e:Exception){loadFailed=true;message="خواندن دارایی‌های قبلی ناموفق بود؛ اطلاعات قبلی حفظ شده و ذخیرهٔ جدید متوقف است."}
  try { quotes=LegacyCodec.quotes(prefs.getString("quotes","{}")?:"{}") } catch(e:Exception){ message="قیمت‌های ذخیره‌شده خوانده نشد؛ دوباره قیمت‌ها را دریافت کن." }
 }
 fun save(h:Holding){if(loadFailed)return;holdings=if(holdings.any { it.id==h.id })holdings.map { if(it.id==h.id) h else it } else holdings+h;prefs.edit().putString("holdings",LegacyCodec.encode(holdings)).apply()}
 fun remove(h:Holding){if(loadFailed)return;holdings=holdings.filterNot { it.id==h.id };prefs.edit().putString("holdings",LegacyCodec.encode(holdings)).apply()}
 fun settings(key:String,fx:String){apiKey=key.trim();manualFx=fx.trim();prefs.edit().putString("apiKey",apiKey).putString("usd",manualFx).apply()}
 fun refresh(){
  if(busy)return
  if(apiKey.isBlank()){message="کلید منبع قیمت را در تنظیمات وارد کن.";return}
  busy=true
  val key=apiKey;val needStocks=holdings.any { it.type==categories[2]&&!it.manual }
  viewModelScope.launch {
   val updated=quotes.toMutableMap();val errors=mutableListOf<String>()
   withContext(Dispatchers.IO){
    val encoded=URLEncoder.encode(key,"UTF-8")
    try { updated.putAll(PriceParser.market(get("https://api.brsapi.ir/Market/Gold_Currency.php?key=$encoded"))) } catch(e:Exception){errors+="دریافت طلا، ارز و رمزارز ناموفق بود؛ اینترنت و کلید را بررسی کن."}
    if(needStocks)try { updated.putAll(PriceParser.stocks(get("https://api.brsapi.ir/Tsetmc/AllSymbols.php?key=$encoded&type=1"))) }catch(e:Exception){errors+="دریافت بورس ناموفق بود؛ قیمت قبلی حفظ شد."}
   }
   quotes=updated.toMap();prefs.edit().putString("quotes",LegacyCodec.encodeQuotes(quotes)).apply();busy=false
   message=if(errors.isEmpty())"قیمت‌ها دریافت شد؛ زمان هر قیمت روی کارت آن است." else errors.joinToString("\n")
  }
 }
 private fun get(url:String):String { val c=URL(url).openConnection() as HttpsURLConnection;c.connectTimeout=18000;c.readTimeout=18000;c.setRequestProperty("User-Agent","Mozilla/5.0 Darayi/2.0");return try { require(c.responseCode==200);c.inputStream.use { input -> val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192);while(true){val n=input.read(buf);if(n<0)break;out.write(buf,0,n);require(out.size()<12_000_000)};out.toString("UTF-8") } }finally { c.disconnect() } }
}
