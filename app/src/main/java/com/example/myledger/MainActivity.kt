package com.example.myledger

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** 一条记账记录 */
data class Tx(
    val id: String,
    val type: String,        // "expense" | "income"
    val cat: String,
    val amount: Double,      // 可以为负数（冲销多记）
    val date: String,        // YYYY-MM-DD
    val note: String,
    val tags: List<String>
)

/** 全局账目状态：数据就存在手机真实文件夹里 */
object LedgerState {
    private var ctx: Context? = null

    /** 真实文件夹：Android/data/com.example.myledger/files/ledger_data.json */
    private val file: File?
        get() = ctx?.getExternalFilesDir(null)?.let { File(it, "ledger_data.json") }

    var budget by mutableStateOf(0.0)
        private set

    val transactions: SnapshotStateList<Tx> = mutableStateListOf()

    fun init(context: Context) {
        ctx = context.applicationContext
        load()
    }

    fun load() {
        transactions.clear()
        val f = file ?: return
        if (!f.exists()) return
        try {
            val root = JSONObject(f.readText())
            budget = root.optDouble("budget", 0.0)
            val arr = root.optJSONArray("transactions") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                transactions.add(
                    Tx(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        type = o.optString("type", "expense"),
                        cat = o.optString("cat", "其他"),
                        amount = o.optDouble("amount", 0.0),
                        date = o.optString("date", ""),
                        note = o.optString("note", ""),
                        tags = run {
                            val t = o.optJSONArray("tags")
                            if (t == null) emptyList()
                            else (0 until t.length()).map { t.getString(it) }
                        }
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun save() {
        val f = file ?: return
        try {
            f.parentFile?.mkdirs()
            val arr = JSONArray()
            transactions.forEach { t ->
                arr.put(
                    JSONObject().apply {
                        put("id", t.id); put("type", t.type); put("cat", t.cat)
                        put("amount", t.amount); put("date", t.date); put("note", t.note)
                        put("tags", JSONArray(t.tags))
                    }
                )
            }
            val root = JSONObject().put("budget", budget).put("transactions", arr)
            f.writeText(root.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun add(tx: Tx) { transactions.add(tx); save() }

    fun update(tx: Tx) {
        val i = transactions.indexOfFirst { it.id == tx.id }
        if (i >= 0) { transactions[i] = tx; save() }
    }

    fun remove(id: String) {
        transactions.removeAll { it.id == id }
        save()
    }

    fun setBudget(b: Double) { budget = b; save() }

    fun replaceAll(list: List<Tx>, newBudget: Double? = null) {
        transactions.clear()
        transactions.addAll(list)
        newBudget?.let { budget = it }
        save()
    }
}

/** 文件工具：把数据写到手机“下载/Ledger”文件夹，或读取用户选的文件 */
object FileUtil {
    /** 写文本到 下载/Ledger 文件夹（真实、可见），返回是否成功 */
    fun writeToDownloads(ctx: Context, fileName: String, mime: String, text: String): Boolean {
        return try {
            val resolver = ctx.contentResolver
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ledger")
                }
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val folder = File(dir, "Ledger").apply { mkdirs() }
                val f = File(folder, fileName)
                Uri.fromFile(f)
            }
            resolver.openOutputStream(uri!!)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 读取用户用系统文件选择器挑中的文件内容 */
    fun readUri(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/** 小工具：金额格式化、日期、分类、颜色 */
object Util {
    val EXPENSE_CATS = listOf("餐饮", "交通", "购物", "居住", "娱乐", "医疗", "教育", "人情", "其他")
    val INCOME_CATS = listOf("工资", "奖金", "理财", "兼职", "红包", "其他")
    val CHART_COLORS = listOf(
        0xFF6D5BFF.toInt(), 0xFF22C55E.toInt(), 0xFFF59E0B.toInt(), 0xFFEF4444.toInt(),
        0xFF06B6D4.toInt(), 0xFFA855F7.toInt(), 0xFFEC4899.toInt(), 0xFF14B8A6.toInt(),
        0xFF84CC16.toInt(), 0xFFF97316.toInt(), 0xFF3B82F6.toInt(), 0xFFEAB308.toInt()
    )

    fun money(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        return if (r == Math.floor(r) && r.isFinite()) "¥${r.toLong()}" else "¥${String.format("%.2f", r)}"
    }

    fun moneyNoSymbol(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        return if (r == Math.floor(r) && r.isFinite()) "${r.toLong()}" else String.format("%.2f", r)
    }

    /** 带正负号的金额显示：支出负数显示成绿色“+¥x”（冲销/退回） */
    fun fmtAmt(t: Tx): String {
        val m = money(Math.abs(t.amount))
        return if (t.type == "expense") {
            if (t.amount < 0) "+$m" else "-$m"
        } else {
            "+$m"
        }
    }

    fun today(): String {
        val c = java.util.Calendar.getInstance()
        return String.format(
            "%04d-%02d-%02d",
            c.get(java.util.Calendar.YEAR),
            c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    fun ym(d: java.util.Calendar): String =
        String.format("%04d-%02d", d.get(java.util.Calendar.YEAR), d.get(java.util.Calendar.MONTH) + 1)

    fun monthOf(date: String): String = if (date.length >= 7) date.substring(0, 7) else ""

    fun monthTx(month: String): List<Tx> = LedgerState.transactions.filter { monthOf(it.date) == month }

    /** 解析 CSV 文本，返回 (有效记录列表, 新增数, 无效数) */
    fun parseCsv(text: String): Triple<List<Tx>, Int, Int> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var field = StringBuilder()
        var inQ = false
        val t = text.replace("\r\n", "\n").replace('\r', '\n')
        for (i in t.indices) {
            val ch = t[i]
            if (inQ) {
                if (ch == '"') {
                    if (i + 1 < t.length && t[i + 1] == '"') { field.append('"'); i.inc() }
                    else inQ = false
                } else field.append(ch)
            } else {
                when (ch) {
                    '"' -> inQ = true
                    ',' -> { row.add(field.toString()); field = StringBuilder() }
                    '\n' -> { row.add(field.toString()); rows.add(row.toList()); row = mutableListOf(); field = StringBuilder() }
                    else -> field.append(ch)
                }
            }
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row.toList()) }
        if (rows.isEmpty()) return Triple(emptyList(), 0, 0)
        val header = rows.first().map { it.trim() }
        val idxDate = header.indexOfFirst { it == "日期" }
        val idxType = header.indexOfFirst { it == "类型" }
        val idxCat = header.indexOfFirst { it == "分类" }
        val idxAmt = header.indexOfFirst { it == "金额" }
        val idxNote = header.indexOfFirst { it == "备注" }
        val idxTags = header.indexOfFirst { it == "标签" }
        val valid = mutableListOf<Tx>()
        var bad = 0
        for (r in rows.drop(1)) {
            if (r.all { it.isBlank() }) continue
            val date = if (idxDate >= 0) r.getOrNull(idxDate)?.trim() ?: "" else ""
            val type = if (idxType >= 0) r.getOrNull(idxType)?.trim() ?: "" else ""
            val cat = if (idxCat >= 0) r.getOrNull(idxCat)?.trim() ?: "" else ""
            val amtRaw = if (idxAmt >= 0) r.getOrNull(idxAmt)?.trim() ?: "" else ""
            val note = if (idxNote >= 0) r.getOrNull(idxNote)?.trim() ?: "" else ""
            val tags = if (idxTags >= 0) parseTags(r.getOrNull(idxTags) ?: "") else emptyList()
            val amt = amtRaw.replace("[¥,]".toRegex(), "").toDoubleOrNull()
            val ok = date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}\$")) && (type == "支出" || type == "收入")
                    && cat.isNotBlank() && amt != null && amt != 0.0
            if (!ok) { bad++; continue }
            valid.add(
                Tx(
                    id = UUID.randomUUID().toString(),
                    type = if (type == "收入") "income" else "expense",
                    cat = cat,
                    amount = amt!!,
                    date = date,
                    note = note,
                    tags = tags
                )
            )
        }
        return Triple(valid, valid.size, bad)
    }

    fun parseTags(s: String): List<String> =
        s.split(Regex("[\\s,，、]+")).map { it.trim() }.filter { it.isNotBlank() }.take(8)

    fun csvTemplate(): String {
        return "日期,类型,分类,金额,备注,标签\n" +
                "2025-01-01,支出,餐饮,35.5,早餐示例（删掉这行，换成你自己的）,\n" +
                "2025-01-05,收入,工资,8000,一月工资示例,\n" +
                "2025-01-10,支出,餐饮,-40,冲销示例（负数用来抵消多记的账）,\n"
    }

    fun exportCsv(): String {
        val sb = StringBuilder("日期,类型,分类,金额,备注,标签\n")
        LedgerState.transactions.sortedBy { it.date }.forEach { t ->
            val type = if (t.type == "income") "收入" else "支出"
            val note = t.note.replace("\"", "\"\"")
            val tags = t.tags.joinToString(" ")
            sb.append("${t.date},${type},${t.cat},${t.amount},\"${note}\",${tags}\n")
        }
        return sb.toString()
    }
}

// ---------- 应用入口 ----------
class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        LedgerState.init(this)
        setContent {
            LedgerApp()
        }
    }
}
