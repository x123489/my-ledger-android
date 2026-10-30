package com.example.myledger

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun MineScreen() {
    var sub by remember { mutableStateOf<String?>(null) }
    when (sub) {
        "cat" -> CategoryManageScreen(onBack = { sub = null })
        "budget" -> BudgetManageScreen(onBack = { sub = null })
        else -> MineMenu(onGo = { sub = it })
    }
}

@Composable
fun MineMenu(onGo: (String) -> Unit) {
    val ctx = LocalContext.current
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreDlg by remember { mutableStateOf(false) }
    var showClearDlg by remember { mutableStateOf(false) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingRestoreUri = uri; showRestoreDlg = true }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = FileUtil.readUri(ctx, uri)
        if (text != null) {
            val (valid, _, bad) = Util.parseCsv(text)
            val exist = LedgerState.transactions.map { sigOf(it) }.toMutableSet()
            val merged = LedgerState.transactions.toMutableList()
            var reallyAdded = 0
            valid.forEach {
                val s = sigOf(it)
                if (s !in exist) { exist.add(s); merged.add(it); reallyAdded++ }
            }
            LedgerState.replaceAll(merged)
            toast(ctx, "导入完成：新增 $reallyAdded，跳过重复/无效 $bad")
        } else toast(ctx, "读取文件失败")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("我的", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextMain)
        Spacer(Modifier.height(14.dp))

        EntryCard("🗂️ 分类管理", "自定义名称 / 增删改 / 支持最多四级多级分类") { onGo("cat") }
        Spacer(Modifier.height(12.dp))
        EntryCard("💰 预算管理", "设置每月预算与按分类预算") { onGo("budget") }

        Spacer(Modifier.height(18.dp))
        Text("数据（存在手机真实文件夹：Android/data/com.example.myledger/files/）", fontSize = 13.sp, color = TextMuted)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigBtn("💾 备份到下载文件夹（换手机用）") {
                    val json = buildBackupJson()
                    val name = "记账备份_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().time) + ".json"
                    val ok = FileUtil.writeToDownloads(ctx, name, "application/json", json)
                    toast(ctx, if (ok) "已备份到 下载/Ledger/$name" else "备份失败，请重试")
                }
                BigBtn("📂 从备份恢复（JSON）") { restoreLauncher.launch(arrayOf("*/*")) }
                BigBtn("📥 下载导入模板（CSV）") {
                    val ok = FileUtil.writeToDownloads(ctx, "记账导入模板.csv", "text/csv", Util.csvTemplate())
                    toast(ctx, if (ok) "模板已下载到 下载/Ledger/" else "下载失败")
                }
                BigBtn("📤 导入数据（CSV）") { importLauncher.launch(arrayOf("*/*")) }
                BigBtn("⬇️ 导出全部数据（CSV）") {
                    val ok = FileUtil.writeToDownloads(ctx, "记账全部数据.csv", "text/csv", Util.exportCsv())
                    toast(ctx, if (ok) "已导出到 下载/Ledger/" else "导出失败")
                }
                BigBtn("🗑️ 清空全部数据", danger = true) { showClearDlg = true }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("说明：本 App 把数据直接存在你手机的文件夹里（不是浏览器），换手机时用“备份/恢复”即可迁移。导出/备份的文件会自动放进手机“下载/Ledger”文件夹，方便你查看和搬运。", fontSize = 12.sp, color = TextMuted)
    }

    if (showRestoreDlg && pendingRestoreUri != null) {
        val uri = pendingRestoreUri!!
        AlertDialog(
            onDismissRequest = { showRestoreDlg = false; pendingRestoreUri = null },
            title = { Text("恢复方式") },
            text = { Text("覆盖：用备份完全替换当前数据。\n合并：备份与当前数据合并，重复记录自动跳过。") },
            confirmButton = {
                TextButton(onClick = {
                    doRestore(ctx, uri, overwrite = true)
                    showRestoreDlg = false; pendingRestoreUri = null
                }) { Text("覆盖", color = ExpenseRed) }
            },
            dismissButton = {
                TextButton(onClick = {
                    doRestore(ctx, uri, overwrite = false)
                    showRestoreDlg = false; pendingRestoreUri = null
                }) { Text("合并") }
            }
        )
    }

    if (showClearDlg) {
        AlertDialog(
            onDismissRequest = { showClearDlg = false },
            title = { Text("清空全部数据？") },
            text = { Text("这会删除所有记账记录，且无法恢复！建议先“备份到下载文件夹”。") },
            confirmButton = {
                TextButton(onClick = { LedgerState.replaceAll(emptyList(), 0.0); showClearDlg = false; toast(ctx, "已清空") }) { Text("删除", color = ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { showClearDlg = false }) { Text("取消") } }
        )
    }
}

