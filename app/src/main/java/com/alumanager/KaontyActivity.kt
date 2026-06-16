package com.alumanager

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
import java.util.Date
import java.util.Locale

/** Kaonty — gestion de caisse : versements (entrées) et sorties, avec solde. Stockage local. */
class KaontyActivity : AppCompatActivity() {

    private lateinit var b: ActivityKaontyBinding
    private val fullFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityKaontyBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.btnIn.setOnClickListener { showAdd("in") }
        b.btnOut.setOnClickListener { showAdd("out") }
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(v: Double): String = "%,d".format(Math.round(v)).replace(",", " ")
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun prefs() = getSharedPreferences("kaonty", MODE_PRIVATE)
    private fun load(): JSONArray = try { JSONArray(prefs().getString("ops", "[]")) } catch (e: Exception) { JSONArray() }
    private fun save(a: JSONArray) = prefs().edit().putString("ops", a.toString()).apply()

    private fun render() {
        val ops = load()
        var tin = 0.0; var tout = 0.0
        val list = ArrayList<JSONObject>()
        for (i in 0 until ops.length()) {
            val o = ops.optJSONObject(i) ?: continue
            val amt = o.optDouble("amount", 0.0)
            if (o.optString("type") == "in") tin += amt else tout += amt
            list.add(o)
        }
        b.totalIn.text = fmt(tin)
        b.totalOut.text = fmt(tout)
        val solde = tin - tout
        b.solde.text = "${fmt(solde)} Ar"
        b.solde.setTextColor(Color.parseColor(if (solde >= 0) "#21E6FF" else "#FF4D9D"))

        b.listContainer.removeAllViews()
        if (list.isEmpty()) { b.listContainer.addView(b.emptyState); return }
        list.sortedByDescending { it.optLong("ts") }.forEach { b.listContainer.addView(card(it)) }
    }

    private fun card(o: JSONObject): View {
        val isIn = o.optString("type") == "in"
        val accent = if (isIn) "#27FFC4" else "#FF4D9D"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = strokedBg(Color.parseColor("#0E1730"), 14, Color.parseColor(accent))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10); layoutParams = lp
        }
        row.addView(TextView(this).apply {
            text = if (isIn) "⬇️" else "⬆️"; textSize = 20f
            setPadding(0, 0, dp(12), 0)
        })
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = o.optString("label").ifBlank { if (isIn) "Versement" else "Sortie" }
            setTextColor(Color.parseColor("#EAF2FF")); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
        })
        info.addView(TextView(this).apply {
            text = fullFmt.format(Date(o.optLong("ts")))
            setTextColor(Color.parseColor("#8A97C2")); textSize = 11f
        })
        row.addView(info)
        row.addView(TextView(this).apply {
            text = (if (isIn) "+" else "−") + " ${fmt(o.optDouble("amount", 0.0))} Ar"
            setTextColor(Color.parseColor(accent)); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = "  ✕"; setTextColor(Color.parseColor("#8A97C2")); textSize = 16f
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { confirmDelete(o) }
        })
        return row
    }

    private fun showAdd(type: String) {
        val pad = dp(16)
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8), pad, 0) }
        val montant = field("Montant (Ar)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.,")
        }
        val libelle = field("Libellé (motif)")
        v.addView(montant); v.addView(libelle)
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle(if (type == "in") "➕ Nouveau versement" else "➖ Nouvelle sortie")
            .setView(v)
            .setPositiveButton("Enregistrer") { _, _ ->
                val amt = montant.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                if (amt <= 0) { toast("Montant invalide"); return@setPositiveButton }
                val arr = load()
                arr.put(JSONObject().apply {
                    put("id", "k" + System.currentTimeMillis())
                    put("type", type); put("amount", amt)
                    put("label", libelle.text.toString().trim())
                    put("ts", System.currentTimeMillis())
                })
                save(arr); render()
                toast(if (type == "in") "Versement enregistré" else "Sortie enregistrée")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmDelete(o: JSONObject) {
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("Supprimer")
            .setMessage("Supprimer cette opération ?")
            .setPositiveButton("Supprimer") { _, _ ->
                val id = o.optString("id")
                val arr = load(); val keep = JSONArray()
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    if (e.optString("id") != id) keep.put(e)
                }
                save(keep); render(); toast("Supprimé")
            }
            .setNegativeButton("Annuler", null)
            .show()
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
