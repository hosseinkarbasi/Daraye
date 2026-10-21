@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.darayi.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode

@Composable fun DarayiTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialExpressiveTheme(colorScheme = colors, typography = DarayiTypography, content = content)
    }
}
data class PortfolioHomeState(val holdings: List<Holding>, val quotes: Map<String, Quote>, val states: Map<PriceSource, SourceState>,
    val manualFx: String = "", val loading: Boolean = false, val busy: Boolean = false, val failed: Boolean = false, val automaticFx: Boolean = false)

@Composable fun PortfolioHome(state: PortfolioHomeState, now: Long, onAdd: () -> Unit, onEdit: (Holding) -> Unit,
    onSettings: () -> Unit, onRefresh: () -> Unit, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var sortName by rememberSaveable { mutableStateOf(HoldingSort.VALUE.name) }
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    val fx = selectFx(state.quotes, state.manualFx, now)
    val rows = remember(state.holdings, state.quotes, fx.rate) { valued(state.holdings, state.quotes, fx.rate) }
    val filtered = remember(rows, query, category, sortName) { filterHoldings(rows, query, category, HoldingSort.valueOf(sortName)) }
    val total = rows.mapNotNull { it.toman }.fold(zero, BigDecimal::add)
    val unknown = rows.count { it.toman == null }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("دارایی من", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "تنظیمات") } },
            actions = { IconButton(onClick = onRefresh, enabled = !state.busy && !state.loading && !state.failed) {
                if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, "دریافت قیمت روز")
            } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        bottomBar = { Surface(color = MaterialTheme.colorScheme.background) {
            Button(onClick = onAdd, enabled = !state.loading && !state.failed, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 56.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("افزودن دارایی")
            }
        } }, snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("portfolioList"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ارزش کل دارایی‌ها", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${money(total)} تومان", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(if (fx.rate > zero) "${format(total.divide(fx.rate, 2, RoundingMode.HALF_UP))} دلار" else "— دلار", style = MaterialTheme.typography.titleMedium, color = Mint)
                    if (unknown > 0) Text("جمع ناقص · $unknown دارایی بدون قیمت یا نرخ تبدیل", style = MaterialTheme.typography.bodySmall, color = palette[0])
                    Text(when (fx.kind) {
                        FxKind.FRESH -> "هر دلار ${money(fx.rate)} تومان · نرخ دریافتی"
                        FxKind.CACHED -> "هر دلار ${money(fx.rate)} تومان · نرخ ذخیره‌شده و قدیمی"
                        FxKind.MANUAL -> "هر دلار ${money(fx.rate)} تومان · نرخ دستی"
                        FxKind.MISSING -> "نرخ دلار برای تبدیل موجود نیست"
                    }, style = MaterialTheme.typography.bodySmall, color = if (fx.kind == FxKind.CACHED) palette[0] else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("در حال خواندن سبد…", style = MaterialTheme.typography.bodySmall) }
            if (state.failed) item { Text("اطلاعات قبلی خوانده نشد؛ برای جلوگیری از تغییر داده‌ها، ذخیره متوقف است.", color = MaterialTheme.colorScheme.error) }
            if (!state.loading && state.holdings.isNotEmpty()) item { PriceStatusCard(state, fx, now, onRefresh) }
            if (rows.isNotEmpty()) item { AllocationCard(rows, total, unknown) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("دارایی‌ها", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if(toolsOpen){query="";category=null};toolsOpen=!toolsOpen }) {
                            Icon(if(toolsOpen)Icons.Default.Close else Icons.Default.Search,if(toolsOpen)"بستن جست‌وجو" else "جست‌وجو و فیلتر")
                        }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.KeyboardArrowDown, "مرتب‌سازی") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                HoldingSort.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { sortName = option.name; menu = false }) }
                            }
                        }
                    }
                    if(toolsOpen){
                    OutlinedTextField(query, { query = it }, placeholder = { Text("جست‌وجو در سبد") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth().testTag("portfolioSearch"), singleLine = true, shape = RoundedCornerShape(18.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = category == null, onClick = { category = null }, label = { Text("همه") })
                        categories.forEach { group -> FilterChip(selected = category == group, onClick = { category = if (category == group) null else group }, label = { Text(shortCategory(group)) }, modifier = Modifier.testTag("filter-${group}")) }
                    }
                    }
                }
            }
            if (filtered.isEmpty() && !state.loading) item {
                Text(if (rows.isEmpty()) "هنوز دارایی ثبت نکرده‌ای؛ اولین دارایی را اضافه کن." else "دارایی مطابق جست‌وجو یا فیلتر پیدا نشد.", Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(filtered, key = { it.holding.id }) { row -> HoldingCard(row) { onEdit(row.holding) } }
        }
    }
}

