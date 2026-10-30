package com.example.myledger

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- 通用样式（橙色主色调） ----------
val Brand = Color(0xFFFF7A00)
val BrandDark = Color(0xFFE85F00)
val LightBg = Color(0xFFFCF6F0)
val CardWhite = Color(0xFFFFFFFF)
val TextMain = Color(0xFF222222)
val TextMuted = Color(0xFF8A8F99)
val ExpenseRed = Color(0xFFEF4444)
val IncomeGreen = Color(0xFF22C55E)
val TransferBlue = Color(0xFF3B82F6)
val InvestViolet = Color(0xFF8B5CF6)
val SoftOrange = Color(0xFFFFF1E6)

fun toast(ctx: android.content.Context, s: String) =
    Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

// ---------- 主框架 ----------
@Composable
fun LedgerApp() {
    var tab by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Tx?>(null) }

    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Brand,
            background = LightBg,
            surface = CardWhite,
            onBackground = TextMain,
            onSurface = TextMain
        )
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = CardWhite) {
                    val items = listOf(
                        "记账" to Icons.Filled.Add,
                        "明细" to Icons.Filled.List,
                        "统计" to Icons.Filled.Assessment,
                        "我的" to Icons.Filled.Person
                    )
                    items.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(LightBg)
            ) {
                when (tab) {
                    0 -> AddScreen(editing) { editing = null }
                    1 -> ListScreen { tx -> editing = tx; tab = 0 }
                    2 -> StatsScreen()
                    3 -> MineScreen()
                }
            }
        }
    }
}

