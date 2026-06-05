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
import kotlin.math.ceil
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

    private fun isDemiVitre(s: String) =
        s.contains("demi vitr") || s.contains("demi-vitr") || s.contains("1/2 vitr") || s.contains("mi vitr")

    private fun nacoBlades(hCm: Double): Int {
        val hmm = hCm * 10
        val table = listOf(380 to 4, 469 to 5, 558 to 6, 647 to 7, 736 to 8, 825 to 9, 914 to 10, 1003 to 11, 1092 to 12, 1181 to 13)
        for ((hm, nb) in table) if (hmm <= hm) return nb
        return 13
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
            porte1v -> renderPorte1V(card, dims, isDemiVitre(s), barCm)
            (pid == "fenetre" || pid == "porte") && naco -> renderNaco(card, dims, barCm)
            (pid == "fenetre" || pid == "porte") && projetant -> renderProjetant(card, dims, barCm)
            (pid == "fenetre" || pid == "porte") && fixe -> renderFixe(card, dims, barCm)
            (pid == "fenetre" || pid == "porte") && mode != null -> renderCoulissante(card, dims, mode, barCm)
            pid == "fenetre" || pid == "porte" -> {
                card.addView(note("Formules dispo : Promotion / 2e / 1er Choix, Naco, Projetant, Fixe, Porte 1 vantail.", "#FFC34D"))
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
        renderAccItems(card, "🔩 Accessoires — $suffix (Qty:$totQty)", listOf(
            Triple("🔒 Serrure $suffix", "$totQty × 2", totQty * 2),
            Triple("⚙️ Roulette $suffix", "$totQty × 4", totQty * 4),
            Triple("🔧 Vis de pose", "$totQty × 4", totQty * 4)
        ))
    }

    /* ════════ PORTE OUVRANT 1 VANTAIL (PV & DV) ════════ */
    private fun renderPorte1V(card: LinearLayout, dims: JSONArray, isDV: Boolean, barCm: Double) {
        val typeLabel = if (isDV) "Demi Vitré" else "Plein Vitré"
        card.addView(note(
            "Porte 1 vantail $typeLabel : BatiH=H+3(×2) · BatiL=L+6(×1) · OuvrH=H-2.7(×2) · OuvrL=L-4.6(×1) · Interm=OuvrL-6.3(×2)" +
                if (isDV) " · Bardage 72cm si H≤200 sinon 82cm, Qty=⌈Interm/9⌉" else "", "#21E6FF"
        ))
        renderLegend(card, dims)
        val bati = mutableListOf<Entry>(); val ouvr = mutableListOf<Entry>()
        val inter = mutableListOf<Entry>(); val bard = mutableListOf<Entry>()
        var totQty = 0
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm")
            val qty = dm.optString("quantite", "1").toIntOrNull() ?: 1
            val ref = dm.optString("dim_ref").ifBlank { "L${di + 1}" }
            val batiH = r2(h + 3); val batiL = r2(l + 6)
            val ouvrH = r2(h - 2.7); val ouvrL = r2(l - 4.6)
            val interL = r2(ouvrL - 6.3)
            totQty += qty
            bati.add(Entry(batiH, qty * 2, ref, di)); bati.add(Entry(batiL, qty, ref, di))
            ouvr.add(Entry(ouvrH, qty * 2, ref, di)); ouvr.add(Entry(ouvrL, qty, ref, di))
            inter.add(Entry(interL, qty * 2, ref, di))
            if (isDV) {
                val bardageL = if (h <= 200) 72.0 else 82.0
                val bardageQty = ceil(interL / 9.0).toInt() * qty
                bard.add(Entry(bardageL, bardageQty, ref, di))
            }
        }
        renderCoupeGroup(card, "BATI PORTE", "#21E6FF", bati, barCm)
        renderCoupeGroup(card, "OUVRANT PORTE", "#8B5CFF", ouvr, barCm)
        renderCoupeGroup(card, "INTERMÉDIAIRE", "#FFC34D", inter, barCm)
        if (isDV) renderCoupeGroup(card, "BARDAGE", "#FF4D9D", bard, barCm)
        renderAccItems(card, "🔩 Accessoires — Porte 1V $typeLabel (Qty:$totQty)", listOf(
            Triple("🔒 Serrure Porte", "$totQty × 1", totQty),
            Triple("🔗 Charnière", "$totQty × 3", totQty * 3),
            Triple("🔧 Vis de pose", "$totQty × 4", totQty * 4)
        ))
    }

    /* ════════ FIXE ════════ */
    private fun renderFixe(card: LinearLayout, dims: JSONArray, barCm: Double) {
        card.addView(note("Fixe : BatiH=H+6(×2) · BatiL=L+6(×2) · ParaH=H-5.6(×2) · ParaL=L-5.6(×2) · VitrH=ParaH-5 · VitrL=ParaL-5 · Aucun accessoire", "#21E6FF"))
        renderLegend(card, dims)
        val bati = mutableListOf<Entry>(); val para = mutableListOf<Entry>()
        val vitre = mutableListOf<Triple<Double, Double, Int>>()
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm")
            val qty = dm.optString("quantite", "1").toIntOrNull() ?: 1
            val ref = dm.optString("dim_ref").ifBlank { "L${di + 1}" }
            val batiH = r2(h + 6); val batiL = r2(l + 6)
            val paraH = r2(h - 5.6); val paraL = r2(l - 5.6)
            bati.add(Entry(batiH, qty * 2, ref, di)); bati.add(Entry(batiL, qty * 2, ref, di))
            para.add(Entry(paraH, qty * 2, ref, di)); para.add(Entry(paraL, qty * 2, ref, di))
            vitre.add(Triple(r2(paraH - 5), r2(paraL - 5), qty))
        }
        renderCoupeGroup(card, "BATI FIXE", "#21E6FF", bati, barCm)
        renderCoupeGroup(card, "PARACLOSE FIXE", "#8B5CFF", para, barCm)
        renderVitres(card, vitre)
        card.addView(note("Aucun accessoire pour le type FIXE.", "#8A97C2"))
    }

    /* ════════ PROJETANT ════════ */
    private fun renderProjetant(card: LinearLayout, dims: JSONArray, barCm: Double) {
        card.addView(note("Projetant : BatiH=H+6(×2) · BatiL=L+6(×2) · OuvrH=H-4.6(×2) · OuvrL=L-4.6(×2) · VitrH=OuvrH-6.5 · VitrL=OuvrL-6.5", "#21E6FF"))
        renderLegend(card, dims)
        val bati = mutableListOf<Entry>(); val ouvr = mutableListOf<Entry>()
        val vitre = mutableListOf<Triple<Double, Double, Int>>()
        var totQty = 0
        val compas = linkedMapOf<String, Int>()
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm")
            val qty = dm.optString("quantite", "1").toIntOrNull() ?: 1
            val ref = dm.optString("dim_ref").ifBlank { "L${di + 1}" }
            val batiH = r2(h + 6); val batiL = r2(l + 6)
            val ouvrH = r2(h - 4.6); val ouvrL = r2(l - 4.6)
            totQty += qty
            bati.add(Entry(batiH, qty * 2, ref, di)); bati.add(Entry(batiL, qty * 2, ref, di))
            ouvr.add(Entry(ouvrH, qty * 2, ref, di)); ouvr.add(Entry(ouvrL, qty * 2, ref, di))
            vitre.add(Triple(r2(ouvrH - 6.5), r2(ouvrL - 6.5), qty))
            val t = if (h < 30) "20" else if (h < 40) "30" else "40"
            compas[t] = (compas[t] ?: 0) + qty * 2
        }
        renderCoupeGroup(card, "BATI PROJETANT", "#21E6FF", bati, barCm)
        renderCoupeGroup(card, "OUVRANT PROJETANT", "#8B5CFF", ouvr, barCm)
        renderVitres(card, vitre)
        val acc = mutableListOf(Triple("🔧 Poignée Projetant", "$totQty × 1", totQty))
        compas.forEach { (t, q) -> acc.add(Triple("🛠️ Compas $t", "selon H", q)) }
        renderAccItems(card, "🔩 Accessoires — Projetant (Qty:$totQty)", acc)
    }

    /* ════════ NACO ════════ */
    private fun renderNaco(card: LinearLayout, dims: JSONArray, barCm: Double) {
        card.addView(note("Naco : BatiH=H+6(×2) · BatiL=L+6(×2) · LameH=10cm (fixe) · LameL=L-15 · LameQty=Qty×NbLames (lames selon H)", "#21E6FF"))
        renderLegend(card, dims)
        val bati = mutableListOf<Entry>()
        val lames = mutableListOf<Triple<Double, Double, Int>>()
        val bladesChips = flowText()
        val acc = mutableListOf<Triple<String, String, Int>>()
        var totQty = 0
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm")
            val qty = dm.optString("quantite", "1").toIntOrNull() ?: 1
            val ref = dm.optString("dim_ref").ifBlank { "L${di + 1}" }
            val col = DIM_COLORS[di % DIM_COLORS.size]
            val nb = nacoBlades(h)
            val batiH = r2(h + 6); val batiL = r2(l + 6)
            totQty += qty
            bati.add(Entry(batiH, qty * 2, ref, di)); bati.add(Entry(batiL, qty * 2, ref, di))
            lames.add(Triple(10.0, r2(l - 15), qty * nb))
            bladesChips.addView(chip("$ref : $nb lames", col))
            acc.add(Triple("🏷️ Lame Naco de $nb ($ref)", "$qty × 2", qty * 2))
        }
        card.addView(sectionTitle("🔢 Nombre de lames"))
        card.addView(bladesChips)
        renderCoupeGroup(card, "BATI NACO", "#21E6FF", bati, barCm)
        renderVitres(card, lames)
        acc.add(Triple("🔧 Vis de pose", "$totQty × 4", totQty * 4))
        renderAccItems(card, "🔩 Accessoires — Naco (Qty:$totQty)", acc)
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

    private fun renderAccItems(card: LinearLayout, title: String, items: List<Triple<String, String, Int>>) {
        card.addView(sectionTitle(title))
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
    private fun renderLegend(card: LinearLayout, dims: JSONArray) {
        val legend = flowText()
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val ref = dm.optString("dim_ref").ifBlank { "L${di + 1}" }
            val col = DIM_COLORS[di % DIM_COLORS.size]
            legend.addView(chip("$ref ${numCm(d(dm, "hauteur_cm"))}×${numCm(d(dm, "largeur_cm"))}cm", col))
        }
        card.addView(legend)
    }

    private fun renderVitres(card: LinearLayout, list: List<Triple<Double, Double, Int>>) {
        card.addView(sectionTitle("🪟 Vitres / Lames"))
        for (v in list) card.addView(TextView(this).apply {
            text = "   • ${numCm(v.first)} × ${numCm(v.second)} cm  ×${v.third} p"
            setTextColor(Color.parseColor("#C7D2F2")); textSize = 12f
        })
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