@Composable
fun EntryCard(title: String, desc: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextMain)
                Spacer(Modifier.height(4.dp))
                Text(desc, fontSize = 12.sp, color = TextMuted)
            }
            Text("›", fontSize = 22.sp, color = TextMuted)
        }
    }
}

// ---------- 分类管理（递归，最多四级） ----------
@Composable
fun CategoryManageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var showCatDlg by remember { mutableStateOf(false) }
    var catDlgType by remember { mutableStateOf("expense") }
    var catDlgParentId by remember { mutableStateOf<String?>(null) }
    var catDlgEditId by remember { mutableStateOf<String?>(null) }
    var catDlgName by remember { mutableStateOf("") }
    var catDelId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("‹ 返回", Modifier.clickable { onBack() }, color = Brand, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("分类管理", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        listOf("expense" to "支出分类", "income" to "收入分类", "invest" to "投资分类").forEach { (type, label) ->
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMain)
            Spacer(Modifier.height(6.dp))
            LedgerState.topCats(type).forEach { top ->
                CatManageNode(type, top, 1,
                    onAddChild = { pid -> catDlgType = type; catDlgParentId = pid; catDlgEditId = null; catDlgName = ""; showCatDlg = true },
                    onRename = { c -> catDlgType = type; catDlgParentId = c.parentId; catDlgEditId = c.id; catDlgName = c.name; showCatDlg = true },
                    onDelete = { c -> catDelId = c.id })
            }
            BigBtn("＋ 添加${label}") { catDlgType = type; catDlgParentId = null; catDlgEditId = null; catDlgName = ""; showCatDlg = true }
            Spacer(Modifier.height(14.dp))
        }
    }

    if (showCatDlg) {
        AlertDialog(
            onDismissRequest = { showCatDlg = false },
            title = { Text(if (catDlgEditId != null) "修改分类名称" else "新建分类") },
            text = {
                Column {
                    Text(if (catDlgParentId != null) "将作为子分类添加（最多四级）" else "作为一级分类", fontSize = 13.sp, color = TextMuted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = catDlgName, onValueChange = { catDlgName = it }, Modifier.fillMaxWidth(),
                        placeholder = { Text("分类名称") }, singleLine = true,
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                            focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = catDlgName.trim()
                    if (n.isNotBlank()) {
                        if (catDlgEditId != null) LedgerState.renameCategory(catDlgEditId!!, n)
                        else LedgerState.addCategory(n, catDlgType, catDlgParentId)
                    }
                    showCatDlg = false
                }) { Text("确定", color = Brand) }
            },
            dismissButton = { TextButton(onClick = { showCatDlg = false }) { Text("取消") } }
        )
    }

    catDelId?.let { id ->
        val c = LedgerState.categories.find { it.id == id }
        val hasKids = c?.let { LedgerState.childrenOf(it.id).isNotEmpty() } ?: false
        AlertDialog(
            onDismissRequest = { catDelId = null },
            title = { Text("删除分类？") },
            text = { Text(if (hasKids) "该分类下还有子分类，将一并删除。已记账的历史记录会保留，仅该分类不再可选。" else "已记账的历史记录会保留，仅该分类不再可选。") },
            confirmButton = { TextButton(onClick = { LedgerState.deleteCategory(id); catDelId = null }) { Text("删除", color = ExpenseRed) } },
            dismissButton = { TextButton(onClick = { catDelId = null }) { Text("取消") } }
        )
    }
}

