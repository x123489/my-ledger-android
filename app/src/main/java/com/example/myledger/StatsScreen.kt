package com.example.myledger

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun StatsScreen() {
    val cal = java.util.Calendar.getInstance()
    val ymNow = Util.ym(cal)
    val yearNow = cal.get(java.util.Calendar.YEAR)
    var startD by remember { mutableStateOf(ymNow + "-01") }
    var endD by remember { mutableStateOf(Util.today()) }
    var statType by remember { mutableStateOf("expense") }
    var compareCat by remember { mutableStateOf(LedgerState.topCats("expense").firstOrNull()?.let { LedgerState.catPath(it.id) } ?: "餐饮") }

    val list = LedgerState.transactions.filter { it.date >= startD && it.date <= endD }
    val months = Util.monthsBetween(startD, endD)
    val monthsCount = months.size.coerceAtLeast(1)
    val incByMonth = months.map { m -> list.filter { it.type == "income" && Util.monthOf(it.date) == m }.sumOf { it.amount } }
    val expByMonth = months.map { m -> list.filter { it.type == "expense" && Util.monthOf(it.date) == m }.sumOf { it.amount } }

    val income = list.filter { it.type == "income" }.sumOf { it.amount }
    val expense = list.filter { it.type == "expense" }.sumOf { it.amount }
    val balance = income - expense
    val profitRate = if (income > 0) (income - expense) / income * 100 else 0.0

    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // 时间段：仅自定义
        Text("统计时间段", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val p = startD.split("-").map { it.toInt() }
                DatePickerDialog(ctx, { _, y, m, d -> startD = String.format("%04d-%02d-%02d", y, m + 1, d) }, p[0], p[1] - 1, p[2]).show()
            }, Modifier.weight(1f)) { Text("起 $startD", fontSize = 12.sp) }
            OutlinedButton(onClick = {
                val p = endD.split("-").map { it.toInt() }
                DatePickerDialog(ctx, { _, y, m, d -> endD = String.format("%04d-%02d-%02d", y, m + 1, d) }, p[0], p[1] - 1, p[2]).show()
            }, Modifier.weight(1f)) { Text("止 $endD", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetChip("本月", Modifier.weight(1f)) { startD = ymNow + "-01"; endD = Util.today() }
            PresetChip("近3月", Modifier.weight(1f)) { startD = shiftMonth(ymNow, -2) + "-01"; endD = Util.today() }
            PresetChip("今年", Modifier.weight(1f)) { startD = "$yearNow-01-01"; endD = Util.today() }
            PresetChip("全部", Modifier.weight(1f)) { startD = (LedgerState.transactions.minOfOrNull { it.date } ?: "2000-01-01"); endD = Util.today() }
        }

        Spacer(Modifier.height(16.dp))
        // 汇总（重设计，宽松不拥挤）
        SummaryCard(income, expense, balance, profitRate)

        Spacer(Modifier.height(20.dp))
        // 收支构成（重设计，宽松）
        CompositionSection(list, statType)

        Spacer(Modifier.height(22.dp))
        // 月度收支趋势
        Text("月度收支趋势", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
        Spacer(Modifier.height(8.dp))
        if (months.isEmpty()) {
            Text("暂无数据", color = TextMuted)
        } else {
            MonthTrendChart(months, incByMonth, expByMonth)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                months.forEach { m ->
                    val short = m.takeLast(2) + "/" + m.take(4)
                    Text(short, Modifier.width(48.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = TextMuted)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                LegendDot(ExpenseRed, "支出"); Spacer(Modifier.width(12.dp)); LegendDot(IncomeGreen, "收入")
            }
        }

        Spacer(Modifier.height(22.dp))
        // 分类月度对比
        Text("分类月度对比", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
        Spacer(Modifier.height(8.dp))
        var menuOpen by remember { mutableStateOf(false) }
        val allCats = LedgerState.categories.map { LedgerState.catPath(it.id) }.distinct()
        Box {
            OutlinedButton(onClick = { menuOpen = true }) { Text("分类：$compareCat") }
            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                allCats.forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = { compareCat = c; menuOpen = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val cmpMonths = months
        val cmpVals = cmpMonths.map { m ->
            list.filter { (it.cat == compareCat || it.cat.startsWith(compareCat + "/")) && Util.monthOf(it.date) == m }.sumOf { it.amount }
        }
        if (cmpVals.all { it == 0.0 }) {
            Text("该分类在所选时间段没有记录", color = TextMuted)
        } else {
            CategoryCompareChart(cmpMonths, cmpVals)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                cmpMonths.forEach { m ->
                    Text(m.takeLast(2) + "/" + m.take(4), Modifier.width(48.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = TextMuted)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                LegendDot(ExpenseRed, "净支出（绿=冲销）")
            }
        }

        Spacer(Modifier.height(22.dp))
        // 数据小洞察
        Text("数据小洞察", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
        Spacer(Modifier.height(8.dp))
        val expenses = list.filter { it.type == "expense" }
        val totalEx = expenses.sumOf { it.amount }
        val days = list.map { it.date }.distinct().size
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SoftOrange)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                InsightLine("📌 本期间共支出 ${Util.money(totalEx)}，日均约 ${Util.money(if (days > 0) totalEx / days else 0.0)}（按 $days 天计）。")
                val maxT = expenses.maxByOrNull { it.amount }
                if (maxT != null) InsightLine("💸 最大单笔支出：${maxT.cat} ${Util.money(Math.abs(maxT.amount))}（${maxT.date}）")
                val byCat = list.filter { it.type == "expense" }.groupBy { it.cat }.mapValues { it.value.sumOf { t -> t.amount } }
                val top = byCat.maxByOrNull { it.value }
                if (top != null) InsightLine("🏆 支出最多的分类：${top.key}，共 ${Util.money(top.value)}，占支出 ${if (totalEx > 0) (top.value / totalEx * 100).toFixed(1) else "0"}%")
            }
        }

        Spacer(Modifier.height(22.dp))
        // 预算分析（整体 + 分类）
        BudgetAnalysisSection(list, monthsCount)
    }
}

@Composable
fun PresetChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand)) {
        Text(label, fontSize = 12.sp, color = Brand)
    }
}

