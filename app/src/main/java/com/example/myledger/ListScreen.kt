package com.example.myledger

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ListScreen(onEdit: (Tx) -> Unit) {
    var month by remember { mutableStateOf(Util.ym(java.util.Calendar.getInstance())) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("group") }
    var delTarget by remember { mutableStateOf<Tx?>(null) }

    val all = LedgerState.transactions
    val base = if (query.isBlank()) {
        Util.monthTx(month)
    } else {
        val q = query.lowercase()
        all.filter {
            val tags = it.tags.joinToString(" ")
            (it.date + " " + (if (it.type == "income") "收入" else "支出") + " " + it.cat + " " + it.note + " " + tags + " " + it.amount)
                .lowercase().contains(q)
        }
    }

    val sorted = when (sortMode) {
        "date_desc" -> base.sortedByDescending { it.date }
        "date_asc" -> base.sortedBy { it.date }
        "amt_desc" -> base.sortedByDescending { Math.abs(it.amount) }
        "amt_asc" -> base.sortedBy { Math.abs(it.amount) }
        else -> base.sortedByDescending { it.date }
    }

    Column(Modifier.fillMaxSize()) {
        // 月份导航 / 搜索 / 排序
        Column(Modifier.padding(16.dp)) {
            if (query.isBlank()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { month = shiftMonth(month, -1) }) { Text("‹", fontSize = 20.sp) }
                    Text(month, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedButton(onClick = { month = shiftMonth(month, 1) }) { Text("›", fontSize = 20.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("搜索分类 / 备注 / 金额（跨所有月份）") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextMuted) },
                singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                    focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(10.dp))
            var menuOpen by remember { mutableStateOf(false) }
            val sortLabels = mapOf(
                "group" to "排序：按日期分组", "date_desc" to "排序：日期 新→旧",
                "date_asc" to "排序：日期 旧→新", "amt_desc" to "排序：金额 高→低",
                "amt_asc" to "排序：金额 低→高"
            )
            Box {
                OutlinedButton(onClick = { menuOpen = true }) { Text(sortLabels[sortMode] ?: "排序") }
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
                Text(if (query.isBlank()) "这个月还没有记录，去“记账”记一笔吧～" else "没有匹配的记录", color = TextMuted)
            }
        } else {
            val grouped = if (sortMode == "group") sorted.groupBy { it.date }.toSortedMap(reverseOrder()) else null
            if (grouped != null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    grouped.forEach { (date, items) ->
                        Text(date, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        items.forEach { TxRow(it, onEdit) { delTarget = it } }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                LazyColumn {
                    items(sorted) { TxRow(it, onEdit) { delTarget = it } }
                }
            }
        }
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
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFEEF0FF)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(tag, fontSize = 11.sp, color = Brand)
                            }
                        }
                    }
                }
            }
            val amtColor = when {
                t.type == "income" -> IncomeGreen
                t.amount < 0 -> IncomeGreen
                else -> ExpenseRed
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
