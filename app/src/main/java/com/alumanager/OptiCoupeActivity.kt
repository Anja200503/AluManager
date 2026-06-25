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
import androidx.appcompat.app.AppCompatActivity
import com.alumanager.databinding.ActivityOpticoupeBinding
import kotlin.math.round

/**
 * Optimisation de coupe 1D — outil manuel inspiré des apps de découpe d'acier.
 * Entrées : Lo (longueur barre), Le (chute max tolérée), trait de scie, et un tableau
 * de pièces (longueur × quantité). Sortie : patrons de coupe optimaux (Gilmore-Gomory).
 */
class OptiCoupeActivity : AppCompatActivity() {

    private lateinit var b: ActivityOpticoupeBinding
    private val rows = ArrayList<Pair<EditText, EditText>>()
    private val SCALE = 10  // 1 unité = 0.1 (gère 1 décimale)

    private val COLORS = intArrayOf(
        0xFF21E6FF.toInt(), 0xFF8B5CFF.toInt(), 0xFFFFC34D.toInt(), 0xFF27FFC4.toInt(),
        0xFFFF4D9D.toInt(), 0xFFe67e22.toInt(), 0xFF2ecc71.toInt(), 0xFF9b59b6.toInt(),
        0xFF3498db.toInt(), 0xFFe74c3c.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOpticoupeBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.btnAddRow.setOnClickListener { addRow() }
        b.btnCalc.setOnClickListener { calculate() }
        b.btnClear.setOnClickListener { clearAll() }
        repeat(3) { addRow() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun num(v: Double): String = if (v == round(v)) v.toInt().toString() else (round(v * 100) / 100).toString()

    private fun addRow(len: String = "", qty: String = "") {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6); layoutParams = lp
        }
        val lenEt = field(2f).apply { setText(len); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = "Longueur" }
        val qtyEt = field(1f).apply { setText(qty); inputType = InputType.TYPE_CLASS_NUMBER; hint = "Qté" }
        val del = TextView(this).apply {
            text = "✕"; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#FF4D9D")); textSize = 16f
            layoutParams = LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                b.rowsContainer.removeView(row)
                rows.removeAll { it.first === lenEt }
            }
        }
        row.addView(lenEt); row.addView(qtyEt); row.addView(del)
        b.rowsContainer.addView(row)
        rows.add(lenEt to qtyEt)
    }

    private fun field(weight: Float) = EditText(this).apply {
        setTextColor(Color.parseColor("#EAF2FF")); setHintTextColor(Color.parseColor("#5A688F"))
        setPadding(dp(10), dp(10), dp(10), dp(10)); textSize = 14f
        background = strokedBg(Color.parseColor("#0B1326"), 10, Color.parseColor("#243456"))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply { marginEnd = dp(8) }
    }

    private fun clearAll() {
        b.rowsContainer.removeAllViews(); rows.clear(); b.results.removeAllViews()
        repeat(3) { addRow() }
    }

    private fun calculate() {
        val lo = b.inLo.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
        val le = b.inLe.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
        val kerf = b.inKerf.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
        if (lo <= 0) { toast("Longueur de barre Lo invalide"); return }
        val loU = round(lo * SCALE).toInt()
        val kerfU = round(kerf * SCALE).toInt()
        val leU = round(le * SCALE).toInt()

        val pieces = ArrayList<CuttingStock.Piece>()
        var shortest = Int.MAX_VALUE
        for ((lenEt, qtyEt) in rows) {
            val l = lenEt.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
            val q = qtyEt.text.toString().toIntOrNull() ?: 0
            if (l <= 0 || q <= 0) continue
            val lu = round(l * SCALE).toInt()
            if (lu > loU) { toast("La longueur ${num(l)} dépasse la barre Lo"); return }
            pieces.add(CuttingStock.Piece(lu, q, num(l)))
            if (lu < shortest) shortest = lu
        }
        if (pieces.isEmpty()) { toast("Ajoute au moins une pièce (longueur + quantité)"); return }
        if (leU > 0 && shortest != Int.MAX_VALUE && leU > shortest) {
            toast("⚠ Le doit être ≤ à la plus petite pièce (${num(shortest.toDouble() / SCALE)})")
        }

        val res = try { CuttingStock.solve(pieces, loU, kerfU) } catch (e: Exception) { toast("Erreur : ${e.message}"); return }
        renderResults(res, loU, leU)
    }

    private fun renderResults(res: CuttingStock.Result, loU: Int, leU: Int) {
        b.results.removeAllViews()
        val s = res.stats
        b.results.addView(TextView(this).apply {
            text = "✅ ${s.nbBarres} barre(s) • efficacité ${s.pctEfficacite}% • chute totale ${num(s.totalWaste.toDouble() / SCALE)} • ${s.nbPatrons} patron(s)"
            setTextColor(Color.parseColor("#27FFC4")); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(16); layoutParams = lp
        })

        // Couleur par longueur distincte
        val lenColor = HashMap<Int, Int>()
        var ci = 0
        // Regrouper les barres identiques en patrons
        data class Pat(val key: String, val cuts: List<Int>, val waste: Double, var count: Int)
        val pats = LinkedHashMap<String, Pat>()
        for (bar in res.bars) {
            val cuts = bar.cuts.map { it.length }.sortedDescending()
            cuts.forEach { if (!lenColor.containsKey(it)) lenColor[it] = COLORS[ci++ % COLORS.size] }
            val waste = if (bar.realWaste > 0) bar.realWaste else bar.waste
            val key = cuts.joinToString("+")
            val p = pats[key]
            if (p == null) pats[key] = Pat(key, cuts, waste, 1) else p.count++
        }

        var idx = 1
        for (p in pats.values.sortedByDescending { it.count }) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = strokedBg(Color.parseColor("#0E1730"), 14, Color.parseColor("#243456"))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(10); layoutParams = lp
            }
            val wasteOk = leU <= 0 || p.waste <= leU
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            header.addView(TextView(this).apply {
                text = "Patron ${idx++}"; setTextColor(Color.parseColor("#EAF2FF")); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            header.addView(TextView(this).apply {
                text = "× ${p.count} barre(s)"; setTextColor(Color.parseColor("#21E6FF")); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            })
            card.addView(header)

            // composition agrégée
            val agg = LinkedHashMap<Int, Int>()
            p.cuts.forEach { agg[it] = (agg[it] ?: 0) + 1 }
            card.addView(TextView(this).apply {
                text = agg.entries.joinToString("  +  ") { "${num(it.key.toDouble() / SCALE)} ×${it.value}" }
                setTextColor(Color.parseColor("#C7D2F2")); textSize = 13f
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(4); layoutParams = lp
            })

            // barre visuelle
            val visual = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; weightSum = loU.toFloat()
                background = strokedBg(Color.parseColor("#0B1326"), 6, Color.parseColor("#243456"))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)); lp.topMargin = dp(6); layoutParams = lp
            }
            for (c in p.cuts) {
                visual.addView(TextView(this).apply {
                    text = if (c >= loU / 8) num(c.toDouble() / SCALE) else ""
                    setTextColor(Color.WHITE); textSize = 9f; gravity = Gravity.CENTER; maxLines = 1
                    setBackgroundColor(lenColor[c] ?: COLORS[0])
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, c.toFloat())
                })
            }
            val usedU = p.cuts.sum()
            val resteU = (loU - usedU).coerceAtLeast(0)
            if (resteU > 0) visual.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#1A2440"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, resteU.toFloat())
            })
            card.addView(visual)

            card.addView(TextView(this).apply {
                text = "Utilisé ${num(usedU.toDouble() / SCALE)} / ${num(loU.toDouble() / SCALE)}  •  chute ${num(p.waste / SCALE)}" +
                        (if (!wasteOk) "  ⚠ > Le" else "")
                setTextColor(Color.parseColor(if (wasteOk) "#8A97C2" else "#FFC34D")); textSize = 12f
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(4); layoutParams = lp
            })
            b.results.addView(card)
        }
    }

    private fun strokedBg(color: Int, radiusDp: Int, strokeColor: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(1).coerceAtLeast(1), strokeColor)
    }
}
