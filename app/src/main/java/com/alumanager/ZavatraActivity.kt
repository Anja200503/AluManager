package com.alumanager

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.alumanager.databinding.ActivityZavatraBinding
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.round

/**
 * Zavatra ilaina — nécessaire de pièces par produit (port natif de zavatra.html).
 * Pour chaque produit : pièces à couper (profilés) + plan de coupe Gilmore-Gomory
 * par groupe + accessoires. Formules fidèles au web pour Fenêtre/Porte coulissante
 * (Promotion / 2ème Choix / 1er Choix). Table simple pour les autres produits.
 */
class ZavatraActivity : AppCompatActivity() {

    private lateinit var b: ActivityZavatraBinding
    private var cmd = JSONObject()

    private val DIM_COLORS = intArrayOf(
        0xFFe74c3c.toInt(), 0xFF3498db.toInt(), 0xFF2ecc71.toInt(), 0xFFf39c12.toInt(),
        0xFF9b59b6.toInt(), 0xFF1abc9c.toInt(), 0xFFe67e22.toInt(), 0xFF2980b9.toInt(),
        0xFF27ae60.toInt(), 0xFF8e44ad.toInt(), 0xFFc0392b.toInt(), 0xFF16a085.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityZavatraBinding.inflate(layoutInflater)
        setContentView(b.root)
        cmd = try { JSONObject(intent.getStringExtra("cmd") ?: "{}") } catch (e: Exception) { JSONObject() }
        b.headerSub.text = "${cmd.optString("reference")} — ${cmd.optString("client_nom")}"
        b.btnBack.setOnClickListener { finish() }
        b.btnRecalc.setOnClickListener { render() }
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun r2(v: Double) = round(v * 100) / 100
    private fun numCm(v: Double): String = if (v == Math.floor(v)) v.toInt().toString() else r2(v).toString()
    private fun fmtAr(v: Double): String = "%,d".format(Math.round(v)).replace(",", " ")
    private fun d(o: JSONObject, k: String) = o.optString(k, "0").toDoubleOrNull() ?: 0.0

    private fun render() {
        b.content.removeAllViews()
        val barCm = b.barInput.text.toString().toDoubleOrNull() ?: 580.0
        val produits = cmd.optJSONArray("produits") ?: JSONArray()
        if (produits.length() == 0) {
            b.content.addView(note("Aucun produit dans cette commande.", "#FFC34D"))
            return
        }
        for (i in 0 until produits.length()) {
            val p = produits.optJSONObject(i) ?: continue
            b.content.addView(renderProduit(p, i, barCm))
        }
    }

    /* ── détections (sur config_parsed) ── */
    private fun cfgStr(cfg: JSONObject): String =
        cfg.keys().asSequence().joinToString(" ") { cfg.optString(it) }.lowercase()

    private fun detectMode(s: String): String? = when {
        Regex("1\\s*(er|ere|iere|ier)\\s*choix").containsMatchIn(s) || s.contains("premier choix") -> "1choix"
        Regex("2\\s*(e|eme|ieme)\\s*choix").containsMatchIn(s) || s.contains("deuxieme choix") -> "2choix"
        s.contains("promotion") -> "promo"
        else -> null
    }

    private fun renderProduit(prod: JSONObject, pi: Int, barCm: Double): View {
        val cfg = prod.optJSONObject("config_parsed") ?: JSONObject()
        val s = cfgStr(cfg)
        val pid = prod.optString("product_id")
        val dims = prod.optJSONArray("dimensions") ?: JSONArray()
        val naco = s.contains("naco")
        val projetant = s.contains("projetant")
        val fixe = s.contains("fixe")
        val porte1v = pid == "porte" && s.contains("ouvrant") && (s.contains("1 vantail") || s.contains("1vantail"))
        val mode = detectMode(s)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.glass_card)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12); layoutParams = lp
        }
        // En-tete
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = "${pi + 1}. ${prod.optString("product_label")}"
            setTextColor(Color.parseColor("#EAF2FF")); textSize = 16f; setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        head.addView(TextView(this).apply {
            text = "${fmtAr(d(prod, "total_ar"))} Ar"
            setTextColor(Color.parseColor("#27FFC4")); textSize = 13f; setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(head)
        // config
        val cfgVals = cfg.keys().asSequence().map { cfg.optString(it) }.filter { it.isNotEmpty() }.joinToString(" · ")
        if (cfgVals.isNotEmpty()) card.addView(TextView(this).apply {
            text = cfgVals; setTextColor(Color.parseColor("#8A97C2")); textSize = 11f
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(3); layoutParams = lp
        })

        if (dims.length() == 0) { card.addView(note("Aucune dimension.", "#FFC34D")); return card }

        when {
            (pid == "fenetre" || pid == "porte") && !naco && !projetant && !fixe && !porte1v && mode != null ->
                renderCoulissante(card, dims, mode, barCm)
            (pid == "fenetre" || pid == "porte") && (naco || projetant || fixe || porte1v) -> {
                val t = when { porte1v -> "Ouvrant 1 vantail"; naco -> "Naco"; projetant -> "Projetant"; else -> "Fixe" }
                card.addView(note("Type « $t » : formules spécifiques en cours d'intégration. Dimensions ci-dessous.", "#FFC34D"))
                renderSimple(card, dims, pid)
            }
            else -> renderSimple(card, dims, pid)
        }
        return card
    }

    /* ════════ FENETRE/PORTE COULISSANTE ════════ */
    private data class Entry(val lenCm: Double, val pcs: Int, val ref: String, val di: Int)

    private fun renderCoulissante(card: LinearLayout, dims: JSONArray, mode: String, barCm: Double) {
        val suffix = when (mode) { "promo" -> "Promotion"; "2choix" -> "2ème Choix"; else -> "1er Choix" }
        val fOuvr = if (mode == "promo") "OuvrH=H-4.1 · OuvrL=(L+1)/2" else "OuvrH=H-5.2 · OuvrL=L/2"
        card.addView(note("$suffix : BatiH=H+6 · BatiL=L+6 · $fOuvr · Acros=OuvrH · VitrH=OuvrH-8.3 · VitrL=OuvrL-8.3 · VitrQty=Qty×2", "#21E6FF"))

        val bati = mutableListOf<Entry>()
        val ouvr = mutableListOf<Entry>()
        val acr = mutableListOf<Entry>()
        val vitre = mutableListOf<Triple<Double, Double, Int>>() // vH, vL, qty
        var totQty = 0

        // legende
        val legend = flowText()
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm")
            val qty = dm.optString("quantite", "1").toIntOrNull() ?: 1
            val ref = dm.optString("dim_ref").ifBlank { "L${di + 1}" }
            val col = DIM_COLORS[di % DIM_COLORS.size]
            legend.addView(chip("$ref ${numCm(h)}×${numCm(l)}cm", col))

            val batiH = r2(h + 6); val batiL = r2(l + 6)
            val ouvrH: Double; val ouvrL: Double
            if (mode == "promo") { ouvrH = r2(h - 4.1); ouvrL = r2((l + 1) / 2) }
            else { ouvrH = r2(h - 5.2); ouvrL = r2(l / 2) }
            val acrL = ouvrH
            val vH = r2(ouvrH - 8.3); val vL = r2(ouvrL - 8.3)
            totQty += qty
            bati.add(Entry(batiH, qty * 2, ref, di)); bati.add(Entry(batiL, qty * 2, ref, di))
            ouvr.add(Entry(ouvrH, qty * 4, ref, di)); ouvr.add(Entry(ouvrL, qty * 4, ref, di))
            acr.add(Entry(acrL, qty * 2, ref, di))
            vitre.add(Triple(vH, vL, qty * 2))
        }
        card.addView(legend)

        // groupes de coupe
        renderCoupeGroup(card, "BATI $suffix", "#21E6FF", bati, barCm)
        renderCoupeGroup(card, "OUVRANT $suffix", "#8B5CFF", ouvr, barCm)
        renderCoupeGroup(card, "ACROSAGE $suffix", "#FFC34D", acr, barCm)

        // vitres (info)
        card.addView(sectionTitle("🪟 Vitres"))
        for ((idx, v) in vitre.withIndex()) {
            card.addView(TextView(this).apply {
                text = "   • ${numCm(v.first)} × ${numCm(v.second)} cm  ×${v.third} p"
                setTextColor(Color.parseColor("#C7D2F2")); textSize = 12f
            })
            if (idx == 0) Unit
        }

        // accessoires
        renderAccessoires(card, totQty, suffix)
    }

    private fun renderCoupeGroup(card: LinearLayout, label: String, color: String, entries: List<Entry>, barCm: Double) {
        val valid = entries.filter { it.lenCm > 0 && it.pcs > 0 }
        if (valid.isEmpty()) return
        // aggregat affichage
        card.addView(sectionTitle("✂️ $label"))
        val agg = LinkedHashMap<Double, Int>()
        valid.forEach { agg[it.lenCm] = (agg[it.lenCm] ?: 0) + it.pcs }
        val aggLine = agg.entries.joinToString("   ") { "${numCm(it.key)}cm×${it.value}" }
        card.addView(TextView(this).apply {
            text = aggLine; setTextColor(Color.parseColor("#C7D2F2")); textSize = 12f
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(2); layoutParams = lp
        })
        // coupe GG (mm)
        val pieces = valid.map { CuttingStock.Piece(Math.round(it.lenCm * 10).toInt(), it.pcs, it.ref) }
        val res = try { CuttingStock.solve(pieces, (barCm * 10).toInt(), 0) } catch (e: Exception) { null }
        if (res == null || res.bars.isEmpty()) return
        val s = res.stats
        card.addView(TextView(this).apply {
            text = "${s.nbBarres} barre(s) × ${barCm.toInt()}cm  •  efficacité ${s.pctEfficacite}%  •  chute ${s.pctChute}%"
            setTextColor(Color.parseColor(color)); textSize = 12f; setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(4); layoutParams = lp
        })
        val L = (barCm * 10).toFloat()
        res.bars.forEachIndexed { idx, bar ->
            val waste = if (bar.realWaste > 0) bar.realWaste else bar.waste
            card.addView(TextView(this).apply {
                text = "Barre ${idx + 1} • ${bar.cuts.size} pcs • chute ${r2(waste / 10)}cm"
                setTextColor(Color.parseColor("#8A97C2")); textSize = 11f
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(6); layoutParams = lp
            })
            val visual = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; weightSum = L
                background = strokedBg(Color.parseColor("#0B1326"), 6, Color.parseColor("#243456"))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24))
                lp.topMargin = dp(3); layoutParams = lp
            }
            for (cut in bar.cuts) {
                visual.addView(TextView(this).apply {
                    text = if (cut.length >= 4000) (cut.length / 10).toString() else ""
                    setTextColor(Color.WHITE); textSize = 8f; gravity = Gravity.CENTER
                    setBackgroundColor(cut.color)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, cut.length.toFloat())
                })
            }
            if (waste > 10) visual.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#1A2440"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, waste.toFloat())
            })
            card.addView(visual)
        }
    }

    private fun renderAccessoires(card: LinearLayout, totQty: Int, suffix: String) {
        card.addView(sectionTitle("🔩 Accessoires — $suffix (Qty:$totQty)"))
        val items = listOf(
            Triple("🔒 Serrure $suffix", "$totQty × 2", totQty * 2),
            Triple("⚙️ Roulette $suffix", "$totQty × 4", totQty * 4),
            Triple("🔧 Vis de pose", "$totQty × 4", totQty * 4)
        )
        for (it in items) {
            val rowv = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = strokedBg(Color.parseColor("#0E1730"), 12, Color.parseColor("#243456"))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(6); layoutParams = lp
            }
            val txt = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            txt.addView(TextView(this).apply { text = it.first; setTextColor(Color.parseColor("#EAF2FF")); textSize = 14f; setTypeface(typeface, Typeface.BOLD) })
            txt.addView(TextView(this).apply { text = it.second; setTextColor(Color.parseColor("#8A97C2")); textSize = 11f })
            rowv.addView(txt)
            rowv.addView(TextView(this).apply {
                text = "${it.third} pcs"; setTextColor(Color.parseColor("#27FFC4")); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
            })
            card.addView(rowv)
        }
    }

    /* ════════ TABLE SIMPLE ════════ */
    private fun renderSimple(card: LinearLayout, dims: JSONArray, pid: String) {
        val haH = pid != "lavarangana"; val haP = pid == "vitrine"
        card.addView(sectionTitle("📐 Dimensions"))
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val ref = dm.optString("dim_ref").ifBlank { "L${di + 1}" }
            val col = DIM_COLORS[di % DIM_COLORS.size]
            val parts = buildString {
                if (haH) append("H${numCm(d(dm, "hauteur_cm"))} ")
                append("L${numCm(d(dm, "largeur_cm"))} ")
                if (haP) append("P${numCm(d(dm, "profondeur_cm"))} ")
                append("×${dm.optString("quantite", "1")}")
            }
            val rowv = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
            }
            rowv.addView(View(this).apply {
                setBackgroundColor(col); layoutParams = LinearLayout.LayoutParams(dp(4), dp(20))
            })
            rowv.addView(TextView(this).apply {
                text = "  $ref  $parts"
                setTextColor(Color.parseColor("#EAF2FF")); textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            rowv.addView(TextView(this).apply {
                text = "${fmtAr(d(dm, "prix_total"))} Ar"
                setTextColor(Color.parseColor("#27FFC4")); textSize = 12f
            })
            card.addView(rowv)
        }
    }

    /* ════════ HELPERS UI ════════ */
    private fun note(text: String, color: String) = TextView(this).apply {
        this.text = text; setTextColor(Color.parseColor(color)); textSize = 12f
        background = strokedBg(Color.parseColor("#0E1730"), 12, Color.parseColor(color))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8); layoutParams = lp
    }
    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#EAF2FF")); textSize = 13f; setTypeface(typeface, Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(12); layoutParams = lp
    }
    private fun flowText() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8); layoutParams = lp
    }
    private fun chip(text: String, color: Int) = TextView(this).apply {
        this.text = text; setTextColor(color); textSize = 10f; setTypeface(typeface, Typeface.BOLD)
        background = strokedBg(Color.parseColor("#0E1730"), 10, color)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginEnd = dp(6); layoutParams = lp
    }
    private fun strokedBg(color: Int, radiusDp: Int, strokeColor: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(1).coerceAtLeast(1), strokeColor)
    }
}
