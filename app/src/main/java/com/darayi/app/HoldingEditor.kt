@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.darayi.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.math.BigDecimal

private val DraftSaver = listSaver<HoldingDraft, String>(
 save = { listOf(it.id,it.name,it.key,it.category,it.measure,it.quantity,it.grams,it.milligrams,it.manual.toString(),it.price,it.priceUnit) },
 restore = { HoldingDraft(it[0],it[1],it[2],it[3],it[4],it[5],it[6],it[7],it[8].toBoolean(),it[9],it[10]) }
)

@Composable fun HoldingEditor(old:Holding?,assets:List<AssetOption>,catalogBusy:Boolean,catalogError:String?,loadCatalog:(String,Boolean)->Unit,
 onDismiss:()->Unit,onSave:(Holding)->Unit,onDelete:(Holding)->Unit, states:Map<PriceSource,SourceState> = emptyMap(), quotes:Map<String,Quote> = emptyMap(),
 saving:Boolean=false,hasStockKey:Boolean=true,hasNavasanKey:Boolean=true) {
 var draft by rememberSaveable(old?.id,stateSaver=DraftSaver){mutableStateOf(HoldingDraft.from(old))}
 var error by rememberSaveable{mutableStateOf<String?>(null)}
 var categoryOpen by rememberSaveable{mutableStateOf(false)}
 val selected=assets.firstOrNull{it.key==draft.key&&(it.category==draft.category||(usesStockPrices(it.category)&&usesStockPrices(draft.category)))}
 val ready=draft.key.isNotBlank()||draft.category==AssetCategory.OTHER
 val gold=isGoldWeight(draft.key)
 val manual=draft.manual||draft.category==AssetCategory.OTHER||selected?.manualOnly==true||draft.key=="IR_GOLD_24K"
 val source=sourceFor(draft.category,draft.key)
 val hasKey=when(source){PriceSource.STOCKS,PriceSource.CERTIFICATES->hasStockKey;PriceSource.NAVASAN->hasNavasanKey;else->true}
 val now=System.currentTimeMillis()
 val available=selected?.let{availability(it,states,now)}?:Availability.UNCHECKED
 LaunchedEffect(draft.category){loadCatalog(draft.category,false)}
 fun save(){
  val result=draft.copy(manual=manual).validate()
  error=result.error
  result.holding?.let(onSave)
 }
 Dialog(onDismissRequest={if(!saving)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
   Scaffold(modifier=Modifier.imePadding(),containerColor=MaterialTheme.colorScheme.background,
    topBar={CenterAlignedTopAppBar(title={Text(if(old==null)"افزودن دارایی" else "ویرایش دارایی",style=MaterialTheme.typography.titleMedium)},
     navigationIcon={IconButton(onClick=onDismiss,enabled=!saving){Icon(Icons.Default.Close,"بستن")}},
     actions={if(old!=null)IconButton(onClick={onDelete(old)},enabled=!saving){Icon(Icons.Default.Delete,"حذف دارایی",tint=MaterialTheme.colorScheme.error)}},
     colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))},
    bottomBar={Surface(color=MaterialTheme.colorScheme.background,shadowElevation=8.dp){
     Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
      error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("editorError"))}
      Button(onClick=::save,enabled=!saving,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("saveHolding"),shape=RoundedCornerShape(18.dp)){
       if(saving)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)else Icon(if(old==null)Icons.Default.Add else Icons.Default.Check,null,Modifier.size(18.dp))
       Spacer(Modifier.width(8.dp));Text(if(saving)"در حال ذخیره…" else if(old==null)"افزودن به سبد" else "ذخیرهٔ تغییرات")
      }
     }
    }}
   ){padding->
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp).testTag("editorScroll"),verticalArrangement=Arrangement.spacedBy(16.dp)){
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
      Text("نوع دارایی",style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
      TextButton(onClick={categoryOpen=!categoryOpen}){Text(shortCategory(draft.category));Text(if(categoryOpen)"  −" else "  تغییر")}
     }
     if(categoryOpen){
      categories.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
       row.forEach{category->FilterChip(selected=draft.category==category,onClick={draft=draft.changeCategory(category);categoryOpen=false;error=null},
        label={Text(shortCategory(category))},modifier=Modifier.weight(1f).testTag("category-${category}"))}
      }}
     }
     if(draft.category==AssetCategory.OTHER){
      OutlinedTextField(draft.name,{draft=draft.copy(name=it);error=null},label={Text("نام دارایی")},placeholder={Text("مثلاً خودرو یا ملک")},singleLine=true,
       modifier=Modifier.fillMaxWidth().testTag("assetName"),shape=RoundedCornerShape(18.dp))
     }else AssetSearch(draft.name,draft.category,assets,onQuery={draft=draft.search(it);error=null},onSelect={draft=draft.select(it);error=null},states=states)
     if(catalogBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
     catalogError?.let{
      Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
      TextButton(onClick={loadCatalog(draft.category,true)},enabled=!catalogBusy){Text("تلاش دوباره")}
     }
     if(ready){
      if(gold){
       Text("وزن طلا",style=MaterialTheme.typography.titleSmall)
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedTextField(draft.grams,{draft=draft.copy(grams=it);error=null},label={Text("گرم")},placeholder={Text("۶۷")},
         modifier=Modifier.weight(1f).testTag("goldGrams"),singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Next))
        OutlinedTextField(draft.milligrams,{draft=draft.copy(milligrams=it);error=null},label={Text("میلی‌گرم")},placeholder={Text("۳۸۰")},
         modifier=Modifier.weight(1f).testTag("goldMilligrams"),singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done))
       }
       Text(combinedGoldGrams(draft.grams,draft.milligrams)?.let{"معادل ${format(it)} گرم"}?:"هر ۱۰۰۰ میلی‌گرم برابر ۱ گرم است.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
       if(old!=null&&old.key==draft.key&&old.qty>=BigDecimal(1000)&&combinedGoldGrams(draft.grams,draft.milligrams)?.compareTo(old.qty)==0){
        Text("اگر وزن قبلی را به میلی‌گرم نوشته بودی، می‌توانی اصلاحش کنی.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={draft=draft.withPreviousMilligrams(old)}){Text("مقدار قبلی را میلی‌گرم حساب کن")}
       }
      }else{
       OutlinedTextField(draft.quantity,{draft=draft.copy(quantity=it);error=null},label={Text("مقدار / تعداد")},suffix={Text(draft.measure.ifBlank{"عدد"})},
        modifier=Modifier.fillMaxWidth().testTag("holdingQuantity"),singleLine=true,shape=RoundedCornerShape(18.dp),
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),textStyle=MaterialTheme.typography.headlineSmall.copy(fontSize=26.sp))
      }
      if(isCertificate(draft.key))Text("تعداد گواهی سپرده را وارد کن؛ قیمت برای هر گواهی است.",style=MaterialTheme.typography.bodySmall)
      FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
       FilterChip(selected=!manual,onClick={draft=draft.copy(manual=false)},enabled=draft.category!=AssetCategory.OTHER&&selected?.manualOnly!=true&&draft.key!="IR_GOLD_24K",label={Text("قیمت آنلاین")})
       FilterChip(selected=manual,onClick={draft=draft.copy(manual=true)},label={Text("قیمت دستی")})
      }
      if(manual){
       Choice("واحد قیمت",draft.priceUnit,listOf("تومان","دلار")){draft=draft.copy(priceUnit=it)}
       OutlinedTextField(draft.price,{draft=draft.copy(price=it);error=null},label={Text(if(gold)"قیمت هر گرم" else "قیمت هر واحد")},modifier=Modifier.fillMaxWidth().testTag("manualPrice"),
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),singleLine=true,shape=RoundedCornerShape(18.dp))
      }else{
       val text=when{
        !hasKey->"برای دریافت قیمت آنلاین، کلید منبع را در تنظیمات ثبت کن؛ فعلاً می‌توانی قیمت دستی وارد کنی."
        available==Availability.UNAVAILABLE->"قیمت آنلاین این دارایی در پاسخ فعلی موجود نیست؛ قیمت دستی ثبت کن یا بعداً دوباره دریافت کن."
        available==Availability.UNCHECKED->"قیمت آنلاین هنوز تأیید نشده است. اگر دریافت نشود، ارزش این دارایی تا ثبت قیمت مشخص نخواهد بود."
        else->quotes[draft.key]?.let{"قیمت دریافتی هر ${draft.measure.ifBlank{"واحد"}}: ${money(it.price)} تومان"}?:"قیمت آنلاین موجود است."
       }
       Text(text,style=MaterialTheme.typography.bodySmall,color=if(available==Availability.ACTIVE&&hasKey)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,modifier=Modifier.testTag("availabilityNotice"))
      }
      if(gold)Text("ارزش طلا بدون اجرت، مالیات و کارمزد محاسبه می‌شود.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
    }
   }
  }
 }
}
