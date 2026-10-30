package com.example.myledger

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ListScreen(onEdit: (Tx) -> Unit) {
    val cal = java.util.Calendar.getInstance()
    val thisMonthStart = Util.ym(cal) + "-01"
    var dateStart by remember { mutableStateOf(thisMonthStart) }
    var dateEnd by remember { mutableStateOf(Util.today()) }
    var unlimitedTime by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf("all") }
    var catFilter by remember { mutableStateOf<String?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var amtMin by remember { mutableStateOf("") }
    var amtMax by remember { mutableStateOf("") }
    var noteKw by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("date_desc") }
    var delTarget by remember { mutableStateOf<Tx?>(null) }
    var showFilter by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val all = LedgerState.transactions
    val minV = amtMin.toDoubleOrNull()
    val maxV = amtMax.toDoubleOrNull()

    val base = all.filter { t ->
        if (query.isNotBlank()) {
            val q = query.lowercase()
            val tags = t.tags.joinToString(" ")
            if (!(t.date + " " + Util.typeLabel(t.type) + " " + t.cat + " " + t.note + " " + tags + " " + t.amount).lowercase().contains(q)) return@filter false
        }
        if (typeFilter != "all" && t.type != typeFilter) return@filter false
        if (!unlimitedTime && (t.date < dateStart || t.date > dateEnd)) return@filter false
        if (catFilter != null && t.cat != catFilter && !t.cat.startsWith(catFilter + "/")) return@filter false
        if (tagFilter != null && t.tags.none { it == tagFilter }) return@filter false
        if (minV != null && t.amount < minV) return@filter false
        if (maxV != null && t.amount > maxV) return@filter false
        if (noteKw.isNotBlank() && !t.note.lowercase().contains(noteKw.lowercase())) return@filter false
        true
    }

    val sorted = when (sortMode) {
        "date_desc" -> base.sortedByDescending { it.date }
        "date_asc" -> base.sortedBy { it.date }
        "amt_desc" -> base.sortedByDescending { Math.abs(it.amount) }
        "amt_asc" -> base.sortedBy { Math.abs(it.amount) }
        else -> base.sortedByDescending { it.date }
    }

    val activeCount = (if (!unlimitedTime) 1 else 0) + (if (typeFilter != "all") 1 else 0) +
            (if (catFilter != null) 1 else 0) + (if (tagFilter != null) 1 else 0) +
            (if (minV != null) 1 else 0) + (if (maxV != null) 1 else 0) + (if (noteKw.isNotBlank()) 1 else 0)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, Modifier.weight(1f),
                    placeholder = { Text("搜索关键字") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextMuted) },
                    singleLine = true,
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                        focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedButton(
                    onClick = { showFilter = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (activeCount > 0) Brand else TextMain)
                ) {
                    Text("筛选", color = if (activeCount > 0) Brand else TextMain, fontWeight = if (activeCount > 0) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                }
            }
            if (activeCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("筛选中（${activeCount}）", color = Brand, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        unlimitedTime = false; typeFilter = "all"; catFilter = null; tagFilter = null
                        amtMin = ""; amtMax = ""; noteKw = ""; dateStart = thisMonthStart; dateEnd = Util.today()
                    }) { Text("清除筛选", color = TextMuted, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            var menuOpen by remember { mutableStateOf(false) }
            val sortLabels = mapOf("date_desc" to "日期 新→旧", "date_asc" to "日期 旧→新", "amt_desc" to "金额 高→低", "amt_asc" to "金额 低→高")
            Box {
                OutlinedButton(onClick = { menuOpen = true }) { Text("排序：${sortLabels[sortMode]}", fontSize = 13.sp) }
                DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                    sortLabels.forEach { (k, v) ->
                        DropdownMenuItem(text = { Text(v) }, onClick = { sortMode = k; menuOpen = false })
                    }
                }
            }
        }

        // 汇总
        val income = base.filter { it.type == "income" }.sumOf { it.amount }
        val expense = base.filter { it.type == "expense" }.sumOf { it.amount }
        Row(Modifier.padding(horizontal = 16.dp)) {
            StatCard("收入", Util.money(income), IncomeGreen, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            StatCard("支出", Util.money(expense), ExpenseRed, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            StatCard("结余", Util.money(income - expense), TextMain, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank() && activeCount == 0) "这个月还没有记录，去“记账”记一笔吧～" else "没有匹配的记录", color = TextMuted)
            }
        } else {
            LazyColumn {
                items(sorted) { TxRow(it, onEdit) { delTarget = it } }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // 筛选对话框
    if (showFilter) {
        val catOptions = listOf<String?>(null) + LedgerState.categories.map { LedgerState.catPath(it.id) }.distinct()
        val allTags = all.flatMap { it.tags }.distinct().sorted()
        val tagOptions = listOf<String?>(null) + allTags
        var catMenu by remember { mutableStateOf(false) }
        var tagMenu by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showFilter = false },
            title = { Text("筛选条件", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    // 类型
                    Text("类型", color = TextMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("all" to "全部", "expense" to "支出", "income" to "收入", "transfer" to "转账", "invest" to "投资").forEach { (k, v) ->
                            FilterChip(v, typeFilter == k) { typeFilter = k }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // 时间
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("时间", color = TextMuted, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        FilterChip("不限时间", unlimitedTime) { unlimitedTime = !unlimitedTime }
                    }
                    if (!unlimitedTime) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val p = dateStart.split("-").map { it.toInt() }
                                DatePickerDialog(ctx, { _, y, m, d -> dateStart = String.format("%04d-%02d-%02d", y, m + 1, d) }, p[0], p[1] - 1, p[2]).show()
                            }, Modifier.weight(1f)) { Text("起 $dateStart", fontSize = 12.sp) }
                            OutlinedButton(onClick = {
                                val p = dateEnd.split("-").map { it.toInt() }
                                DatePickerDialog(ctx, { _, y, m, d -> dateEnd = String.format("%04d-%02d-%02d", y, m + 1, d) }, p[0], p[1] - 1, p[2]).show()
                            }, Modifier.weight(1f)) { Text("止 $dateEnd", fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // 分类
                    Text("分类", color = TextMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(onClick = { catMenu = true }, Modifier.fillMaxWidth()) { Text(catFilter ?: "全部分类", fontSize = 13.sp) }
                        DropdownMenu(catMenu, onDismissRequest = { catMenu = false }) {
                            catOptions.forEach { opt ->
                                DropdownMenuItem(text = { Text(opt ?: "全部分类") }, onClick = { catFilter = opt; catMenu = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // 标签
                    Text("标签", color = TextMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(onClick = { tagMenu = true }, Modifier.fillMaxWidth()) { Text(tagFilter ?: "全部标签", fontSize = 13.sp) }
                        DropdownMenu(tagMenu, onDismissRequest = { tagMenu = false }) {
                            tagOptions.forEach { opt ->
                                DropdownMenuItem(text = { Text(opt ?: "全部标签") }, onClick = { tagFilter = opt; tagMenu = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // 金额
                    Text("金额区间（元）", color = TextMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = amtMin, onValueChange = { amtMin = it }, Modifier.weight(1f), placeholder = { Text("最小") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = androidx.compose.material3.TextFieldDefaults.colors(focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite, focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)),
                            shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(value = amtMax, onValueChange = { amtMax = it }, Modifier.weight(1f), placeholder = { Text("最大") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = androidx.compose.material3.TextFieldDefaults.colors(focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite, focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)),
                            shape = RoundedCornerShape(12.dp))
                    }
                    Spacer(Modifier.height(12.dp))

                    // 备注
                    Text("备注包含", color = TextMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = noteKw, onValueChange = { noteKw = it }, Modifier.fillMaxWidth(), placeholder = { Text("如：午饭") },
                        colors = androidx.compose.material3.TextFieldDefaults.colors(focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite, focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)),
                        shape = RoundedCornerShape(12.dp))
                }
            },
            confirmButton = { TextButton(onClick = { showFilter = false }) { Text("完成", color = Brand) } },
            dismissButton = {
                TextButton(onClick = {
                    unlimitedTime = false; typeFilter = "all"; catFilter = null; tagFilter = null
                    amtMin = ""; amtMax = ""; noteKw = ""; dateStart = thisMonthStart; dateEnd = Util.today()
                    showFilter = false
                }) { Text("重置") }
            }
        )
    }

    delTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { delTarget = null },
            title = { Text("删除这笔记录？") },
            text = { Text("${t.cat} ${Util.money(Math.abs(t.amount))} / $t.date") },
            confirmButton = {
                TextButton(onClick = { LedgerState.remove(t.id); delTarget = null }) { Text("删除", color = ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { delTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Brand else Color(0xFFEDEFF5))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.White else TextMain, fontSize = 13.sp)
    }
}

@Composable
fun TxRow(t: Tx, onEdit: (Tx) -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.cat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
                if (t.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(t.note, fontSize = 12.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (t.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        t.tags.forEach { tag ->
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(SoftOrange).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(tag, fontSize = 11.sp, color = Brand)
                            }
                        }
                    }
                }
            }
            val amtColor = when (t.type) {
                "income" -> IncomeGreen
                "transfer" -> TransferBlue
                "invest" -> InvestViolet
                else -> if (t.amount < 0) IncomeGreen else ExpenseRed
            }
            Text(Util.fmtAmt(t), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = amtColor)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Edit, null, tint = TextMuted, modifier = Modifier.size(20.dp).clickable { onEdit(t) })
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Delete, null, tint = ExpenseRed, modifier = Modifier.size(20.dp).clickable { onDelete() })
        }
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 12.sp, color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

fun shiftMonth(month: String, delta: Int): String {
    val (y, m) = month.split("-").map { it.toInt() }
    val c = java.util.Calendar.getInstance()
    c.set(y, m - 1, 1)
    c.add(java.util.Calendar.MONTH, delta)
    return Util.ym(c)
}
