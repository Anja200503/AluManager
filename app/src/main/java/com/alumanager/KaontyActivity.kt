package com.alumanager

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.alumanager.databinding.ActivityKaontyBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Kaonty — caisse à deux soldes (Aluminium / Plateaux), filtrée par date.
 * Local-first : stockage local immédiat + synchronisation sur le serveur InfinityFree.
 */
class KaontyActivity : AppCompatActivity() {

    private lateinit var b: ActivityKaontyBinding
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dispFmt = SimpleDateFormat("EEEE dd/MM/yyyy", Locale.FRANCE)
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)

    private val CAT_ALU = "alu"; private val CAT_PLT = "plateaux"
    private val accentAlu = "#21E6FF"; private val accentPlt = "#FFC34D"

    private val GET_URL = "https://alu.xo.je/get_kaonty.php"
    private val SAVE_URL = "https://alu.xo.je/save_kaonty.php"
    private val DELETE_URL = "https://alu.xo.je/delete_kaonty.php"

    private var selectedDate = dayFmt.format(Date())
    private var settingFields = false
    private var inAlu = 0.0; private var outAlu = 0.0; private var inPlt = 0.0; private var outPlt = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKaontyBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.btnIn.setOnClickListener { showAdd("in") }
        b.btnOut.setOnClickListener { showAdd("out") }
        b.btnPrevDay.setOnClickListener { shiftDay(-1) }
        b.btnNextDay.setOnClickListener { shiftDay(1) }
        b.dateLabel.setOnClickListener { pickDate() }
        b.volaAlu.addTextChangedListener(simpleWatcher {
            if (!settingFields) { setVola(CAT_ALU, it.replace(',', '.').toDoubleOrNull() ?: 0.0); updateSoldes() }
        })
        b.volaPlateaux.addTextChangedListener(simpleWatcher {
            if (!settingFields) { setVola(CAT_PLT, it.replace(',', '.').toDoubleOrNull() ?: 0.0); updateSoldes() }
        })
        updateDateLabel()
        render()
        syncFromServer()
    }

    private fun simpleWatcher(onText: (String) -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) { onText(s?.toString() ?: "") }
    }

    private fun loadVola(): JSONObject = try { JSONObject(prefs().getString("vola", "{}")) } catch (e: Exception) { JSONObject() }
    private fun getVola(cat: String): Double = loadVola().optDouble("$cat|$selectedDate", 0.0)
    private fun setVola(cat: String, amt: Double) {
        val v = loadVola(); v.put("$cat|$selectedDate", amt)
        prefs().edit().putString("vola", v.toString()).apply()
    }
    private fun trimNum(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else v.toString().replace('.', ',')

    private fun updateSoldes() {
        fun apply(soldeTv: TextView, detTv: TextView, cat: String, inV: Double, outV: Double, accent: String) {
            val v = getVola(cat); val bal = v + inV - outV
            soldeTv.text = "${fmt(bal)} Ar"
            soldeTv.setTextColor(Color.parseColor(if (bal >= 0) accent else "#FF4D9D"))
            detTv.text = "Omaly ${fmt(v)} · +${fmt(inV)} / -${fmt(outV)}"
        }
        apply(b.soldeAlu, b.detailAlu, CAT_ALU, inAlu, outAlu, accentAlu)
        apply(b.soldePlateaux, b.detailPlateaux, CAT_PLT, inPlt, outPlt, accentPlt)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(v: Double): String = "%,d".format(Math.round(v)).replace(",", " ")
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun prefs() = getSharedPreferences("kaonty", MODE_PRIVATE)
    private fun load(): JSONArray = try { JSONArray(prefs().getString("ops", "[]")) } catch (e: Exception) { JSONArray() }
    private fun save(a: JSONArray) = prefs().edit().putString("ops", a.toString()).apply()
    private fun catOf(o: JSONObject) = o.optString("cat", CAT_ALU).ifBlank { CAT_ALU }
    private fun dateOf(o: JSONObject) = o.optString("date").ifBlank { dayFmt.format(Date(o.optLong("ts"))) }

    /* ── date ── */
    private fun updateDateLabel() {
        val today = dayFmt.format(Date())
        val d = dayFmt.parse(selectedDate) ?: Date()
        val txt = dispFmt.format(d).replaceFirstChar { it.uppercase() }
        b.dateLabel.text = "📅 " + (if (selectedDate == today) "Aujourd'hui — $txt" else txt)
    }
    private fun shiftDay(delta: Int) {
        val c = Calendar.getInstance(); c.time = dayFmt.parse(selectedDate) ?: Date()
        c.add(Calendar.DAY_OF_MONTH, delta)
        selectedDate = dayFmt.format(c.time)
        updateDateLabel(); render(); syncFromServer()
    }
    private fun pickDate() {
        val c = Calendar.getInstance(); c.time = dayFmt.parse(selectedDate) ?: Date()
        DatePickerDialog(this, { _, y, m, d ->
            c.set(y, m, d); selectedDate = dayFmt.format(c.time)
            updateDateLabel(); render(); syncFromServer()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    /* ── rendu (filtré par date) ── */
    private fun render() {
        val ops = load()
        val tin = HashMap<String, Double>(); val tout = HashMap<String, Double>()
        val byCat = HashMap<String, ArrayList<JSONObject>>()
        for (i in 0 until ops.length()) {
            val o = ops.optJSONObject(i) ?: continue
            if (dateOf(o) != selectedDate) continue
            val cat = catOf(o); val amt = o.optDouble("amount", 0.0)
            if (o.optString("type") == "in") tin[cat] = (tin[cat] ?: 0.0) + amt
            else tout[cat] = (tout[cat] ?: 0.0) + amt
            byCat.getOrPut(cat) { ArrayList() }.add(o)
        }
        inAlu = tin[CAT_ALU] ?: 0.0; outAlu = tout[CAT_ALU] ?: 0.0
        inPlt = tin[CAT_PLT] ?: 0.0; outPlt = tout[CAT_PLT] ?: 0.0
        settingFields = true
        val vA = getVola(CAT_ALU); val vP = getVola(CAT_PLT)
        b.volaAlu.setText(if (vA != 0.0) trimNum(vA) else "")
        b.volaPlateaux.setText(if (vP != 0.0) trimNum(vP) else "")
        settingFields = false
        updateSoldes()

        b.listContainer.removeAllViews()
        val total = (byCat[CAT_ALU]?.size ?: 0) + (byCat[CAT_PLT]?.size ?: 0)
        if (total == 0) { b.emptyState.text = "Aucune opération à cette date"; b.listContainer.addView(b.emptyState); return }
        renderSection("🔩 ALUMINIUM", accentAlu, byCat[CAT_ALU])
        renderSection("🟫 PLATEAUX", accentPlt, byCat[CAT_PLT])
    }

    private fun renderSection(title: String, accent: String, ops: List<JSONObject>?) {
        b.listContainer.addView(TextView(this).apply {
            text = title; setTextColor(Color.parseColor(accent)); textSize = 13f; setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(16); layoutParams = lp
        })
        if (ops.isNullOrEmpty()) {
            b.listContainer.addView(TextView(this).apply {
                text = "Aucune opération"; setTextColor(Color.parseColor("#5A688F")); textSize = 12f
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(6); layoutParams = lp
            }); return
        }
        ops.sortedByDescending { it.optLong("ts") }.forEach { b.listContainer.addView(card(it, accent)) }
    }

    private fun card(o: JSONObject, catAccent: String): View {
        val isIn = o.optString("type") == "in"
        val amtColor = if (isIn) "#27FFC4" else "#FF4D9D"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = strokedBg(Color.parseColor("#0E1730"), 14, Color.parseColor(catAccent))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10); layoutParams = lp
        }
        row.addView(TextView(this).apply { text = if (isIn) "⬇️" else "⬆️"; textSize = 20f; setPadding(0, 0, dp(12), 0) })
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = o.optString("label").ifBlank { if (isIn) "Versement" else "Sortie" }
            setTextColor(Color.parseColor("#EAF2FF")); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
        })
        val ts = o.optLong("ts"); val timeStr = if (ts > 0) timeFmt.format(Date(ts)) else ""
        info.addView(TextView(this).apply {
            text = timeStr; setTextColor(Color.parseColor("#8A97C2")); textSize = 11f
        })
        row.addView(info)
        row.addView(TextView(this).apply {
            text = (if (isIn) "+" else "−") + " ${fmt(o.optDouble("amount", 0.0))} Ar"
            setTextColor(Color.parseColor(amtColor)); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = "  ✕"; setTextColor(Color.parseColor("#8A97C2")); textSize = 16f; setPadding(dp(8), 0, 0, 0)
            setOnClickListener { confirmDelete(o) }
        })
        return row
    }

    /* ── ajout ── */
    private fun showAdd(type: String) {
        val pad = dp(16)
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8), pad, 0) }
        v.addView(TextView(this).apply { text = "Catégorie"; setTextColor(Color.parseColor("#8A97C2")); textSize = 12f })
        var cat = CAT_ALU
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6); layoutParams = lp
        }
        lateinit var aluBtn: TextView; lateinit var pltBtn: TextView
        fun refresh() {
            aluBtn.background = strokedBg(Color.parseColor(if (cat == CAT_ALU) "#13314A" else "#0B1326"), 12, Color.parseColor(if (cat == CAT_ALU) accentAlu else "#243456"))
            aluBtn.setTextColor(Color.parseColor(if (cat == CAT_ALU) accentAlu else "#8A97C2"))
            pltBtn.background = strokedBg(Color.parseColor(if (cat == CAT_PLT) "#3A2E12" else "#0B1326"), 12, Color.parseColor(if (cat == CAT_PLT) accentPlt else "#243456"))
            pltBtn.setTextColor(Color.parseColor(if (cat == CAT_PLT) accentPlt else "#8A97C2"))
        }
        aluBtn = TextView(this).apply {
            text = "🔩 Aluminium"; gravity = Gravity.CENTER; textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { cat = CAT_ALU; refresh() }
        }
        pltBtn = TextView(this).apply {
            text = "🟫 Plateaux"; gravity = Gravity.CENTER; textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
            setOnClickListener { cat = CAT_PLT; refresh() }
        }
        seg.addView(aluBtn); seg.addView(pltBtn); refresh(); v.addView(seg)

        val montant = field("Montant (Ar)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.,")
        }
        val libelle = field("Libellé (motif)")
        v.addView(montant); v.addView(libelle)

        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle((if (type == "in") "➕ Versement" else "➖ Sortie") + " — " + selectedDate)
            .setView(v)
            .setPositiveButton("Enregistrer") { _, _ ->
                val amt = montant.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                if (amt <= 0) { toast("Montant invalide"); return@setPositiveButton }
                val now = System.currentTimeMillis()
                val op = JSONObject().apply {
                    put("id", "k$now"); put("cat", cat); put("type", type); put("amount", amt)
                    put("label", libelle.text.toString().trim()); put("ts", now); put("date", selectedDate)
                }
                val arr = load(); arr.put(op); save(arr); render()
                pushToServer(op)
                toast(if (type == "in") "Versement enregistré" else "Sortie enregistrée")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmDelete(o: JSONObject) {
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("Supprimer").setMessage("Supprimer cette opération ?")
            .setPositiveButton("Supprimer") { _, _ ->
                val id = o.optString("id")
                val arr = load(); val keep = JSONArray()
                for (i in 0 until arr.length()) { val e = arr.optJSONObject(i) ?: continue; if (e.optString("id") != id) keep.put(e) }
                save(keep); render(); deleteOnServer(id); toast("Supprimé")
            }
            .setNegativeButton("Annuler", null).show()
    }

    /* ── serveur (best-effort) ── */
    private fun pushToServer(op: JSONObject) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("op_id", op.optString("id")); put("cat", op.optString("cat"))
                    put("type", op.optString("type")); put("amount", op.optDouble("amount"))
                    put("label", op.optString("label")); put("date", op.optString("date")); put("ts", op.optLong("ts"))
                }.toString()
                Net.post(SAVE_URL, body)
            } catch (e: Exception) { /* hors-ligne : reste en local */ }
        }.start()
    }

    private fun deleteOnServer(id: String) {
        Thread {
            try { Net.post(DELETE_URL, JSONObject().put("op_id", id).toString()) } catch (e: Exception) {}
        }.start()
    }

    /** Récupère les opérations du serveur pour la date et les fusionne dans le local. */
    private fun syncFromServer() {
        val date = selectedDate
        Thread {
            try {
                val body = Net.get("$GET_URL?date=$date&t=${System.currentTimeMillis()}")
                val jsonStr = Net.extractJson(body) ?: return@Thread
                val resp = JSONObject(jsonStr)
                if (!resp.optBoolean("success", false)) return@Thread
                val serverOps = resp.optJSONArray("ops") ?: return@Thread
                val local = load()
                val ids = HashSet<String>()
                for (i in 0 until local.length()) ids.add(local.optJSONObject(i).optString("id"))
                var added = false
                for (i in 0 until serverOps.length()) {
                    val s = serverOps.optJSONObject(i) ?: continue
                    val id = s.optString("op_id")
                    if (id.isBlank() || ids.contains(id)) continue
                    local.put(JSONObject().apply {
                        put("id", id); put("cat", s.optString("cat", CAT_ALU)); put("type", s.optString("type", "in"))
                        put("amount", s.optString("amount", "0").toDoubleOrNull() ?: 0.0)
                        put("label", s.optString("label")); put("date", s.optString("op_date", date))
                        put("ts", s.optString("ts", "0").toLongOrNull() ?: 0L)
                    })
                    added = true
                }
                if (added) { save(local); runOnUiThread { if (selectedDate == date) render() } }
            } catch (e: Exception) { /* serveur indispo : on garde le local */ }
        }.start()
    }

    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.parseColor("#EAF2FF")); setHintTextColor(Color.parseColor("#5A688F"))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = strokedBg(Color.parseColor("#0B1326"), 12, Color.parseColor("#243456"))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10); layoutParams = lp
    }

    private fun strokedBg(color: Int, radiusDp: Int, strokeColor: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(1).coerceAtLeast(1), strokeColor)
    }
}
