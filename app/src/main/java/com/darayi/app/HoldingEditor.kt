@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.darayi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.math.BigDecimal

@Composable
fun HoldingEditor(
    old: Holding?, assets: List<AssetOption>, catalogBusy: Boolean, catalogError: String?,
    loadCatalog: (String, Boolean) -> Unit, onDismiss: () -> Unit,
    onSave: (Holding) -> Unit, onDelete: (Holding) -> Unit
) {
    var name by rememberSaveable(old?.id) { mutableStateOf(old?.name ?: "") }
    var key by rememberSaveable(old?.id) { mutableStateOf(old?.key ?: "") }
    var qty by rememberSaveable(old?.id) {
        mutableStateOf(old?.qty?.let { if (isGoldWeight(old.key)) it * BigDecimal(1000) else it }?.stripTrailingZeros()?.toPlainString() ?: "")
    }
    var quantityIsMilligrams by rememberSaveable(old?.id) { mutableStateOf(old?.key?.let(::isGoldWeight) ?: false) }
    var category by rememberSaveable(old?.id) { mutableStateOf(old?.type ?: categories[0]) }
    var measure by rememberSaveable(old?.id) { mutableStateOf(old?.measure ?: "") }
    var manual by rememberSaveable(old?.id) { mutableStateOf(old?.manual ?: false) }
    var price by rememberSaveable(old?.id) { mutableStateOf(old?.price?.takeIf { it > zero }?.toPlainString() ?: "") }
    var unit by rememberSaveable(old?.id) { mutableStateOf(old?.unit ?: "تومان") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val selected = assets.firstOrNull { it.key == key && (it.category == category || (usesStockPrices(it.category) && usesStockPrices(category))) }
    val actualMeasure = selected?.measure ?: measure.ifBlank { "عدد" }
    val useManual = manual || category == AssetCategory.OTHER || selected?.manualOnly == true
    val automaticAllowed = category != AssetCategory.OTHER && selected?.manualOnly != true
    val goldWeight = isGoldWeight(key)
    LaunchedEffect(category) { loadCatalog(category, false) }

    fun save() {
        val quantity = if (goldWeight) goldGrams(qty) else number(qty)
        val amount = if (useManual) number(price) else zero
        error = when {
            category != AssetCategory.OTHER && key.isBlank() -> "دارایی را از نتایج جست‌وجو انتخاب کن."
            name.isBlank() -> "نام دارایی را وارد کن."
            quantity == null || quantity <= zero -> "مقدار دارایی باید بیشتر از صفر باشد."
            amount == null || (useManual && amount <= zero) -> "قیمت معتبر و بیشتر از صفر وارد کن."
            else -> null
        }
        if (error == null && quantity != null && amount != null) {
            onSave(Holding(id = old?.id ?: java.util.UUID.randomUUID().toString(), name = name.trim(), key = symbol(key),
                qty = quantity, measure = actualMeasure, type = category, manual = useManual, price = amount, unit = unit))
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                modifier = Modifier.imePadding(),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(if (old == null) "دارایی جدید" else "ویرایش دارایی", style = MaterialTheme.typography.titleMedium) },
                        navigationIcon = {
                            FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.padding(start = 12.dp)) {
                                Icon(Icons.Default.Close, "بستن")
                            }
                        },
                        actions = {
                            if (old != null) IconButton(onClick = { deleteConfirm = true }, modifier = Modifier.padding(end = 12.dp)) {
                                Icon(Icons.Default.Delete, "حذف دارایی", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 12.dp) {
                        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(20.dp)) {
                                Icon(if (old == null) Icons.Default.Add else Icons.Default.Check, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(if (old == null) "افزودن به سبد" else "ذخیرهٔ تغییرات", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (old == null) "دارایی‌ات را ثبت کن" else "سبدت را به‌روز نگه دار", style = MaterialTheme.typography.headlineSmall)
                        Text("دارایی را انتخاب کن و مقدارش را وارد کن.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    EditorSection("۱", "نوع دارایی", "چه چیزی به سبدت اضافه می‌کنی؟") {
                        categories.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { option ->
                                    CategoryTile(option, category == option, Modifier.weight(1f)) {
                                        if (category != option) {
                                            category = option; name = ""; key = ""; measure = ""; qty = ""; quantityIsMilligrams = false
                                            manual = option == AssetCategory.OTHER; price = ""; unit = "تومان"; error = null
                                        }
                                    }
                                }
                            }
                        }
                    }

                    EditorSection("۲", "انتخاب دارایی", if (category == AssetCategory.OTHER) "نام دلخواه دارایی‌ات را بنویس." else "با نام یا نماد پیدایش کن.") {
                        if (category == AssetCategory.OTHER) {
                            OutlinedTextField(name, { name = it; error = null }, label = { Text("نام دارایی") }, placeholder = { Text("مثلاً خودرو یا ملک") },
                                singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
                        } else {
                            AssetSearch(name, category, assets,
                                onQuery = { name = it; key = ""; measure = ""; error = null },
                                onSelect = { asset ->
                                    if (quantityIsMilligrams != isGoldWeight(asset.key)) qty = ""
                                    quantityIsMilligrams = isGoldWeight(asset.key)
                                    name = asset.name; key = asset.key; measure = asset.measure
                                    manual = asset.manualOnly || (old?.manual == true && old.key == asset.key); error = null
                                })
                        }
                        if (key.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("انتخاب شد · واحد: $actualMeasure", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (usesStockPrices(category) || category == AssetCategory.CRYPTO) {
                            if (catalogBusy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("به‌روزرسانی فهرست دارایی‌ها…", style = MaterialTheme.typography.bodySmall)
                            }
                            catalogError?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { loadCatalog(category, true) }, enabled = !catalogBusy) { Text("تلاش دوباره") }
                            }
                        }
                    }

                    EditorSection("۳", if (goldWeight) "وزن طلا" else "مقدار دارایی", if (goldWeight) "وزن را به میلی‌گرم بنویس." else "چقدر از این دارایی داری؟") {
                        OutlinedTextField(
                            value = qty, onValueChange = { qty = it; error = null },
                            placeholder = { Text("۰", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            suffix = { Text(if (goldWeight) "میلی‌گرم" else actualMeasure, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
                            textStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (goldWeight) "وزن (میلی‌گرم)" else "مقدار / تعداد") }
                        )
                        if (goldWeight) {
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .09f), shape = RoundedCornerShape(12.dp)) {
                                Text(goldGrams(qty)?.let { "معادل ${format(it)} گرم" } ?: "۶۷۳۸۰ میلی‌گرم = ۶۷٫۳۸۰ گرم",
                                    Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (old != null && goldWeight && old.key == key && old.qty >= BigDecimal(1000) && number(qty)?.compareTo(old.qty * BigDecimal(1000)) == 0) {
                            Text("وزن قبلی ${format(old.qty)} گرم ذخیره شده است. اگر عدد را به میلی‌گرم وارد کرده بودی، اصلاحش کن.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { qty = old.qty.toPlainString() }) { Text("مقدار قبلی را میلی‌گرم حساب کن") }
                        }
                        if (isCertificate(key)) Text("تعداد گواهی سپرده را وارد کن؛ قیمت برای هر گواهی محاسبه می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                        Text("روش قیمت‌گذاری", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PriceMode("قیمت آنلاین", Icons.Default.Refresh, !useManual, automaticAllowed, Modifier.weight(1f)) { manual = false }
                            PriceMode("قیمت دستی", Icons.Default.Edit, useManual, true, Modifier.weight(1f)) { manual = true }
                        }
                        if (useManual) {
                            Choice("واحد قیمت", unit, listOf("تومان", "دلار")) { unit = it }
                            OutlinedTextField(price, { price = it; error = null }, label = { Text(if (goldWeight) "قیمت هر گرم" else "قیمت هر واحد") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                                shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
                        } else Text("قیمت روز با به‌روزرسانی سبد دریافت می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (category == AssetCategory.PHYSICAL_GOLD) Text("ارزش طلا بدون اجرت، مالیات و کارمزد محاسبه می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (key == "IR_GOLD_24K") Text("قیمت آنلاین طلای ۲۴ عیار در این منبع موجود نیست؛ قیمت هر گرم را دستی وارد کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (deleteConfirm && old != null) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("حذف دارایی") },
            text = { Text("${old.name} از فهرست حذف شود؟") },
            confirmButton = { TextButton(onClick = { onDelete(old) }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("انصراف") } })
    }
}

@Composable
private fun EditorSection(number: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Text(number, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun CategoryTile(category: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val index = categories.indexOf(category).coerceAtLeast(0)
    val accent = palette[index]
    val title = when (category) {
        AssetCategory.PHYSICAL_GOLD -> "طلای فیزیکی"
        AssetCategory.STOCKS -> "سهام و گواهی"
        AssetCategory.METAL_FUNDS -> "صندوق فلزات"
        AssetCategory.OTHER -> "سایر دارایی‌ها"
        else -> category
    }
    val glyph = listOf("Au", "$", "▥", "₿", "◈", "+")[index]
    Surface(onClick = onClick, modifier = modifier.semantics { this.selected = selected; role = Role.RadioButton }, shape = RoundedCornerShape(18.dp),
        color = if (selected) accent.copy(alpha = .13f) else MaterialTheme.colorScheme.background.copy(alpha = .45f),
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = .8f) else Color.Transparent)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 14.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Text(glyph, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PriceMode(label: String, icon: ImageVector, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.semantics { this.selected = selected; role = Role.RadioButton }, shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .1f) else Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = if (selected) .6f else .15f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = color.copy(alpha = if (enabled) 1f else .4f))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = if (enabled) 1f else .4f), modifier = Modifier.weight(1f))
        }
    }
}