/** 汇总卡：收入/支出在上，结余放大居中，结余率以进度条呈现，避免拥挤 */
@Composable
fun SummaryCard(income: Double, expense: Double, balance: Double, profitRate: Double) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("收入", fontSize = 13.sp, color = TextMuted)
                    Spacer(Modifier.height(4.dp))
                    Text(Util.money(income), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("支出", fontSize = 13.sp, color = TextMuted)
                    Spacer(Modifier.height(4.dp))
                    Text(Util.money(expense), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ExpenseRed)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("结余", fontSize = 14.sp, color = TextMuted)
                Spacer(Modifier.weight(1f))
                Text(Util.money(balance), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextMain)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("结余率", fontSize = 13.sp, color = TextMuted)
                Spacer(Modifier.weight(1f))
                Text("${profitRate.toFixed(0)}%", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (profitRate >= 0) Brand else ExpenseRed)
            }
            Spacer(Modifier.height(6.dp))
            val pr = profitRate.toFloat().coerceIn(0f, 100f)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFEDEFF5))) {
                Box(Modifier.fillMaxWidth(pr / 100f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (profitRate >= 0) Brand else ExpenseRed))
            }
        }
    }
}

/** 收支构成：饼图 + 精简图例（最多6项+其他），排行条间距加大避免拥挤 */
@Composable
fun CompositionSection(list: List<Tx>, statType: String) {
    Text("收支构成", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
    Spacer(Modifier.height(8.dp))
    // 类型切换
    var st by remember { mutableStateOf(statType) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { st = "expense" }, Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = if (st == "expense") ExpenseRed else Color(0xFFE5E7EB), contentColor = if (st == "expense") Color.White else TextMuted),
            shape = RoundedCornerShape(10.dp)) { Text("支出分类") }
        Button(onClick = { st = "income" }, Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = if (st == "income") IncomeGreen else Color(0xFFE5E7EB), contentColor = if (st == "income") Color.White else TextMuted),
            shape = RoundedCornerShape(10.dp)) { Text("收入分类") }
    }
    Spacer(Modifier.height(12.dp))
    val byCat = list.filter { it.type == st }
        .groupBy { it.cat }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .toList().sortedByDescending { it.second }
    val catEntries = byCat.map { it.first to Math.max(it.second, 0.0) }.filter { it.second > 0 }
    if (catEntries.isEmpty()) {
        Text("本期间暂无${if (st == "expense") "支出" else "收入"}", color = TextMuted)
    } else {
        val total = catEntries.sumOf { it.second }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            PieChart(catEntries, Modifier.size(150.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                catEntries.take(6).forEachIndexed { i, (c, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(Util.CHART_COLORS[i % Util.CHART_COLORS.size])))
                        Spacer(Modifier.width(8.dp))
                        Text(c, Modifier.weight(1f), fontSize = 13.sp, color = TextMain)
                        Text("${(v / total * 100).toFixed(1)}%", fontSize = 13.sp, color = TextMuted)
                    }
                }
                val others = catEntries.drop(6)
                if (others.isNotEmpty()) {
                    val otherSum = others.sumOf { it.second }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFB0B7C3)))
                        Spacer(Modifier.width(8.dp))
                        Text("其他 ${others.size} 项", Modifier.weight(1f), fontSize = 13.sp, color = TextMuted)
                        Text("${(otherSum / total * 100).toFixed(1)}%", fontSize = 13.sp, color = TextMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        catEntries.take(6).forEachIndexed { i, (c, v) ->
            val pct = (v / total * 100).toFloat()
            Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c, fontSize = 13.sp, color = TextMain)
                    Text("${Util.money(v)} · ${pct.toFixed(1)}%", fontSize = 12.sp, color = TextMuted)
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEDEFF5))) {
                    Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(Util.CHART_COLORS[i % Util.CHART_COLORS.size])))
                }
            }
        }
    }
}