// ---------- 记账页 ----------
@Composable
fun AddScreen(editing: Tx?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var type by remember { mutableStateOf("expense") }
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    var openParentId by remember { mutableStateOf<String?>(null) }
    var transferFrom by remember { mutableStateOf("") }
    var transferTo by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("0") }
    var date by remember { mutableStateOf(Util.today()) }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    LaunchedEffect(editing?.id) {
        if (editing != null) {
            type = editing.type
            if (editing.type == "transfer") {
                val p = editing.cat.split("→")
                transferFrom = p.getOrNull(0) ?: ""
                transferTo = p.getOrNull(1) ?: ""
                selectedCatId = null; openParentId = null
            } else {
                val f = LedgerState.findCatByPath(editing.type, editing.cat)
                selectedCatId = f?.id
                openParentId = f?.parentId
                transferFrom = ""; transferTo = ""
            }
            amount = Util.moneyNoSymbol(editing.amount)
            date = editing.date
            note = editing.note
            tags = editing.tags.joinToString(" ")
        }
    }

    val typeOptions = listOf(
        "expense" to "支出", "income" to "收入",
        "transfer" to "转账", "invest" to "投资"
    )
    val typeColor = mapOf(
        "expense" to ExpenseRed, "income" to IncomeGreen,
        "transfer" to TransferBlue, "invest" to InvestViolet
    )

    Column(Modifier.fillMaxSize().background(LightBg)) {
        // 内容区：尽量一屏内显示；超小屏仍可滚动，但保存按钮始终常驻底部
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)
        ) {
            if (editing != null) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SoftOrange)) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("正在修改一笔记录", color = Brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextButton(onClick = onDone) { Text("取消编辑", color = TextMuted, fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // 类型切换
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                typeOptions.forEach { (t, label) ->
                    val sel = type == t
                    Button(
                        onClick = {
                            type = t
                            selectedCatId = null; openParentId = null
                            transferFrom = ""; transferTo = ""
                        },
                        Modifier.weight(1f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sel) typeColor[t]!! else Color(0xFFE5E7EB),
                            contentColor = if (sel) Color.White else TextMuted
                        ),
                        shape = RoundedCornerShape(9.dp)
                    ) { Text(label, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))

            // 金额 + 嵌入式计算器
            Text("金额", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            val disp = amount.replace("*", "×").replace("/", "÷")
            OutlinedTextField(
                value = disp,
                onValueChange = { v ->
                    val cleaned = v.replace("×", "*").replace("÷", "/")
                    if (cleaned.isEmpty()) amount = "0"
                    else if (cleaned.matches(Regex("""^-?\d*\.?\d*([+\-*/]-?\d*\.?\d*)*$"""))) amount = cleaned
                },
                Modifier.fillMaxWidth(),
                placeholder = { Text("0.00") },
                prefix = { Text("¥", color = TextMuted) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                    focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(6.dp))
            Calculator(amount, { amount = it })
            Spacer(Modifier.height(10.dp))

            // 分类 / 账户
            if (type == "transfer") {
                Text("转出账户", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                WrapChips(Util.TRANSFER_ACCOUNTS, transferFrom) { transferFrom = it }
                Spacer(Modifier.height(8.dp))
                Text("转入账户", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                WrapChips(Util.TRANSFER_ACCOUNTS, transferTo) { transferTo = it }
            } else {
                val current = if (openParentId == null) LedgerState.topCats(type) else LedgerState.childrenOf(openParentId!!)
                Text(if (type == "invest") "投资品种（可多级，最多四级）" else "分类（点选可进入下级，最多四级）", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                val crumbs = LedgerState.breadcrumbPath(openParentId)
                if (crumbs.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("全部", Modifier.clickable { openParentId = null; selectedCatId = null }.padding(vertical = 4.dp, horizontal = 2.dp), color = Brand, fontSize = 12.sp)
                        crumbs.forEach { c ->
                            Text("›", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                            Text(c.name, Modifier.clickable { openParentId = c.id }.padding(vertical = 4.dp, horizontal = 2.dp), color = Brand, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (current.isEmpty()) {
                    Text("该分类下暂无内容，去“我的→分类管理”添加", color = TextMuted, fontSize = 12.sp)
                } else {
                    CatChipRow(current, selectedCatId) { c ->
                        selectedCatId = c.id
                        if (LedgerState.childrenOf(c.id).isNotEmpty()) openParentId = c.id
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // 日期
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("日期", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = {
                    val p = date.split("-").map { it.toInt() }
                    DatePickerDialog(
                        ctx,
                        { _, y, m, d -> date = String.format("%04d-%02d-%02d", y, m + 1, d) },
                        p[0], p[1] - 1, p[2]
                    ).show()
                }) { Text(date, color = TextMain, fontSize = 13.sp) }
            }

            Spacer(Modifier.height(8.dp))
            // 备注
            Text("备注（选填）", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            TextField(
                value = note, onValueChange = { note = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("例如：午饭") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                    focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(8.dp))
            // 标签
            Text("标签（选填）", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            TextField(
                value = tags, onValueChange = { tags = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("例如：旅行 必备") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                    focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // 保存按钮：始终常驻底部，不随内容滚动
        Button(
            onClick = {
                val expr = amount.trimEnd { it in "+-*/" }
                val amt = Util.evalArithmetic(expr) ?: expr.toDoubleOrNull()
                if (amt == null || amt == 0.0) { toast(ctx, "请输入非零金额"); return@Button }
                val finalCat = if (type == "transfer") {
                    if (transferFrom.isBlank() || transferTo.isBlank()) { toast(ctx, "请选择转出和转入账户"); return@Button }
                    if (transferFrom == transferTo) { toast(ctx, "转出和转入账户不能相同"); return@Button }
                    "$transferFrom→$transferTo"
                } else {
                    val id = selectedCatId
                    if (id == null) { toast(ctx, "请选择分类"); return@Button }
                    LedgerState.catPath(id)
                }
                val t = Tx(
                    id = editing?.id ?: java.util.UUID.randomUUID().toString(),
                    type = type, cat = finalCat, amount = amt,
                    date = date, note = note.trim(),
                    tags = Util.parseTags(tags)
                )
                if (editing != null) LedgerState.update(t) else LedgerState.add(t)
                toast(ctx, "已保存 ✓")
                amount = "0"; note = ""; tags = ""; transferFrom = ""; transferTo = ""
                selectedCatId = null; openParentId = null
                onDone()
            },
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (editing != null) "保存修改 ✓" else "保存这笔", fontSize = 17.sp, color = Color.White)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrapChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { o ->
            val sel = o == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (sel) Brand else Color(0xFFEDEFF5))
                    .clickable { onSelect(o) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(o, color = if (sel) Color.White else TextMain, fontSize = 14.sp)
            }
        }
    }
}

/** 嵌入式计算器：直接操作金额字符串，可边算边记；更紧凑以适配单屏 */
@Composable
fun Calculator(amount: String, onChange: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SoftOrange)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            val keys = listOf(
                listOf("7", "8", "9", "/"),
                listOf("4", "5", "6", "*"),
                listOf("1", "2", "3", "-"),
                listOf("C", "0", ".", "+")
            )
            keys.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        val isOp = key in "+-*/"
                        val isC = key == "C"
                        Button(
                            onClick = {
                                when (key) {
                                    "C" -> onChange("0")
                                    "/" -> calcOp(amount, onChange, "/")
                                    "*" -> calcOp(amount, onChange, "*")
                                    "-" -> calcOp(amount, onChange, "-")
                                    "+" -> calcOp(amount, onChange, "+")
                                    "." -> calcDot(amount, onChange)
                                    else -> calcDigit(amount, onChange, key)
                                }
                            },
                            Modifier.weight(1f).height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    isC -> Color(0xFFFFE0E0)
                                    isOp -> Brand
                                    else -> CardWhite
                                },
                                contentColor = when {
                                    isC -> ExpenseRed
                                    isOp -> Color.White
                                    else -> TextMain
                                }
                            ),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text(if (key == "*") "×" else if (key == "/") "÷" else key, fontSize = 17.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { calcBack(amount, onChange) },
                    Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEDEFF5), contentColor = TextMain),
                    shape = RoundedCornerShape(9.dp)
                ) { Text("⌫", fontSize = 17.sp) }
                Button(
                    onClick = { calcEqual(amount, onChange) },
                    Modifier.weight(2f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandDark),
                    shape = RoundedCornerShape(9.dp)
                ) { Text("= 计算", fontSize = 15.sp, color = Color.White) }
            }
        }
    }
}

private fun calcDigit(s: String, set: (String) -> Unit, d: String) {
    if (s == "0") set(d) else set(s + d)
}
private fun calcDot(s: String, set: (String) -> Unit) {
    val seg = s.takeLastWhile { it.isDigit() || it == '.' }
    if (!seg.contains('.')) set(s + ".")
}
private fun calcOp(s: String, set: (String) -> Unit, o: String) {
    if (s.isEmpty()) { if (o == "-") set("-"); return }
    if (s.last() in "+-*/") set(s.dropLast(1) + o) else set(s + o)
}
private fun calcBack(s: String, set: (String) -> Unit) {
    val v = s.dropLast(1)
    set(if (v.isEmpty()) "0" else v)
}
private fun calcEqual(s: String, set: (String) -> Unit) {
    val r = Util.evalArithmetic(s)
    if (r != null) set(Util.moneyNoSymbol(r)) else set(s)
}

/** 分类选择行（横向滚动，支持多级） */
@Composable
fun CatChipRow(cats: List<Category>, selectedId: String?, onSelect: (Category) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        cats.forEach { c ->
            val sel = c.id == selectedId
            val hasKids = LedgerState.childrenOf(c.id).isNotEmpty()
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (sel) Brand else Color(0xFFEDEFF5))
                    .clickable { onSelect(c) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(c.name + if (hasKids) " ▾" else "", color = if (sel) Color.White else TextMain, fontSize = 14.sp)
            }
        }
    }
}
