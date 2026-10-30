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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** 一条记账记录 */
data class Tx(
    val id: String,
    val type: String,        // "expense" | "income" | "transfer" | "invest"
    val cat: String,         // 分类路径，如 "餐饮" 或 "餐饮/早餐"；转账为 "招行→工行"
    val amount: Double,      // 可以为负数（冲销多记）
    val date: String,        // YYYY-MM-DD
    val note: String,
    val tags: List<String>
)

/** 自定义分类（支持多级：parentId 为空表示一级分类） */
data class Category(
    val id: String,
    val name: String,
    val type: String,        // "expense" | "income" | "invest"
    val parentId: String?    // 父分类 id，为空即一级
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

    /** 自定义分类（支持多级，最多四级） */
    val categories: SnapshotStateList<Category> = mutableStateListOf()

    /** 分类预算：分类路径 -> 每月预算金额 */
    val categoryBudgets: SnapshotStateMap<String, Double> = mutableStateMapOf()

    fun init(context: Context) {
        ctx = context.applicationContext
        load()
    }

    fun load() {
        transactions.clear()
        categories.clear()
        val f = file
        if (f == null || !f.exists()) { seedCategories(); return }
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
            // 加载分类
            val catsArr = root.optJSONArray("categories")
            if (catsArr != null && catsArr.length() > 0) {
                for (i in 0 until catsArr.length()) {
                    val o = catsArr.getJSONObject(i)
                    val pidRaw = o.optString("parentId", "")
                    categories.add(
                        Category(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            name = o.optString("name", "其他"),
                            type = o.optString("type", "expense"),
                            parentId = if (pidRaw.isBlank()) null else pidRaw
                        )
                    )
                }
            } else {
                seedCategories()
            }
            // 加载分类预算
            val cb = root.optJSONObject("catBudgets")
            categoryBudgets.clear()
            if (cb != null) {
                val kit = cb.keys()
                while (kit.hasNext()) {
                    val k = kit.next() as String
                    categoryBudgets[k] = cb.getDouble(k)
                }
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
            val catsArray = JSONArray()
            categories.forEach { c ->
                catsArray.put(
                    JSONObject().apply {
                        put("id", c.id); put("name", c.name); put("type", c.type)
                        if (c.parentId != null) put("parentId", c.parentId)
                    }
                )
            }
            val cbObj = JSONObject()
            categoryBudgets.forEach { (k, v) -> cbObj.put(k, v) }
            val root = JSONObject().put("budget", budget).put("transactions", arr).put("categories", catsArray).put("catBudgets", cbObj)
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

    fun setBudgetValue(b: Double) { budget = b; save() }

    fun setCatBudget(catPath: String, amount: Double) {
        if (amount <= 0.0) categoryBudgets.remove(catPath) else categoryBudgets[catPath] = amount
        save()
    }

    // ---------- 分类管理 ----------
    fun seedCategories() {
        categories.clear()
        val seeds = listOf(
            "expense" to listOf("餐饮", "交通", "购物", "居住", "娱乐", "医疗", "教育", "人情", "其他"),
            "income" to listOf("工资", "奖金", "理财", "兼职", "红包", "其他"),
            "invest" to listOf("理财", "基金", "股票", "黄金", "数字货币", "其他")
        )
        seeds.forEach { (type, names) ->
            names.forEach { name ->
                categories.add(Category(UUID.randomUUID().toString(), name, type, null))
            }
        }
    }

    fun catsOfType(type: String): List<Category> = categories.filter { it.type == type }
    fun topCats(type: String): List<Category> = catsOfType(type).filter { it.parentId == null }
    fun childrenOf(parentId: String): List<Category> = categories.filter { it.parentId == parentId }
    /** 分类完整路径：最多展示四级（如 餐饮/早餐/主食/面） */
    fun catPath(id: String): String {
        val c = categories.find { it.id == id } ?: return ""
        val names = mutableListOf<String>()
        var cur: Category? = c
        var guard = 0
        while (cur != null && guard < 4) {
            names.add(0, cur.name)
            cur = cur.parentId?.let { pid -> categories.find { it.id == pid } }
            guard++
        }
        return names.joinToString("/")
    }

    /** 分类当前层级（一级=1） */
    fun categoryDepth(id: String): Int {
        var cur = categories.find { it.id == id }
        var d = 0; var guard = 0
        while (cur != null && guard < 12) { d++; cur = cur.parentId?.let { pid -> categories.find { it.id == pid } }; guard++ }
        return d
    }

    /** 浏览路径（面包屑用）：从顶层到 id 的链条 */
    fun breadcrumbPath(id: String?): List<Category> {
        if (id == null) return emptyList()
        val chain = mutableListOf<Category>()
        var cur = categories.find { it.id == id }
        var guard = 0
        while (cur != null && guard < 12) { chain.add(0, cur); cur = cur.parentId?.let { pid -> categories.find { it.id == pid } }; guard++ }
        return chain
    }
    fun findCatByPath(type: String, path: String): Category? =
        catsOfType(type).firstOrNull { catPath(it.id) == path }

    fun addCategory(name: String, type: String, parentId: String?) {
        val n = name.trim()
        if (n.isBlank()) return
        if (parentId != null && categoryDepth(parentId) >= 4) return // 最多四级分类
        categories.add(Category(UUID.randomUUID().toString(), n, type, parentId))
        save()
    }
    fun renameCategory(id: String, newName: String) {
        val i = categories.indexOfFirst { it.id == id }
        if (i >= 0) categories[i] = categories[i].copy(name = newName.trim())
        save()
    }
    fun deleteCategory(id: String) {
        val toRemove = mutableSetOf(id)
        var changed = true
        while (changed) {
            changed = false
            categories.forEach {
                if (it.parentId in toRemove && it.id !in toRemove) { toRemove.add(it.id); changed = true }
            }
        }
        categories.removeAll { it.id in toRemove }
        save()
    }

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
    /** 转账涉及的账户（转出/转入） */
    val TRANSFER_ACCOUNTS = listOf("现金", "微信", "支付宝", "储蓄卡", "招行", "工行", "建行", "农行", "中行", "其他")
    /** 投资品种 */
    val INVEST_CATS = listOf("理财", "基金", "股票", "黄金", "数字货币", "其他")
    /** 类型中文标签 */
    val TYPE_LABELS = mapOf("expense" to "支出", "income" to "收入", "transfer" to "转账", "invest" to "投资")
    val CHART_COLORS = listOf(
        0xFFFF7A00.toInt(), 0xFF22C55E.toInt(), 0xFFF59E0B.toInt(), 0xFFEF4444.toInt(),
        0xFF06B6D4.toInt(), 0xFFA855F7.toInt(), 0xFFEC4899.toInt(), 0xFF14B8A6.toInt(),
        0xFF84CC16.toInt(), 0xFFF97316.toInt(), 0xFF3B82F6.toInt(), 0xFFEAB308.toInt()
    )

    /** 金额统一显示两位小数 */
    fun money(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        return "¥${String.format("%.2f", r)}"
    }

    /** 不带符号、统一两位小数 */
    fun moneyNoSymbol(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        return String.format("%.2f", r)
    }

    /** 带正负号的金额显示：支出负数显示成绿色“+¥x”（冲销/退回）；转账/投资为中性，不加正负号 */
    fun fmtAmt(t: Tx): String {
        val m = money(Math.abs(t.amount))
        return when (t.type) {
            "income" -> "+$m"
            "expense" -> if (t.amount < 0) "+$m" else "-$m"
            else -> m
        }
    }

    /** 类型中文标签 */
    fun typeLabel(t: String): String = TYPE_LABELS[t] ?: "支出"

    /** 简单四则运算求值（支持 + - * / 且乘除优先于加减），失败返回 null */
    fun evalArithmetic(expr: String): Double? {
        if (expr.isBlank()) return null
        val tokens = mutableListOf<Any>()
        var num = StringBuilder()
        fun flush() {
            if (num.isNotEmpty()) {
                val d = num.toString().toDoubleOrNull() ?: return
                tokens.add(d); num = StringBuilder()
            }
        }
        for (c in expr) {
            when {
                c.isDigit() || c == '.' -> num.append(c)
                c == '+' || c == '-' || c == '*' || c == '/' -> { flush(); tokens.add(c.toString()) }
                else -> return null
            }
        }
        flush()
        if (tokens.isEmpty()) return null
        // 处理开头的负号 / 正号
        if (tokens[0] == "-") tokens.add(0, 0.0)
        else if (tokens[0] == "+") tokens.removeAt(0)
        // 必须为：数字 (运算符 数字)*
        if (tokens.size % 2 == 0) return null
        for (i in tokens.indices) {
            val isNum = tokens[i] is Double
            if ((i % 2 == 0) != isNum) return null
        }
        // 先算乘除法
        val t2 = mutableListOf<Any>()
        var k = 0
        while (k < tokens.size) {
            val tk = tokens[k]
            if (tk is String && (tk == "*" || tk == "/")) {
                val a = t2.removeLast() as Double
                val b = tokens[k + 1] as Double
                t2.add(if (tk == "*") a * b else if (b != 0.0) a / b else return null)
                k += 2
            } else { t2.add(tk); k++ }
        }
        // 再算加减
        var res = t2[0] as Double
        k = 1
        while (k < t2.size) {
            val op = t2[k] as String
            val b = t2[k + 1] as Double
            res = if (op == "+") res + b else res - b
            k += 2
        }
        return res
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

    fun monthsBetween(start: String, end: String): List<String> {
        val sy = start.substring(0, 4).toIntOrNull() ?: return emptyList()
        val sm = start.substring(5, 7).toIntOrNull() ?: return emptyList()
        val ey = end.substring(0, 4).toIntOrNull() ?: return emptyList()
        val em = end.substring(5, 7).toIntOrNull() ?: return emptyList()
        val out = mutableListOf<String>()
        var y = sy; var m = sm
        while (y < ey || (y == ey && m <= em)) {
            out.add(String.format("%04d-%02d", y, m))
            m++
            if (m > 12) { m = 1; y++ }
            if (out.size > 120) break
        }
        return out
    }

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
            val ok = date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}\$")) && type in setOf("支出", "收入", "转账", "投资")
                    && cat.isNotBlank() && amt != null && amt != 0.0
            if (!ok) { bad++; continue }
            valid.add(
                Tx(
                    id = UUID.randomUUID().toString(),
                    type = when (type) {
                        "收入" -> "income"
                        "转账" -> "transfer"
                        "投资" -> "invest"
                        else -> "expense"
                    },
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
                "2025-01-10,支出,餐饮,-40,冲销示例（负数用来抵消多记的账）,\n" +
                "2025-01-15,转账,招行→工行,5000,跨行转账示例（类型可填 支出/收入/转账/投资）,\n" +
                "2025-01-20,投资,股票,10000,买入股票示例,\n"
    }

    fun exportCsv(): String {
        val sb = StringBuilder("日期,类型,分类,金额,备注,标签\n")
        LedgerState.transactions.sortedBy { it.date }.forEach { t ->
            val type = typeLabel(t.type)
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
