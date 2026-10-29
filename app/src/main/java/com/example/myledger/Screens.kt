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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ---------- 通用样式 ----------
val Brand = Color(0xFF6D5BFF)
val LightBg = Color(0xFFF4F5FB)
val CardWhite = Color(0xFFFFFFFF)
val TextMain = Color(0xFF222222)
val TextMuted = Color(0xFF8A8F99)
val ExpenseRed = Color(0xFFEF4444)
val IncomeGreen = Color(0xFF22C55E)

fun toast(ctx: android.content.Context, s: String) =
    Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

// ---------- 主框架 ----------
@OptIn(ExperimentalMaterial3Api::class)
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
    var cat by remember { mutableStateOf(Util.EXPENSE_CATS[0]) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Util.today()) }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    LaunchedEffect(editing?.id) {
        if (editing != null) {
            type = editing.type
            cat = editing.cat
            amount = Util.moneyNoSymbol(editing.amount)
            date = editing.date
            note = editing.note
            tags = editing.tags.joinToString(" ")
        }
    }

    val cats = if (type == "expense") Util.EXPENSE_CATS else Util.INCOME_CATS

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (editing != null) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF0FF))) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("正在修改一笔记录", color = Brand, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDone) { Text("取消编辑", color = TextMuted) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // 收/支切换
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("expense" to "支出", "income" to "收入").forEach { (t, label) ->
                val sel = type == t
                Button(
                    onClick = {
                        type = t
                        cat = (if (t == "expense") Util.EXPENSE_CATS else Util.INCOME_CATS)[0]
                    },
                    Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sel) (if (t == "expense") ExpenseRed else IncomeGreen) else Color(0xFFE5E7EB),
                        contentColor = if (sel) Color.White else TextMuted
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(label, fontSize = 16.sp) }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 金额
        Text("金额", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = amount,
            onValueChange = { amount = it },
            Modifier.fillMaxWidth(),
            placeholder = { Text("0.00，可填负数冲销") },
            prefix = { Text("¥", color = TextMuted) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Text("提示：金额可以填负数，用来冲销之前多记的账。", color = TextMuted, fontSize = 11.sp)

        Spacer(Modifier.height(16.dp))
        // 分类
        Text("分类", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        WrapChips(cats, cat) { cat = it }

        Spacer(Modifier.height(16.dp))
        // 日期
        Text("日期", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            val p = date.split("-").map { it.toInt() }
            DatePickerDialog(
                ctx,
                { _, y, m, d -> date = String.format("%04d-%02d-%02d", y, m + 1, d) },
                p[0], p[1] - 1, p[2]
            ).show()
        }) { Text(date, color = TextMain) }

        Spacer(Modifier.height(16.dp))
        // 备注
        Text("备注", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = note, onValueChange = { note = it }, Modifier.fillMaxWidth(),
            placeholder = { Text("选填，例如：午饭") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))
        // 标签
        Text("标签（空格或逗号隔开，可参与搜索）", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = tags, onValueChange = { tags = it }, Modifier.fillMaxWidth(),
            placeholder = { Text("选填，例如：旅行 必备") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val amt = amount.toDoubleOrNull()
                if (amt == null || amt == 0.0) { toast(ctx, "请输入非零金额"); return@Button }
                val t = Tx(
                    id = editing?.id ?: java.util.UUID.randomUUID().toString(),
                    type = type, cat = cat, amount = amt,
                    date = date, note = note.trim(),
                    tags = Util.parseTags(tags)
                )
                if (editing != null) LedgerState.update(t) else LedgerState.add(t)
                toast(ctx, "已保存 ✓")
                amount = ""; note = ""; tags = ""
                onDone()
            },
            Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (editing != null) "保存修改 ✓" else "保存这笔", fontSize = 17.sp, color = Color.White)
        }
    }
}

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
