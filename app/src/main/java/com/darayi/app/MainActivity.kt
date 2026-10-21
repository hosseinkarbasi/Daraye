@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.darayi.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import androidx.compose.ui.platform.testTag
import java.math.BigDecimal
import java.math.RoundingMode

internal val Mint=Color(0xFF75E0B8)
internal val palette=listOf(Color(0xFFEAC76E),Color(0xFF8FC9FF),Mint,Color(0xFFC1AAFF),Color(0xFFFFB58A),Color(0xFFB7C3D7))
internal val colors=darkColorScheme(primary=Mint,onPrimary=Color(0xFF003828),primaryContainer=Color(0xFF175443),secondary=Color(0xFFB8CCC2),tertiary=Color(0xFFC1AAFF),background=Color(0xFF0C1424),surface=Color(0xFF111D30),surfaceContainer=Color(0xFF18263B),surfaceContainerHigh=Color(0xFF233149),onSurface=Color(0xFFF1F4FB),onSurfaceVariant=Color(0xFFB7C3D2))
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();setContent { DarayiTheme { PortfolioApp() } }}}

@Composable fun PortfolioApp(vm: PortfolioViewModel = viewModel()) {
 var editorId by rememberSaveable { mutableStateOf<String?>(null) }
 var showSettings by rememberSaveable { mutableStateOf(false) }
 var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
 val snackbar = remember { SnackbarHostState() }
 val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
 LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
  while (kotlinx.coroutines.currentCoroutineContext().isActive) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000) }
 } }
 LifecycleStartEffect(vm) { vm.refresh(automatic = true, respectInterval = true); onStopOrDispose { } }
 LaunchedEffect(vm.message) { vm.message?.let { message -> snackbar.showSnackbar(message, withDismissAction = true); if(vm.message==message)vm.message=null } }
 UndoRemovalEffect(vm.pendingUndo, snackbar, vm::undoRemoval, vm::dismissUndo)
 val export = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportBackup) }
 val restore = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readBackup) }
 PortfolioHome(PortfolioHomeState(vm.holdings, vm.quotes, vm.sourceStates, vm.manualFx, vm.isLoading, vm.busy, vm.loadFailed, vm.navasanKey.isNotBlank()), now,
  onAdd = { editorId = "new" }, onEdit = { editorId = it.id }, onSettings = { showSettings = true }, onRefresh = { vm.refresh() }, snackbar = snackbar)
 if (editorId != null && !vm.isLoading) {
  val old = vm.holdings.find { it.id == editorId }
  HoldingEditor(old, vm.assetCatalog, vm.catalogBusy, vm.catalogError, { category, force -> vm.loadAssetCatalog(category, force) },
   onDismiss = { editorId = null }, onSave = { vm.save(it) { editorId = null } }, onDelete = { vm.remove(it) { editorId = null } },
   states = vm.sourceStates, quotes = vm.quotes, saving = vm.saving, hasStockKey = vm.apiKey.isNotBlank(), hasNavasanKey = vm.navasanKey.isNotBlank())
 }
 if (showSettings) SettingsDialog(vm.apiKey, vm.navasanKey, vm.manualFx, { showSettings = false },
  onSave = { key, nav, fx -> vm.settings(key, nav, fx) { showSettings = false } }, saving = vm.saving,
  fileBusy = vm.fileBusy || vm.isLoading || vm.loadFailed,
  onExport = { showSettings = false; export.launch("Darayi-portfolio.json") },
  onRestore = { showSettings = false; restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
 vm.backupPreview?.let { backup ->
  val added = backup.holdings.count { h -> vm.holdings.none { it.id == h.id } }
  AlertDialog(onDismissRequest = { if (!vm.saving) vm.dismissBackup() }, title = { Text("بازیابی سبد") },
   text = { Text("این فایل ${backup.holdings.size} دارایی دارد؛ $added دارایی جدید اضافه می‌شود. دارایی‌های موجود تغییر نمی‌کنند. کلیدهای API از فایل خوانده نمی‌شوند.") },
   confirmButton = { TextButton(onClick = { vm.restoreBackup() }, enabled = !vm.saving) { Text("بازیابی") } },
   dismissButton = { TextButton(onClick = { vm.dismissBackup() }, enabled = !vm.saving) { Text("انصراف") } })
 }
}
@Composable internal fun Choice(label:String,value:String,choices:List<String>,select:(String)->Unit){var open by remember { mutableStateOf(false) }
 ExposedDropdownMenuBox(expanded=open,onExpandedChange={open=it}){
  OutlinedTextField(value=value,onValueChange={},readOnly=true,label={Text(label)},shape=RoundedCornerShape(18.dp),trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(open)},modifier=Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
  ExposedDropdownMenu(expanded=open,onDismissRequest={open=false}){choices.forEach { option->DropdownMenuItem(text={Text(option)},onClick={select(option);open=false})}}
 }
}
@Composable internal fun AssetSearch(value:String,category:String,assets:List<AssetOption>,onQuery:(String)->Unit,onSelect:(AssetOption)->Unit,states:Map<PriceSource,SourceState> = emptyMap(),now:Long = System.currentTimeMillis()){
 var expanded by remember(category) { mutableStateOf(false) }
 var limit by remember(category,value){mutableIntStateOf(40)}
 val activeKeys=remember(states,now){states.values.filter{it.error==null&&isFresh(it.lastSuccessAt,now)}.flatMap{it.availableKeys}.toSet().takeIf{it.isNotEmpty()}}
 val matches=remember(assets,category,value,limit,activeKeys){AssetCatalog.search(assets,category,value,limit+1,activeKeys)}
 val results=matches.take(limit)
 val count=remember(assets,category){assets.count{it.category==category}}
 ExposedDropdownMenuBox(expanded=expanded&&results.isNotEmpty(),onExpandedChange={expanded=it}){
  OutlinedTextField(value=value,onValueChange={onQuery(it);expanded=true},label={Text("نام دارایی")},placeholder={Text("نام یا نماد را بنویس…")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(18.dp),supportingText={Text(if(results.isEmpty()&&value.isNotBlank())"دارایی پیدا نشد؛ نام را دقیق‌تر جست‌وجو کن." else "$count دارایی قابل جست‌وجو")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("assetSearch").menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable))
  ExposedDropdownMenu(expanded=expanded&&results.isNotEmpty(),onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=280.dp)){
   results.forEach { asset ->
    DropdownMenuItem(text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text(asset.name,style=MaterialTheme.typography.titleSmall);if(availability(asset,states,now)==Availability.UNAVAILABLE)Text("قیمت آنلاین موجود نیست",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error);if(asset.details.isNotBlank()&&asset.details!=asset.name)Text(asset.details,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},onClick={onSelect(asset);expanded=false})
   }
   if(matches.size>limit)DropdownMenuItem(text={Text("نمایش گزینه‌های بیشتر")},onClick={limit+=40})
  }
 }
}
@Composable fun SettingsDialog(savedKey:String,savedNavasanKey:String,savedFx:String,onDismiss:()->Unit,onSave:(String,String,String)->Unit,
 saving:Boolean=false,fileBusy:Boolean=false,onExport:()->Unit={},onRestore:()->Unit={}){
 var key by remember { mutableStateOf(savedKey) };var navKey by remember { mutableStateOf(savedNavasanKey) };var fx by rememberSaveable { mutableStateOf(savedFx) };var error by remember { mutableStateOf(false) };val context=LocalContext.current
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
  Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   Text("تنظیمات",style=MaterialTheme.typography.headlineSmall)
   OutlinedTextField(key,{key=it},label={Text("کلید BRSAPI برای سهام، صندوق و گواهی کالا")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
   OutlinedTextField(navKey,{navKey=it},label={Text("کلید نوسان برای ارز، طلا و سکه")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
   Text("قیمت رمزارزها از بازار ریالی نوبیتکس دریافت و به تومان تبدیل می‌شود؛ کلید لازم نیست.",style=MaterialTheme.typography.bodySmall)
   OutlinedTextField(fx,{fx=it;error=false},label={Text("نرخ دستی دلار به تومان")},supportingText={Text("اختیاری؛ اگر نرخ دریافتی قدیمی باشد از این نرخ استفاده می‌شود")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,isError=error,modifier=Modifier.fillMaxWidth())
   Text("دارایی‌ها روی همین گوشی ذخیره می‌شوند. فقط قیمت‌های بازار از منبع دریافت می‌شوند و فهرست دارایی‌ها ارسال نمی‌شود.",style=MaterialTheme.typography.bodySmall)
   TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://brsapi.ir")))}){Text("دریافت کلید بورس از BRSAPI")}
   TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.navasan.tech/api/")))}){Text("دریافت کلید ارز و طلا از نوسان")}
   Button(onClick={val rate=number(fx);if(fx.isNotBlank()&&(rate==null||rate<=zero))error=true else onSave(key,navKey,rate?.toPlainString()?:"")},enabled=!saving,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shapes=ButtonDefaults.shapes()){Text(if(saving)"در حال ذخیره…" else "ذخیرهٔ تنظیمات")}
   HorizontalDivider()
   Text("پشتیبان‌گیری سبد",style=MaterialTheme.typography.titleSmall)
   Text("فایل شامل دارایی‌ها و نرخ دستی دلار است؛ کلیدهای API در آن ذخیره نمی‌شوند.",style=MaterialTheme.typography.bodySmall)
   OutlinedButton(onClick=onExport,enabled=!fileBusy&&!saving,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("ذخیرهٔ فایل پشتیبان")}
   OutlinedButton(onClick=onRestore,enabled=!fileBusy&&!saving,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("بازیابی از فایل")}
   Text("دارایی من · نسخهٔ ۲.۷",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}