@Composable private fun AllocationCard(rows: List<Valued>, total: BigDecimal, unknown: Int) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val values = categories.map { group -> rows.filter { it.holding.type == group }.mapNotNull { it.toman }.fold(zero, BigDecimal::add) }
    Card(onClick = { expanded = !expanded }, shape = RoundedCornerShape(22.dp), modifier = Modifier.testTag("allocation")) {
        Column(Modifier.fillMaxWidth().animateContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ترکیب دارایی‌ها", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "بستن جزئیات ترکیب" else "جزئیات ترکیب")
            }
            Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))) {
                values.forEachIndexed { index, value -> if (value > zero && total > zero) Box(Modifier.weight(value.divide(total, 8, RoundingMode.HALF_UP).toFloat().coerceAtLeast(.00001f)).fillMaxHeight().background(palette[index])) }
            }
            if (expanded) {
                categories.forEachIndexed { i, group -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(palette[i]))
                    Text(group, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(if (total > zero) "${format(share(values[i], total))}٪" else "—", color = palette[i], style = MaterialTheme.typography.labelMedium)
                } }
                if (unknown > 0) Text("درصدها از دارایی‌های دارای ارزش مشخص محاسبه شده‌اند.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun PriceStatusCard(state: PortfolioHomeState, fx: FxSelection, now: Long, onRefresh: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val health = priceHealth(state.holdings, state.quotes, state.states, fx, now, state.automaticFx)
    val color = if (health == PriceHealth.FRESH || health == PriceHealth.MANUAL) Mint else palette[0]
    val title = if (state.busy) "در حال دریافت قیمت‌ها…" else when (health) {
        PriceHealth.FRESH -> "قیمت‌ها به‌روز هستند"
        PriceHealth.CACHED -> "نمایش قیمت‌های ذخیره‌شده"
        PriceHealth.PARTIAL -> "دریافت بعضی قیمت‌ها ناموفق بود"
        PriceHealth.MANUAL -> "قیمت‌های دستی"
    }
    Card(onClick = { expanded = !expanded }, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .09f)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(8.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = color)
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "جزئیات قیمت‌ها", tint = color)
            }
            if (expanded) {
                if(state.holdings.any { !it.manual && sourceFor(it) == null }) Text("برای سایر دارایی‌ها قیمت دستی ثبت کن؛ منبع قیمت آنلاین ندارند.", style = MaterialTheme.typography.bodySmall)
                val sources = state.holdings.mapNotNull(::sourceFor).toSet() + if (state.automaticFx || fx.kind == FxKind.FRESH || fx.kind == FxKind.CACHED) setOf(PriceSource.NAVASAN) else emptySet()
                sources.forEach { source ->
                    val status = state.states[source]
                    Text("${source.label} · آخرین دریافت موفق: ${ageLabel(status?.lastSuccessAt ?: 0, now)}", style = MaterialTheme.typography.bodySmall)
                    val sourceQuotes = state.holdings.filter { sourceFor(it) == source }.mapNotNull { state.quotes[it.key] } +
                        if(source == PriceSource.NAVASAN) listOfNotNull(state.quotes["USD"]) else emptyList()
                    sourceQuotes.map(::sourceTimeMillis).filter { it > 0 }.minOrNull()?.let { time ->
                        Text("زمان دادهٔ منبع: ${ageLabel(time, now)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    status?.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = color) }
                }
                if (health == PriceHealth.CACHED) Text("جمع سبد با قیمت‌های ذخیره‌شده محاسبه شده است.", style = MaterialTheme.typography.bodySmall)
                if (health != PriceHealth.MANUAL) TextButton(onClick = onRefresh, enabled = !state.busy) { Text("دریافت دوباره") }
            }
        }
    }
}
internal fun ageLabel(time: Long, now: Long): String = when {
    time <= 0 -> "هنوز دریافت نشده"
    now < time -> "زمان نامشخص"
    now - time < 60_000 -> "همین الان"
    now - time < 3_600_000 -> "${(now - time) / 60_000} دقیقه پیش"
    now - time < 86_400_000 -> "${(now - time) / 3_600_000} ساعت پیش"
    else -> "${(now - time) / 86_400_000} روز پیش"
}
internal fun shortCategory(category: String) = when (category) {
    AssetCategory.PHYSICAL_GOLD -> "طلای فیزیکی"; AssetCategory.METAL_FUNDS -> "صندوق فلزات"; AssetCategory.OTHER -> "سایر"; else -> category
}

@Composable fun HoldingCard(row: Valued, onClick: () -> Unit) {
    val h = row.holding
    val title = buildAnnotatedString {
        withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(format(h.qty)); append(" ") }
        append(holdingTitle(h).removePrefix("${format(h.qty)} "))
    }
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.testTag("holding-${h.id}")) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            val narrow = maxWidth < 270.dp || LocalDensity.current.fontScale > 1.3f
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 25.sp))
                    if (!narrow) ValueLabel(row)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    val change = row.quote?.change
                    Text(if (change == null) "—" else "${if (change > zero) "+" else ""}${format(change.setScale(2, RoundingMode.HALF_UP))}٪",
                        style = MaterialTheme.typography.labelMedium, color = if (change == null || change == zero) MaterialTheme.colorScheme.onSurfaceVariant else if (change > zero) Mint else MaterialTheme.colorScheme.error)
                    if (narrow) ValueLabel(row)
                }
            }
        }
    }
}
@Composable private fun ValueLabel(row: Valued) {
    Column(horizontalAlignment = Alignment.End) {
        Text(row.toman?.let(::money) ?: "نامشخص", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("تومان", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun UndoRemovalEffect(removed: RemovedHolding?, snackbar: SnackbarHostState, onUndo: (String) -> Unit, onDismiss: (String) -> Unit) {
 LaunchedEffect(removed?.token) {
  removed?.let {
   val result=snackbar.showSnackbar("${it.holding.name} حذف شد",actionLabel="برگردان",withDismissAction=true,duration=SnackbarDuration.Long)
   if(result==SnackbarResult.ActionPerformed)onUndo(it.token)else onDismiss(it.token)
  }
 }
}
