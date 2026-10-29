package com.example.myledger

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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun StatsScreen() {
    var period by remember { mutableStateOf("month") }
    var month by remember { mutableStateOf(Util.ym(java.util.Calendar.getInstance())) }
    var statType by remember { mutableStateOf("expense") }
    var compareCat by remember { mutableStateOf(Util.EXPENSE_CATS[0]) }

    val result = analyzeRange(period, month)
    val list = result.first
    val label = result.second
    val days = result.third

    val income = list.filter { it.type == "income" }.sumOf { it.amount }
    val expense = list.filter { it.type == "expense" }.sumOf { it.amount }
    val profitRate = if (income > 0) (income - expense) / income * 100 else 0.0

    // 趋势月份 & 收支
    val months = trendMonths(period, month)
    val incByMonth = months.map { m -> list.filter { it.type == "income" && Util.monthOf(it.date) == m }.sumOf { it.amount } }
    val expByMonth = months.map { m -> list.filter { it.type == "expense" && Util.monthOf(it.date) == m }.sumOf { it.amount } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // 时间范围切换
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("month" to "本月", "3m" to "近3月", "year" to "本年", "all" to "全部").forEach { (p, lab) ->
                val sel = period == p
                Button(
                    onClick = { period = p }, Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (sel) Brand else Color(0xFFE5E7EB), contentColor = if (sel) Color.White else TextMuted),
                    shape = RoundedCornerShape(10.dp)
                ) { Text(lab, fontSize = 13.sp) }
            }
        }

        if (period != "all") {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { month = shiftMonth(month, if (period == "year") 12 else 1) }) { Text("‹") }
                Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedButton(onClick = { month = shiftMonth(month, if (period == "year") 12 else 1) }) { Text("›") }
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(14.dp))
        // 汇总
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                SumItem("收入", Util.money(income), IncomeGreen)
                SumItem("支出", Util.money(expense), ExpenseRed)
                SumItem("结余", Util.money(income - expense), TextMain)
                SumItem("结余率", String.format("%.0f%%", profitRate), Brand)
            }
        }

        Spacer(Modifier.height(18.dp))
        // 收支构成
        Text("收支构成", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tsel = statType == "expense"
            Button(onClick = { statType = "expense" }, Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (tsel) ExpenseRed else Color(0xFFE5E7EB), contentColor = if (tsel) Color.White else TextMuted),
                shape = RoundedCornerShape(10.dp)) { Text("支出分类") }
            Button(onClick = { statType = "income" }, Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (!tsel) IncomeGreen else Color(0xFFE5E7EB), contentColor = if (!tsel) Color.White else TextMuted),
                shape = RoundedCornerShape(10.dp)) { Text("收入分类") }
        }
        Spacer(Modifier.height(12.dp))
        val byCat = list.filter { it.type == statType }
            .groupBy { it.cat }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList().sortedByDescending { it.second }
        val catEntries = byCat.map { (c, v) -> c to if (statType == "expense") Math.max(v, 0.0) else Math.max(v, 0.0) }
            .filter { it.second > 0 }
        if (catEntries.isEmpty()) {
            Text("本期间暂无${if (statType == "expense") "支出" else "收入"}", color = TextMuted)
        } else {
            val total = catEntries.sumOf { it.second }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PieChart(catEntries)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    catEntries.forEachIndexed { i, (c, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(Util.CHART_COLORS[i % Util.CHART_COLORS.size])))
                            Spacer(Modifier.width(8.dp))
                            Text(c, Modifier.weight(1f), fontSize = 13.sp, color = TextMain)
                            Text("${Util.money(v)} · ${(v / total * 100).toFixed(1)}%", fontSize = 13.sp, color = TextMuted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 排行条
            catEntries.forEachIndexed { i, (c, v) ->
                val pct = (v / total * 100).toFloat()
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c, fontSize = 13.sp, color = TextMain)
                        Text("${Util.money(v)} · ${pct.toFixed(1)}%", fontSize = 12.sp, color = TextMuted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEDEFF5))) {
                        Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(Util.CHART_COLORS[i % Util.CHART_COLORS.size])))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
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

        Spacer(Modifier.height(20.dp))
        // 分类月度对比
        Text("分类月度对比（最近6个月净额）", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
        Spacer(Modifier.height(8.dp))
        var menuOpen by remember { mutableStateOf(false) }
        val allCats = (Util.EXPENSE_CATS + Util.INCOME_CATS).distinct()
        Box {
            OutlinedButton(onClick = { menuOpen = true }) { Text("分类：$compareCat") }
            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                allCats.forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = { compareCat = c; menuOpen = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val cmpMonths = trendMonths("6m", month)
        val cmpVals = cmpMonths.map { m ->
            list.filter { it.cat == compareCat && Util.monthOf(it.date) == m }.sumOf { it.amount }
        }
        if (cmpVals.all { it == 0.0 }) {
            Text("该分类在最近6个月没有记录", color = TextMuted)
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

        Spacer(Modifier.height(20.dp))
        // 数据小洞察
        Text("数据小洞察", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
        Spacer(Modifier.height(8.dp))
        val expenses = list.filter { it.type == "expense" }
        val totalEx = expenses.sumOf { it.amount }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F1FF))) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                InsightLine("📌 本期间共支出 ${Util.money(totalEx)}，日均约 ${Util.money(if (days > 0) totalEx / days else 0.0)}（按 $days 天计）。")
                val maxT = expenses.maxByOrNull { it.amount }
                if (maxT != null) InsightLine("💸 最大单笔支出：${maxT.cat} ${Util.money(Math.abs(maxT.amount))}（${maxT.date}）")
                val top = byCat.maxByOrNull { it.second }
                if (top != null) InsightLine("🏆 支出最多的分类：${top.first}，共 ${Util.money(top.second)}，占支出 ${if (totalEx > 0) (top.second / totalEx * 100).toFixed(1) else "0"}%")
                val prev = prevExpense(period, month)
                if (prev != null) {
                    if (prev > 0) {
                        val diff = totalEx - prev
                        val pct = diff / prev * 100
                        val up = diff >= 0
                        InsightLine("🔄 对比上一周期，支出 ${if (up) "↑" else "↓"} ${Math.abs(pct).toFixed(1)}%（${(if (up) "多花" else "少花")} ${Util.money(Math.abs(diff))}）。")
                    } else {
                        InsightLine("🔄 上一周期暂无支出数据，无法对比。")
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        // 预算进度
        val bud = LedgerState.budget
        val curMonthEx = Util.monthTx(month).filter { it.type == "expense" }.sumOf { it.amount }
        Text("本月预算", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
        Spacer(Modifier.height(8.dp))
        if (bud <= 0) {
            Text("尚未设置预算，去“我的”页面设置一个每月支出上限吧。", color = TextMuted)
        } else {
            val ratio = (curMonthEx / bud * 100).toFloat().coerceAtMost(100f)
            val over = curMonthEx > bud
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("已花 ${Util.money(curMonthEx)} / 预算 ${Util.money(bud)}", fontWeight = FontWeight.Bold, color = TextMain)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFEDEFF5))) {
                        Box(Modifier.fillMaxWidth(ratio / 100f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(if (over) ExpenseRed else Brand))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("还剩 ${Util.money(Math.max(bud - curMonthEx, 0.0))}" + if (over) "（已超出 ${Util.money(curMonthEx - bud)}）" else "", fontSize = 13.sp, color = if (over) ExpenseRed else TextMuted)
                }
            }
            if (over) Text("⚠️ 本月支出已超出预算，注意控制哦！", color = ExpenseRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
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

/** 根据时间范围 + 锚点月份，返回（筛选后的记录, 标签, 有记录的天数） */
fun analyzeRange(period: String, month: String): Triple<List<Tx>, String, Int> {
    val all = LedgerState.transactions
    val (y, m) = month.split("-").map { it.toInt() }
    val list = when (period) {
        "month" -> all.filter { Util.monthOf(it.date) == month }
        "3m" -> {
            val months = (0..2).map { offset -> shiftMonth(month, -offset) }
            all.filter { Util.monthOf(it.date) in months }
        }
        "year" -> all.filter { it.date.startsWith("$y-") }
        else -> all
    }
    val label = when (period) {
        "month" -> month
        "3m" -> "近3月"
        "year" -> "${y}年"
        else -> "全部"
    }
    val days = list.map { it.date }.distinct().size
    return Triple(list, label, days)
}

/** 趋势图用的月份列表 */
fun trendMonths(period: String, month: String): List<String> {
    val y = month.split("-").map { it.toInt() }.firstOrNull()
        ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    return when (period) {
        "month" -> listOf(month)
        "3m" -> (0..2).map { offset -> shiftMonth(month, -offset) }
        "6m" -> (0..5).map { offset -> shiftMonth(month, -offset) }
        "year" -> (0..11).map { i -> String.format("%04d-%02d", y, i + 1) }
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
