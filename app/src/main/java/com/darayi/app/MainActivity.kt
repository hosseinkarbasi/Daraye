@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.darayi.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import java.math.BigDecimal
import java.math.RoundingMode

private val Mint=Color(0xFF75E0B8)
internal val palette=listOf(Color(0xFFEAC76E),Color(0xFF8FC9FF),Mint,Color(0xFFC1AAFF),Color(0xFFFFB58A),Color(0xFFB7C3D7))
private val colors=darkColorScheme(primary=Mint,onPrimary=Color(0xFF003828),primaryContainer=Color(0xFF175443),secondary=Color(0xFFB8CCC2),tertiary=Color(0xFFC1AAFF),background=Color(0xFF0C1424),surface=Color(0xFF111D30),surfaceContainer=Color(0xFF18263B),surfaceContainerHigh=Color(0xFF233149),onSurface=Color(0xFFF1F4FB),onSurfaceVariant=Color(0xFFB7C3D2))
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){MaterialExpressiveTheme(colorScheme=colors,typography=DarayiTypography){PortfolioApp()}} }}}

@Composable fun PortfolioApp(vm:PortfolioViewModel=viewModel()){
 var editorId by rememberSaveable { mutableStateOf<String?>(null) }
 var showSettings by rememberSaveable { mutableStateOf(false) }
 val snackbar=remember { SnackbarHostState() }
 LifecycleStartEffect(vm){
  vm.refresh(automatic=true,respectInterval=true)
  onStopOrDispose { }
 }
 LaunchedEffect(vm.message){vm.message?.let { snackbar.showSnackbar(it,withDismissAction=true,duration=SnackbarDuration.Long);vm.message=null }}
 val fx=vm.fx
 val rows=remember(vm.holdings,vm.quotes,fx){valued(vm.holdings,vm.quotes,fx)}
 val total=rows.mapNotNull { it.toman }.fold(zero,BigDecimal::add)
 val unknown=rows.count { it.toman==null }
 Scaffold(
  containerColor=MaterialTheme.colorScheme.background,
  topBar={CenterAlignedTopAppBar(title={Text("دارایی من",style=MaterialTheme.typography.titleMedium)},navigationIcon={IconButton(onClick={showSettings=true}){Icon(Icons.Default.Settings,"تنظیمات")}},actions={IconButton(onClick={vm.refresh()},enabled=!vm.busy){if(vm.busy)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)else Icon(Icons.Default.Refresh,"دریافت قیمت روز")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))},
  bottomBar={Surface(color=MaterialTheme.colorScheme.background){Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(top=10.dp,bottom=14.dp),contentAlignment=Alignment.Center){Button(onClick={editorId="new"},enabled=!vm.loadFailed,modifier=Modifier.height(60.dp).widthIn(min=228.dp),shapes=ButtonDefaults.shapes(),contentPadding=PaddingValues(horizontal=28.dp)){Icon(Icons.Default.Add,null);Spacer(Modifier.width(10.dp));Text("افزودن دارایی",style=MaterialTheme.typography.titleMedium)}}}},
  snackbarHost={SnackbarHost(snackbar)}
 ){padding->
  LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item { Column(Modifier.fillMaxWidth().padding(top=10.dp,bottom=12.dp),horizontalAlignment=Alignment.CenterHorizontally){
    Text("ارزش کل دارایی‌ها",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Text(money(total)+" تومان",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(if(fx>zero)format(total.divide(fx,2,RoundingMode.HALF_UP))+" دلار" else "— دلار",style=MaterialTheme.typography.titleLarge,color=Mint)
    Spacer(Modifier.height(12.dp))
    if(unknown>0)Text("جمع ناقص · $unknown دارایی بدون قیمت یا نرخ تبدیل",fontSize=11.sp,color=palette[0],textAlign=TextAlign.Center)
    Text(if(fx>zero)"هر دلار ${money(fx)} تومان · ${vm.quotes["USD"]?.stamp?:"نرخ دستی"}" else "نرخ دلار برای تبدیل موجود نیست",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
   }}
   vm.refreshError?.let { error ->
    item {
     Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) {
      Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
       Text("قیمت‌ها کامل به‌روزرسانی نشدند",style=MaterialTheme.typography.titleSmall)
       Text(error,style=MaterialTheme.typography.bodySmall)
       TextButton(onClick={vm.refresh()},enabled=!vm.busy){Text("تلاش دوباره")}
      }
     }
    }
   }
   item { Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     Text("ترکیب دارایی‌ها",style=MaterialTheme.typography.titleMedium)
     categories.forEachIndexed { index,category->
      val value=rows.filter { it.holding.type==category }.mapNotNull { it.toman }.fold(zero,BigDecimal::add)
      val percent=share(value,total)
      val fraction by animateFloatAsState((percent.toFloat()/100f).coerceIn(0f,1f),label="allocation")
      Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(category,modifier=Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(if(total>zero)format(percent)+"٪" else "—",fontSize=12.sp,color=palette[index])}
       Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(palette[index].copy(alpha=.13f)).semantics { contentDescription="$category، ${format(percent)} درصد" }){Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(palette[index]))}
      }
     }
     if(unknown>0)Text("درصدها از دارایی‌های دارای ارزش مشخص محاسبه شده‌اند",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }}
   item { Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("دارایی‌ها",style=MaterialTheme.typography.titleLarge);Text("بیشترین ارزش",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)} }
   if(rows.isEmpty())item { Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("هنوز دارایی ثبت نکرده‌ای",style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(8.dp));Text("با دکمهٔ پایین اولین دارایی را اضافه کن",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)} }
   items(rows,key={it.holding.id}){r->HoldingCard(r){editorId=r.holding.id}}
  }
 }
 if(editorId!=null){val h=vm.holdings.find { it.id==editorId };HoldingEditor(h,vm.assetCatalog,vm.catalogBusy,vm.catalogError,{category,force->vm.loadAssetCatalog(category,force)},onDismiss={editorId=null},onSave={vm.save(it);editorId=null},onDelete={vm.remove(it);editorId=null})}
 if(showSettings)SettingsDialog(vm.apiKey,vm.navasanKey,vm.manualFx,{showSettings=false}){key,navKey,rate->vm.settings(key,navKey,rate);showSettings=false}
}
@Composable fun HoldingCard(row:Valued,onClick:()->Unit){val h=row.holding;val q=row.quote;val change=q?.change
 Card(onClick=onClick,shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
    AssetLogo(h)
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
     Text(buildAnnotatedString {
      withStyle(SpanStyle(fontSize=12.sp,fontWeight=FontWeight.Normal,color=MaterialTheme.colorScheme.onSurfaceVariant)){append(format(h.qty));append(" ")}
      append(holdingTitle(h).removePrefix("${format(h.qty)} "))
     },style=MaterialTheme.typography.titleSmall.copy(fontSize=15.sp,lineHeight=25.sp),maxLines=3,overflow=TextOverflow.Ellipsis)
     Text(if(change==null)"—" else "${if(change>zero)"+" else ""}${format(change.setScale(2,RoundingMode.HALF_UP))}٪",fontSize=10.sp,color=if(change==null||change.compareTo(zero)==0)MaterialTheme.colorScheme.onSurfaceVariant else if(change>zero)Mint else Color(0xFFFF93A2))
    }
    Column(horizontalAlignment=Alignment.End,modifier=Modifier.widthIn(max=132.dp)){
     Text(row.toman?.let(::money)?:"نامشخص",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.End)
     Text("تومان",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }
   if(q?.unit=="دلار")Text("${format(q.price*h.qty)} دلار",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}
@Composable fun AssetLogo(h:Holding){val category=categories.indexOf(h.type).coerceAtLeast(0);val color=if(h.key=="BTC")Color(0xFFF7931A)else palette[category]
 Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha=.15f)),contentAlignment=Alignment.Center){
  if(h.key=="ETH")Canvas(Modifier.size(30.dp)){val w=size.width;val he=size.height;val p=Path().apply{moveTo(w*.5f,0f);lineTo(w*.83f,he*.53f);lineTo(w*.5f,he*.69f);lineTo(w*.17f,he*.53f);close();moveTo(w*.17f,he*.6f);lineTo(w*.5f,he);lineTo(w*.83f,he*.6f);lineTo(w*.5f,he*.77f);close()};drawPath(p,color)}
  else Text(when(h.key){"BTC"->"₿";"USD"->"$";"EUR"->"€";"GBP"->"£";"USDT"->"₮";else->when(category){0->"Au";2,4->"▥";else->h.key.ifBlank { h.name }.take(3)}},fontSize=if(h.key.length>3)17.sp else 24.sp,fontWeight=FontWeight.Bold,color=color)
 }
}