@Composable
fun CatManageNode(type: String, cat: Category, depth: Int, onAddChild: (String) -> Unit, onRename: (Category) -> Unit, onDelete: (Category) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = ((depth - 1) * 16).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text((if (depth > 1) "· ".repeat(depth - 1) else "") + cat.name, Modifier.weight(1f), fontSize = 13.sp, color = if (depth == 1) TextMain else TextMuted)
        if (depth < 4) TextButton(onClick = { onAddChild(cat.id) }) { Text("加子分类", color = Brand, fontSize = 12.sp) }
        TextButton(onClick = { onRename(cat) }) { Text("改名", color = TextMuted, fontSize = 12.sp) }
        TextButton(onClick = { onDelete(cat) }) { Text("删除", color = ExpenseRed, fontSize = 12.sp) }
    }
    LedgerState.childrenOf(cat.id).forEach { child ->
        CatManageNode(type, child, depth + 1, onAddChild, onRename, onDelete)
    }
}

// ---------- 预算管理（整体 + 按分类） ----------
@Composable
fun BudgetManageScreen(onBack: () -> Unit) {
        val ctx = LocalContext.current
    var budgetText by remember { mutableStateOf(if (LedgerState.budget > 0) Util.moneyNoSymbol(LedgerState.budget) else "") }
    val allPaths = remember { LedgerState.categories.map { LedgerState.catPath(it.id) }.distinct() }
    var catInputs by remember {
        mutableStateOf(allPaths.associateWith { (LedgerState.categoryBudgets[it]?.let { v -> Util.moneyNoSymbol(v) } ?: "") })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("‹ 返回", Modifier.clickable { onBack() }, color = Brand, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("预算管理", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))

        // 整体月预算
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("每月总预算", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = budgetText, onValueChange = { budgetText = it }, Modifier.weight(1f),
                        placeholder = { Text("例如 2000") }, prefix = { Text("¥", color = TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                            focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val b = budgetText.toDoubleOrNull()
                        if (b != null && b >= 0) { LedgerState.setBudgetValue(b); toast(ctx, "预算已设为 ${Util.money(b)}") }
                        else toast(ctx, "请输入合法数字")
                    }, colors = ButtonDefaults.buttonColors(containerColor = Brand)) { Text("保存", color = Color.White) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("分类预算（每月，按“分类管理”设定）", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (LedgerState.categories.isEmpty()) {
                    Text("暂无分类，去“分类管理”添加后再设置分类预算。", color = TextMuted, fontSize = 13.sp)
                } else {
                    listOf("expense" to "支出分类", "income" to "收入分类", "invest" to "投资分类").forEach { (type, label) ->
                        val tops = LedgerState.topCats(type)
                        if (tops.isNotEmpty()) {
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Brand)
                            Spacer(Modifier.height(4.dp))
                            tops.forEach { top ->
                                CatBudgetNode(type, top, 1, catInputs, { cp, v -> catInputs = catInputs + (cp to v) })
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                catInputs.forEach { (cp, v) -> val num = v.toDoubleOrNull(); LedgerState.setCatBudget(cp, num ?: 0.0) }
                toast(ctx, "分类预算已保存")
            },
            Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand),
            shape = RoundedCornerShape(12.dp)
        ) { Text("保存分类预算", fontSize = 16.sp, color = Color.White) }
    }
}

/** 分类预算节点：按分类管理结构递归展示（缩进体现多级），每个节点对应一条分类预算 */
@Composable
fun CatBudgetNode(type: String, cat: Category, depth: Int, catInputs: Map<String, String>, onInput: (String, String) -> Unit) {
    val cp = LedgerState.catPath(cat.id)
    Row(
        Modifier.fillMaxWidth().padding(start = ((depth - 1) * 16).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(cat.name, Modifier.weight(1f), fontSize = 13.sp, color = if (depth == 1) TextMain else TextMuted)
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = catInputs[cp] ?: "", onValueChange = { onInput(cp, it) },
            Modifier.width(110.dp), placeholder = { Text("不限") }, prefix = { Text("¥", color = TextMuted) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                focusedIndicatorColor = Brand, unfocusedIndicatorColor = Color(0xFFE5E7EB)
            ),
            shape = RoundedCornerShape(10.dp)
        )
    }
    LedgerState.childrenOf(cat.id).forEach { child ->
        CatBudgetNode(type, child, depth + 1, catInputs, onInput)
    }
}

@Composable
fun BigBtn(text: String, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = if (danger) ExpenseRed else Brand
        ),
        shape = RoundedCornerShape(10.dp)
    ) { Text(text, fontSize = 14.sp) }
}

fun sigOf(t: Tx): String = "${t.date}|${t.type}|${t.cat}|${t.amount}|${t.note}"

fun buildBackupJson(): String {
    val arr = JSONArray()
    LedgerState.transactions.forEach { t ->
        arr.put(JSONObject().apply {
            put("id", t.id); put("type", t.type); put("cat", t.cat)
            put("amount", t.amount); put("date", t.date); put("note", t.note); put("tags", JSONArray(t.tags))
        })
    }
    val cats = JSONArray()
    LedgerState.categories.forEach { c ->
        cats.put(JSONObject().apply {
            put("id", c.id); put("name", c.name); put("type", c.type)
            if (c.parentId != null) put("parentId", c.parentId)
        })
    }
    val cb = JSONObject()
    LedgerState.categoryBudgets.forEach { (k, v) -> cb.put(k, v) }
    return JSONObject().put("app", "my-ledger").put("version", 1).put("budget", LedgerState.budget)
        .put("transactions", arr).put("categories", cats).put("catBudgets", cb).toString(2)
}

fun doRestore(ctx: Context, uri: Uri, overwrite: Boolean) {
    val text = FileUtil.readUri(ctx, uri) ?: run { toast(ctx, "读取备份失败"); return }
    try {
        val root = JSONObject(text)
        val bud = root.optDouble("budget", LedgerState.budget)
        val arr = root.optJSONArray("transactions") ?: JSONArray()
        val list = mutableListOf<Tx>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Tx(
                    id = o.optString("id", java.util.UUID.randomUUID().toString()),
                    type = o.optString("type", "expense"),
                    cat = o.optString("cat", "其他"),
                    amount = o.optDouble("amount", 0.0),
                    date = o.optString("date", ""),
                    note = o.optString("note", ""),
                    tags = run { val t = o.optJSONArray("tags"); if (t == null) emptyList() else (0 until t.length()).map { t.getString(it) } }
                )
            )
        }
        val catsArr = root.optJSONArray("categories")
        if (catsArr != null) {
            val newCats = mutableListOf<Category>()
            for (i in 0 until catsArr.length()) {
                val o = catsArr.getJSONObject(i)
                val pidRaw = o.optString("parentId", "")
                newCats.add(
                    Category(
                        o.optString("id", java.util.UUID.randomUUID().toString()),
                        o.optString("name", "其他"),
                        o.optString("type", "expense"),
                        if (pidRaw.isBlank()) null else pidRaw
                    )
                )
            }
            LedgerState.categories.clear()
            LedgerState.categories.addAll(newCats)
        }
        val cbObj = root.optJSONObject("catBudgets")
        LedgerState.categoryBudgets.clear()
        if (cbObj != null) {
            val kit = cbObj.keys()
            while (kit.hasNext()) { val k = kit.next() as String; LedgerState.categoryBudgets[k] = cbObj.getDouble(k) }
        }
        if (overwrite) {
            LedgerState.replaceAll(list, bud)
            toast(ctx, "已覆盖恢复 ${list.size} 条")
        } else {
            val exist = LedgerState.transactions.map { sigOf(it) }.toMutableSet()
            val merged = LedgerState.transactions.toMutableList()
            var added = 0
            list.forEach {
                val s = sigOf(it)
                if (s !in exist) { exist.add(s); merged.add(it); added++ }
            }
            LedgerState.replaceAll(merged, bud)
            toast(ctx, "已合并：新增 $added，跳过重复 ${list.size - added}")
        }
        LedgerState.save()
    } catch (e: Exception) {
        e.printStackTrace()
        toast(ctx, "备份格式不正确")
    }
}
