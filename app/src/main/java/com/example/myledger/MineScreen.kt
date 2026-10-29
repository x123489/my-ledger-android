package com.example.myledger

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
    val ctx = LocalContext.current
    var budgetText by remember { mutableStateOf(if (LedgerState.budget > 0) Util.moneyNoSymbol(LedgerState.budget) else "") }
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
        // 预算
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("月度预算", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = budgetText, onValueChange = { budgetText = it }, Modifier.weight(1f),
                        placeholder = { Text("例如 2000") },
                        prefix = { Text("¥", color = TextMuted) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
        // 数据
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

    // 恢复对话框：覆盖 or 合并
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

    // 清空确认
    if (showClearDlg) {
        AlertDialog(
            onDismissRequest = { showClearDlg = false },
            title = { Text("清空全部数据？") },
            text = { Text("这会删除所有记账记录，且无法恢复！建议先“备份到下载文件夹”。") },
            confirmButton = {
                TextButton(onClick = { LedgerState.replaceAll(emptyList(), 0.0); budgetText = ""; showClearDlg = false; toast(ctx, "已清空") }) { Text("删除", color = ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { showClearDlg = false }) { Text("取消") } }
        )
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
    return JSONObject().put("app", "my-ledger").put("version", 1).put("budget", LedgerState.budget).put("transactions", arr).toString(2)
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
    } catch (e: Exception) {
        e.printStackTrace()
        toast(ctx, "备份格式不正确")
    }
}