@Composable internal fun Choice(label:String,value:String,choices:List<String>,select:(String)->Unit){var open by remember { mutableStateOf(false) }
 ExposedDropdownMenuBox(expanded=open,onExpandedChange={open=it}){
  OutlinedTextField(value=value,onValueChange={},readOnly=true,label={Text(label)},shape=RoundedCornerShape(18.dp),trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(open)},modifier=Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
  ExposedDropdownMenu(expanded=open,onDismissRequest={open=false}){choices.forEach { option->DropdownMenuItem(text={Text(option)},onClick={select(option);open=false})}}
 }
}
@Composable internal fun AssetSearch(value:String,category:String,assets:List<AssetOption>,onQuery:(String)->Unit,onSelect:(AssetOption)->Unit){
 var expanded by remember(category) { mutableStateOf(false) }
 var limit by remember(category,value){mutableIntStateOf(40)}
 val matches=remember(assets,category,value,limit){AssetCatalog.search(assets,category,value,limit+1)}
 val results=matches.take(limit)
 val count=remember(assets,category){assets.count{it.category==category}}
 ExposedDropdownMenuBox(expanded=expanded&&results.isNotEmpty(),onExpandedChange={expanded=it}){
  OutlinedTextField(value=value,onValueChange={onQuery(it);expanded=true},label={Text("نام دارایی")},placeholder={Text("نام یا نماد را بنویس…")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(18.dp),supportingText={Text(if(results.isEmpty()&&value.isNotBlank())"دارایی پیدا نشد؛ نام را دقیق‌تر جست‌وجو کن." else "$count دارایی قابل جست‌وجو")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},singleLine=true,modifier=Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable))
  ExposedDropdownMenu(expanded=expanded&&results.isNotEmpty(),onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=280.dp)){
   results.forEach { asset ->
    DropdownMenuItem(text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text(asset.name,style=MaterialTheme.typography.titleSmall);if(asset.details.isNotBlank()&&asset.details!=asset.name)Text(asset.details,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},onClick={onSelect(asset);expanded=false})
   }
   if(matches.size>limit)DropdownMenuItem(text={Text("نمایش گزینه‌های بیشتر")},onClick={limit+=40})
  }
 }
}
@Composable fun SettingsDialog(savedKey:String,savedNavasanKey:String,savedFx:String,onDismiss:()->Unit,onSave:(String,String,String)->Unit){
 var key by rememberSaveable { mutableStateOf(savedKey) };var navKey by rememberSaveable { mutableStateOf(savedNavasanKey) };var fx by rememberSaveable { mutableStateOf(savedFx) };var error by remember { mutableStateOf(false) };val context=LocalContext.current
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
  Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   Text("تنظیمات",style=MaterialTheme.typography.headlineSmall)
   OutlinedTextField(key,{key=it},label={Text("کلید BRSAPI برای سهام، صندوق و گواهی کالا")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
   OutlinedTextField(navKey,{navKey=it},label={Text("کلید نوسان برای ارز، طلا و سکه")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
   Text("قیمت رمزارزها از بازار ریالی نوبیتکس دریافت و به تومان تبدیل می‌شود؛ کلید لازم نیست.",style=MaterialTheme.typography.bodySmall)
   OutlinedTextField(fx,{fx=it;error=false},label={Text("نرخ دستی دلار به تومان")},supportingText={Text("اختیاری؛ نرخ دریافتی دلار اولویت دارد")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,isError=error,modifier=Modifier.fillMaxWidth())
   Text("دارایی‌ها روی همین گوشی ذخیره می‌شوند. فقط قیمت‌های بازار از منبع دریافت می‌شوند و فهرست دارایی‌ها ارسال نمی‌شود.",style=MaterialTheme.typography.bodySmall)
   TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://brsapi.ir")))}){Text("دریافت کلید بورس از BRSAPI")}
   TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.navasan.tech/api/")))}){Text("دریافت کلید ارز و طلا از نوسان")}
   Button(onClick={val rate=number(fx);if(fx.isNotBlank()&&(rate==null||rate<=zero))error=true else onSave(key,navKey,rate?.toPlainString()?:"")},modifier=Modifier.fillMaxWidth().height(56.dp),shapes=ButtonDefaults.shapes()){Text("ذخیرهٔ تنظیمات")}
   Text("دارایی من · نسخهٔ ۲.۵",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}