/** 预算分析：整体月预算 + 按分类预算（按所选时间段时长折算） */
@Composable
fun BudgetAnalysisSection(list: List<Tx>, monthsCount: Int) {
    Text("预算分析", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
    Spacer(Modifier.height(8.dp))
    val bud = LedgerState.budget
    val catBud = LedgerState.categoryBudgets.toList().sortedByDescending { it.second }
    if (bud <= 0 && catBud.isEmpty()) {
        Text("尚未设置预算，去“我的 → 预算管理”设置每月预算或分类预算吧。", color = TextMuted)
    } else {
        if (bud > 0) {
            val rangeBud = bud * monthsCount
            val spent = list.filter { it.type == "expense" }.sumOf { it.amount }
            BudgetBarCard("整体月预算（${monthsCount}个月）", spent, rangeBud)
        }
        if (catBud.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("分类预算", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            catBud.forEach { (cp, lim) ->
                val spent = list.filter { it.type == "expense" && (it.cat == cp || it.cat.startsWith(cp + "/")) }.sumOf { it.amount }
                val rangeLim = lim * monthsCount
                BudgetBarCard(cp, spent, rangeLim)
            }
        }
    }
}

@Composable
fun BudgetBarCard(label: String, spent: Double, limit: Double) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Text("${Util.money(spent)} / ${Util.money(limit)}", fontSize = 13.sp, color = TextMuted)
            }
            Spacer(Modifier.height(8.dp))
            val ratio = if (limit > 0) (spent / limit * 100).toFloat().coerceAtMost(100f) else 0f
            val over = spent > limit && limit > 0
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFEDEFF5))) {
                Box(Modifier.fillMaxWidth(ratio / 100f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(if (over) ExpenseRed else Brand))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "还剩 ${Util.money(Math.max(limit - spent, 0.0))}" + if (over) "（已超出 ${Util.money(spent - limit)}）" else "",
                fontSize = 13.sp, color = if (over) ExpenseRed else TextMuted
            )
        }
    }
}

@Composable
fun SumItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
    }
}

@Composable
fun InsightLine(text: String) {
    Text(text, fontSize = 13.sp, color = TextMain, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = TextMuted)
    }
}

// 扩展：Double/ Float 保留一位小数
fun Double.toFixed(n: Int): String = String.format(Locale.US, "%.${n}f", this)
fun Float.toFixed(n: Int): String = String.format(Locale.US, "%.${n}f", this)

/** 根据时间范围，返回（筛选后的记录, 标签, 有记录的天数） */
fun analyzeRange(period: String, month: String, start: String, end: String): Triple<List<Tx>, String, Int> {
    val all = LedgerState.transactions
    val (y, m) = month.split("-").map { it.toInt() }
    val list = when (period) {
        "month" -> all.filter { Util.monthOf(it.date) == month }
        "3m" -> {
            val months = (0..2).map { offset -> shiftMonth(month, -offset) }
            all.filter { Util.monthOf(it.date) in months }
        }
        "6m" -> {
            val months = (0..5).map { offset -> shiftMonth(month, -offset) }
            all.filter { Util.monthOf(it.date) in months }
        }
        "year" -> all.filter { it.date.startsWith("$y-") }
        "custom" -> all.filter { it.date >= start && it.date <= end }
        else -> all
    }
    val label = when (period) {
        "month" -> month
        "3m" -> "近3月"
        "6m" -> "近6月"
        "year" -> "${y}年"
        "custom" -> "$start ~ $end"
        else -> "全部"
    }
    val days = list.map { it.date }.distinct().size
    return Triple(list, label, days)
}

/** 趋势图用的月份列表 */
fun trendMonths(period: String, month: String, start: String, end: String): List<String> {
    val y = month.split("-").map { it.toInt() }.firstOrNull()
        ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    return when (period) {
        "month" -> listOf(month)
        "3m" -> (0..2).map { offset -> shiftMonth(month, -offset) }
        "6m" -> (0..5).map { offset -> shiftMonth(month, -offset) }
        "year" -> (0..11).map { i -> String.format("%04d-%02d", y, i + 1) }
        "custom" -> Util.monthsBetween(start, end)
        "all" -> LedgerState.transactions.map { Util.monthOf(it.date) }.filter { it.isNotBlank() }.distinct().sorted()
        else -> listOf(month)
    }.distinct().sorted()
}

/** 上一周期支出（用于环比） */
fun prevExpense(period: String, month: String): Double? {
    val all = LedgerState.transactions
    val (y, m) = month.split("-").map { it.toInt() }
    val prevList = when (period) {
        "month" -> all.filter { Util.monthOf(it.date) == shiftMonth(month, -1) }
        "year" -> all.filter { it.date.startsWith(String.format("%04d-", y - 1)) }
        else -> null
    }
    return prevList?.filter { it.type == "expense" }?.sumOf { it.amount }
}
