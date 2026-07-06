package com.alumanager

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.alumanager.databinding.ActivityZavatraBinding
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.round

/**
 * Zavatra ilaina — nécessaire de pièces par produit (port natif de zavatra.html).
 * Reproduit la vraie structure : tableau multi-colonnes (groupes BATI/OUVRANT/…),
 * plan de coupe Gilmore-Gomory par groupe + accessoires, par type de produit.
 */
class ZavatraActivity : AppCompatActivity() {

    private lateinit var b: ActivityZavatraBinding
    private var cmd = JSONObject()

    private val DIM_COLORS = intArrayOf(
        0xFFe74c3c.toInt(), 0xFF3498db.toInt(), 0xFF2ecc71.toInt(), 0xFFf39c12.toInt(),
        0xFF9b59b6.toInt(), 0xFF1abc9c.toInt(), 0xFFe67e22.toInt(), 0xFF2980b9.toInt(),
        0xFF27ae60.toInt(), 0xFF8e44ad.toInt(), 0xFFc0392b.toInt(), 0xFF16a085.toInt()
    )
    private val FIX = 0xFF334063.toInt()
    private val C_BATI = 0xFF1E88E5.toInt()
    private val C_OUVR = 0xFF1565C0.toInt()
    private val C_ACR = 0xFFE0A020.toInt()
    private val C_VITRE = 0xFF2E9E5B.toInt()
    private val C_INTER = 0xFFE0A020.toInt()
    private val C_BARD = 0xFFC2118F.toInt()
    private val C_NACO = 0xFF16A085.toInt()

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
    private fun withA(color: Int, a: Int) = (a shl 24) or (color and 0x00FFFFFF)

    private fun render() {
        b.content.removeAllViews()
        F = FormulaConfig.all(this)
        val barCm = b.barInput.text.toString().toDoubleOrNull() ?: 580.0
        val produits = cmd.optJSONArray("produits") ?: JSONArray()
        if (produits.length() == 0) { b.content.addView(note("Aucun produit dans cette commande.", "#E0A020")); return }
        for (i in 0 until produits.length()) {
            val p = produits.optJSONObject(i) ?: continue
            b.content.addView(renderProduit(p, i, barCm))
        }
    }

    /* ── détections ── */
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
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12); layoutParams = lp
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = "${pi + 1}. ${prod.optString("product_label")}"
            setTextColor(Color.parseColor("#1F2733")); textSize = 16f; setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        head.addView(TextView(this).apply {
            text = "${fmtAr(d(prod, "total_ar"))} Ar"
            setTextColor(Color.parseColor("#2E9E5B")); textSize = 13f; setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(head)
        val cfgVals = cfg.keys().asSequence().map { cfg.optString(it) }.filter { it.isNotEmpty() }.joinToString(" · ")
        if (cfgVals.isNotEmpty()) card.addView(TextView(this).apply {
            text = cfgVals; setTextColor(Color.parseColor("#6B7686")); textSize = 11f
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(3); layoutParams = lp
        })
        if (dims.length() == 0) { card.addView(note("Aucune dimension.", "#E0A020")); return card }

        when {
            porte1v -> renderPorte1V(card, dims, isDemiVitre(s), barCm)
            (pid == "fenetre" || pid == "porte") && naco -> renderNaco(card, dims, barCm)
            (pid == "fenetre" || pid == "porte") && projetant -> renderProjetant(card, dims, barCm)
            (pid == "fenetre" || pid == "porte") && fixe -> renderFixe(card, dims, barCm)
            (pid == "fenetre" || pid == "porte") && mode != null -> renderCoulissante(card, dims, mode, barCm)
            pid == "fenetre" || pid == "porte" -> {
                card.addView(note("Formules dispo : Promotion / 2e / 1er Choix, Naco, Projetant, Fixe, Porte 1 vantail.", "#E0A020"))
                renderSimple(card, dims, pid)
            }
            else -> renderSimple(card, dims, pid)
        }
        return card
    }

    private data class Entry(val lenCm: Double, val pcs: Int, val ref: String, val di: Int, val tag: String = "")

    private fun dimRef(dm: JSONObject, di: Int) = "N°${di + 1}"

    private var curRefColors: Map<String, Int> = emptyMap()
    private var F: Map<String, Double> = emptyMap()
    private fun f(k: String) = F[k] ?: 0.0
    private fun refColorMap(dims: JSONArray): Map<String, Int> {
        val m = HashMap<String, Int>()
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            m[dimRef(dm, di)] = DIM_COLORS[di % DIM_COLORS.size]
        }
        return m
    }

    /* ════════ COULISSANTE ════════ */
    private fun renderCoulissante(card: LinearLayout, dims: JSONArray, mode: String, barCm: Double) {
        val suffix = when (mode) { "promo" -> "Promotion"; "2choix" -> "2ème Choix"; else -> "1er Choix" }
        val fOuvr = if (mode == "promo") "OuvrH=H-4.1 · OuvrL=(L+1)/2" else "OuvrH=H-5.2 · OuvrL=L/2"
        card.addView(note("$suffix : BatiH=H+6 · BatiL=L+6 · $fOuvr · Acros=OuvrH · VitrH=OuvrH-8.3 · VitrL=OuvrL-8.3 · VitrQty=Qty×2", "#1E88E5"))
        renderLegend(card, dims)
        curRefColors = refColorMap(dims)
        val bati = mutableListOf<Entry>(); val ouvr = mutableListOf<Entry>(); val acr = mutableListOf<Entry>()
        val rows = mutableListOf<Pair<Int, List<String>>>()
        var totQty = 0
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm")
            val qty = dm.optString("quantite", "1").toIntOrNull() ?: 1
            val ref = dimRef(dm, di); val col = DIM_COLORS[di % DIM_COLORS.size]
            val batiH = r2(h + f("coul_bati_h")); val batiL = r2(l + f("coul_bati_l"))
            val div = if (f("coul_ouvr_div") != 0.0) f("coul_ouvr_div") else 2.0
            val ouvrH: Double; val ouvrL: Double
            if (mode == "promo") { ouvrH = r2(h - f("coul_ouvrH_promo")); ouvrL = r2((l + f("coul_ouvrL_promo_add")) / div) }
            else { ouvrH = r2(h - f("coul_ouvrH_choix")); ouvrL = r2(l / div) }
            val acrL = ouvrH; val vH = r2(ouvrH - f("coul_vitre")); val vL = r2(ouvrL - f("coul_vitre"))
            totQty += qty
            bati.add(Entry(batiH, qty * 2, ref, di, "H")); bati.add(Entry(batiL, qty * 2, ref, di, "L"))
            ouvr.add(Entry(ouvrH, qty * 4, ref, di, "H")); ouvr.add(Entry(ouvrL, qty * 4, ref, di, "L"))
            acr.add(Entry(acrL, qty * 2, ref, di, "H"))
            rows.add(col to listOf(
                ref, numCm(h), numCm(l), qty.toString(),
                numCm(batiH), "${qty * 2}", numCm(batiL), "${qty * 2}",
                numCm(ouvrH), "${qty * 4}", numCm(ouvrL), "${qty * 4}",
                numCm(acrL), "${qty * 2}",
                numCm(vH), numCm(vL), "${qty * 2}"
            ))
        }
        card.addView(buildTable(
            listOf(Triple("Dim", 1, FIX), Triple("H", 1, FIX), Triple("L", 1, FIX), Triple("Qty", 1, FIX),
                Triple("BATI", 4, C_BATI), Triple("OUVRANT", 4, C_OUVR), Triple("ACROSAGE", 2, C_ACR), Triple("VITRE", 3, C_VITRE)),
            listOf("" to FIX, "" to FIX, "" to FIX, "" to FIX,
                "Hcm" to C_BATI, "Pcs" to C_BATI, "Lcm" to C_BATI, "Pcs" to C_BATI,
                "Hcm" to C_OUVR, "Pcs" to C_OUVR, "Lcm" to C_OUVR, "Pcs" to C_OUVR,
                "cm" to C_ACR, "Pcs" to C_ACR, "Hcm" to C_VITRE, "Lcm" to C_VITRE, "Qty" to C_VITRE),
            rows
        ))
        renderCoupeGroup(card, "BATI $suffix", "#1E88E5", bati, barCm)
        renderCoupeGroup(card, "OUVRANT $suffix", "#1565C0", ouvr, barCm)
        renderCoupeGroup(card, "ACROSAGE $suffix", "#E0A020", acr, barCm)
        renderAccItems(card, "🔩 Accessoires — $suffix (Qty:$totQty)", listOf(
            Triple("🔒 Serrure $suffix", "$totQty × 2", totQty * 2),
            Triple("⚙️ Roulette $suffix", "$totQty × 4", totQty * 4),
            Triple("🔧 Vis de pose", "$totQty × 4", totQty * 4)
        ))
    }

    /* ════════ PORTE 1 VANTAIL ════════ */
    private fun renderPorte1V(card: LinearLayout, dims: JSONArray, isDV: Boolean, barCm: Double) {
        val typeLabel = if (isDV) "Demi Vitré" else "Plein Vitré"
        card.addView(note("Porte 1 vantail $typeLabel : BatiH=H+3(×2) · BatiL=L+6(×1) · OuvrH=H-2.7(×2) · OuvrL=L-4.6(×1) · Interm=OuvrL-6.3(×2)" +
            if (isDV) " · Bardage 72cm si H≤200 sinon 82cm, Qty=⌈Interm/9⌉" else "", "#1E88E5"))
        renderLegend(card, dims)
        curRefColors = refColorMap(dims)
        val bati = mutableListOf<Entry>(); val ouvr = mutableListOf<Entry>(); val inter = mutableListOf<Entry>(); val bard = mutableListOf<Entry>()
        val rows = mutableListOf<Pair<Int, List<String>>>()
        var totQty = 0
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm")
            val qty = dm.optString("quantite", "1").toIntOrNull() ?: 1
            val ref = dimRef(dm, di); val col = DIM_COLORS[di % DIM_COLORS.size]
            val batiH = r2(h + f("p1v_bati_h")); val batiL = r2(l + f("p1v_bati_l"))
            val ouvrH = r2(h - f("p1v_ouvr_h")); val ouvrL = r2(l - f("p1v_ouvr_l")); val interL = r2(ouvrL - f("p1v_inter"))
            totQty += qty
            bati.add(Entry(batiH, qty * 2, ref, di, "H")); bati.add(Entry(batiL, qty, ref, di, "L"))
            ouvr.add(Entry(ouvrH, qty * 2, ref, di, "H")); ouvr.add(Entry(ouvrL, qty, ref, di, "L"))
            inter.add(Entry(interL, qty * 2, ref, di, "I"))
            val cells = mutableListOf(ref, numCm(h), numCm(l), qty.toString(),
                numCm(batiH), "${qty * 2}", numCm(batiL), "$qty",
                numCm(ouvrH), "${qty * 2}", numCm(ouvrL), "$qty",
                numCm(interL), "${qty * 2}")
            if (isDV) {
                val bardageL = if (h <= f("p1v_bard_seuil")) f("p1v_bard_court") else f("p1v_bard_long")
                val bdiv = if (f("p1v_bard_div") != 0.0) f("p1v_bard_div") else 9.0
                val bardageQty = ceil(interL / bdiv).toInt() * qty
                bard.add(Entry(bardageL, bardageQty, ref, di, "B"))
                cells.add(numCm(bardageL)); cells.add("$bardageQty")
            }
            rows.add(col to cells)
        }
        val groups = mutableListOf(Triple("Dim", 1, FIX), Triple("H", 1, FIX), Triple("L", 1, FIX), Triple("Qty", 1, FIX),
            Triple("BATI PORTE", 4, C_BATI), Triple("OUVRANT", 4, C_OUVR), Triple("INTERM.", 2, C_INTER))
        val subs = mutableListOf("" to FIX, "" to FIX, "" to FIX, "" to FIX,
            "Hcm" to C_BATI, "Pcs" to C_BATI, "Lcm" to C_BATI, "Pcs" to C_BATI,
            "Hcm" to C_OUVR, "Pcs" to C_OUVR, "Lcm" to C_OUVR, "Pcs" to C_OUVR,
            "Lcm" to C_INTER, "Pcs" to C_INTER)
        if (isDV) { groups.add(Triple("BARDAGE", 2, C_BARD)); subs.add("Lcm" to C_BARD); subs.add("Pcs" to C_BARD) }
        card.addView(buildTable(groups, subs, rows))
        renderCoupeGroup(card, "BATI PORTE", "#1E88E5", bati, barCm)
        renderCoupeGroup(card, "OUVRANT PORTE", "#1565C0", ouvr, barCm)
        renderCoupeGroup(card, "INTERMÉDIAIRE", "#E0A020", inter, barCm)
        if (isDV) renderCoupeGroup(card, "BARDAGE", "#C2118F", bard, barCm)
        renderAccItems(card, "🔩 Accessoires — Porte 1V $typeLabel (Qty:$totQty)", listOf(
            Triple("🔒 Serrure Porte", "$totQty × 1", totQty),
            Triple("🔗 Charnière", "$totQty × 3", totQty * 3),
            Triple("🔧 Vis de pose", "$totQty × 4", totQty * 4)
        ))
    }

    /* ════════ FIXE ════════ */
    private fun renderFixe(card: LinearLayout, dims: JSONArray, barCm: Double) {
        card.addView(note("Fixe : BatiH=H+6(×2) · BatiL=L+6(×2) · ParaH=H-5.6(×2) · ParaL=L-5.6(×2) · VitrH=ParaH-5 · VitrL=ParaL-5 · Aucun accessoire", "#1E88E5"))
        renderLegend(card, dims)
        curRefColors = refColorMap(dims)
        val bati = mutableListOf<Entry>(); val para = mutableListOf<Entry>()
        val rows = mutableListOf<Pair<Int, List<String>>>()
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm")
            val qty = dm.optString("quantite", "1").toIntOrNull() ?: 1
            val ref = dimRef(dm, di); val col = DIM_COLORS[di % DIM_COLORS.size]
            val batiH = r2(h + f("fixe_bati_h")); val batiL = r2(l + f("fixe_bati_l"))
            val paraH = r2(h - f("fixe_para_h")); val paraL = r2(l - f("fixe_para_l"))
            val vH = r2(paraH - f("fixe_vitre")); val vL = r2(paraL - f("fixe_vitre"))
            bati.add(Entry(batiH, qty * 2, ref, di, "H")); bati.add(Entry(batiL, qty * 2, ref, di, "L"))
            para.add(Entry(paraH, qty * 2, ref, di, "H")); para.add(Entry(paraL, qty * 2, ref, di, "L"))
            rows.add(col to listOf(ref, numCm(h), numCm(l), qty.toString(),
                numCm(batiH), "${qty * 2}", numCm(batiL), "${qty * 2}",
                numCm(paraH), "${qty * 2}", numCm(paraL), "${qty * 2}",
                numCm(vH), numCm(vL), "$qty"))
        }
        card.addView(buildTable(
            listOf(Triple("Dim", 1, FIX), Triple("H", 1, FIX), Triple("L", 1, FIX), Triple("Qty", 1, FIX),
                Triple("BATI FIXE", 4, C_BATI), Triple("PARACLOSE", 4, C_OUVR), Triple("VITRE", 3, C_VITRE)),
            listOf("" to FIX, "" to FIX, "" to FIX, "" to FIX,
                "Hcm" to C_BATI, "Pcs" to C_BATI, "Lcm" to C_BATI, "Pcs" to C_BATI,
                "Hcm" to C_OUVR, "Pcs" to C_OUVR, "Lcm" to C_OUVR, "Pcs" to C_OUVR,
                "Hcm" to C_VITRE, "Lcm" to C_VITRE, "Qty" to C_VITRE),
            rows
        ))
        renderCoupeGroup(card, "BATI FIXE", "#1E88E5", bati, barCm)
        renderCoupeGroup(card, "PARACLOSE FIXE", "#1565C0", para, barCm)
        card.addView(note("Aucun accessoire pour le type FIXE.", "#6B7686"))
    }

    /* ════════ PROJETANT ════════ */
    private fun renderProjetant(card: LinearLayout, dims: JSONArray, barCm: Double) {
        card.addView(note("Projetant : BatiH=H+6(×2) · BatiL=L+6(×2) · OuvrH=H-4.6(×2) · OuvrL=L-4.6(×2) · VitrH=OuvrH-6.5 · VitrL=OuvrL-6.5", "#1E88E5"))
        renderLegend(card, dims)
        curRefColors = refColorMap(dims)
        val bati = mutableListOf<Entry>(); val ouvr = mutableListOf<Entry>()
        val rows = mutableListOf<Pair<Int, List<String>>>()
        var totQty = 0; val compas = linkedMapOf<String, Int>()
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm")
            val qty = dm.optString("quantite", "1").toIntOrNull() ?: 1
            val ref = dimRef(dm, di); val col = DIM_COLORS[di % DIM_COLORS.size]
            val batiH = r2(h + f("proj_bati_h")); val batiL = r2(l + f("proj_bati_l"))
            val ouvrH = r2(h - f("proj_ouvr_h")); val ouvrL = r2(l - f("proj_ouvr_l"))
            val vH = r2(ouvrH - f("proj_vitre")); val vL = r2(ouvrL - f("proj_vitre"))
            totQty += qty
            bati.add(Entry(batiH, qty * 2, ref, di, "H")); bati.add(Entry(batiL, qty * 2, ref, di, "L"))
            ouvr.add(Entry(ouvrH, qty * 2, ref, di, "H")); ouvr.add(Entry(ouvrL, qty * 2, ref, di, "L"))
            val t = if (h < 30) "20" else if (h < 40) "30" else "40"
            compas[t] = (compas[t] ?: 0) + qty * 2
            rows.add(col to listOf(ref, numCm(h), numCm(l), qty.toString(),
                numCm(batiH), "${qty * 2}", numCm(batiL), "${qty * 2}",
                numCm(ouvrH), "${qty * 2}", numCm(ouvrL), "${qty * 2}",
                numCm(vH), numCm(vL), "$qty"))
        }
        card.addView(buildTable(
            listOf(Triple("Dim", 1, FIX), Triple("H", 1, FIX), Triple("L", 1, FIX), Triple("Qty", 1, FIX),
                Triple("BATI PROJ.", 4, C_BATI), Triple("OUVRANT", 4, C_OUVR), Triple("VITRE", 3, C_VITRE)),
            listOf("" to FIX, "" to FIX, "" to FIX, "" to FIX,
                "Hcm" to C_BATI, "Pcs" to C_BATI, "Lcm" to C_BATI, "Pcs" to C_BATI,
                "Hcm" to C_OUVR, "Pcs" to C_OUVR, "Lcm" to C_OUVR, "Pcs" to C_OUVR,
                "Hcm" to C_VITRE, "Lcm" to C_VITRE, "Qty" to C_VITRE),
            rows
        ))
        renderCoupeGroup(card, "BATI PROJETANT", "#1E88E5", bati, barCm)
        renderCoupeGroup(card, "OUVRANT PROJETANT", "#1565C0", ouvr, barCm)
        val acc = mutableListOf(Triple("🔧 Poignée Projetant", "$totQty × 1", totQty))
        compas.forEach { (t, q) -> acc.add(Triple("🛠️ Compas $t", "selon H", q)) }
        renderAccItems(card, "🔩 Accessoires — Projetant (Qty:$totQty)", acc)
    }

    /* ════════ NACO ════════ */
    private fun renderNaco(card: LinearLayout, dims: JSONArray, barCm: Double) {
        card.addView(note("Naco : BatiH=H+6(×2) · BatiL=L+6(×2) · LameH=10cm (fixe) · LameL=L-15 · LameQty=Qty×NbLames (lames selon H)", "#1E88E5"))
        renderLegend(card, dims)
        curRefColors = refColorMap(dims)
        val bati = mutableListOf<Entry>()
        val rows = mutableListOf<Pair<Int, List<String>>>()
        val acc = mutableListOf<Triple<String, String, Int>>()
        var totQty = 0
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm")
            val qty = dm.optString("quantite", "1").toIntOrNull() ?: 1
            val ref = dimRef(dm, di); val col = DIM_COLORS[di % DIM_COLORS.size]
            val nb = nacoBlades(h)
            val batiH = r2(h + f("naco_bati_h")); val batiL = r2(l + f("naco_bati_l"))
            val lameL = r2(l - f("naco_lame_l")); val lameQty = qty * nb
            totQty += qty
            bati.add(Entry(batiH, qty * 2, ref, di, "H")); bati.add(Entry(batiL, qty * 2, ref, di, "L"))
            acc.add(Triple("🏷️ Lame Naco de $nb ($ref)", "$qty × 2", qty * 2))
            rows.add(col to listOf(ref, numCm(h), numCm(l), qty.toString(),
                numCm(batiH), "${qty * 2}", numCm(batiL), "${qty * 2}",
                "$nb", numCm(f("naco_lame_h")), numCm(lameL), "$lameQty"))
        }
        card.addView(buildTable(
            listOf(Triple("Dim", 1, FIX), Triple("H", 1, FIX), Triple("L", 1, FIX), Triple("Qty", 1, FIX),
                Triple("BATI NACO", 4, C_BATI), Triple("Lames", 1, C_NACO), Triple("LAME", 3, C_VITRE)),
            listOf("" to FIX, "" to FIX, "" to FIX, "" to FIX,
                "Hcm" to C_BATI, "Pcs" to C_BATI, "Lcm" to C_BATI, "Pcs" to C_BATI,
                "Nb" to C_NACO, "Hcm" to C_VITRE, "Lcm" to C_VITRE, "Qty" to C_VITRE),
            rows
        ))
        renderCoupeGroup(card, "BATI NACO", "#1E88E5", bati, barCm)
        acc.add(Triple("🔧 Vis de pose", "$totQty × 4", totQty * 4))
        renderAccItems(card, "🔩 Accessoires — Naco (Qty:$totQty)", acc)
    }

    /* ════════ TABLE SIMPLE ════════ */
    private fun renderSimple(card: LinearLayout, dims: JSONArray, pid: String) {
        val haH = pid != "lavarangana"; val haP = pid == "vitrine"
        val groups = mutableListOf(Triple("Dim", 1, FIX))
        if (haH) groups.add(Triple("H", 1, FIX))
        groups.add(Triple("L", 1, FIX))
        if (haP) groups.add(Triple("P", 1, FIX))
        groups.add(Triple("Qté", 1, FIX)); groups.add(Triple("Prix U.", 1, C_OUVR)); groups.add(Triple("Total", 1, C_VITRE))
        val rows = mutableListOf<Pair<Int, List<String>>>()
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val ref = dimRef(dm, di); val col = DIM_COLORS[di % DIM_COLORS.size]
            val cells = mutableListOf(ref)
            if (haH) cells.add(numCm(d(dm, "hauteur_cm")))
            cells.add(numCm(d(dm, "largeur_cm")))
            if (haP) cells.add(numCm(d(dm, "profondeur_cm")))
            cells.add(dm.optString("quantite", "1"))
            cells.add(fmtAr(d(dm, "prix_unitaire")))
            cells.add(fmtAr(d(dm, "prix_total")))
            rows.add(col to cells)
        }
        card.addView(buildTable(groups, emptyList(), rows, 1))
    }

    /* ════════ TABLE BUILDER ════════ */
    private fun buildTable(
        groups: List<Triple<String, Int, Int>>,
        subs: List<Pair<String, Int>>,
        rows: List<Pair<Int, List<String>>>,
        fixedCols: Int = 4
    ): View {
        val hasSub = subs.isNotEmpty()
        // séparer les groupes : colonnes fixes (gauche) vs défilantes (droite)
        var spanAcc = 0; var split = 0
        for ((i, g) in groups.withIndex()) {
            if (spanAcc >= fixedCols) { split = i; break }
            spanAcc += g.second; split = i + 1
        }
        fun part(grp: List<Triple<String, Int, Int>>, sub: List<Pair<String, Int>>, left: Boolean): TableLayout {
            val t = TableLayout(this).apply { setBackgroundColor(Color.parseColor("#E2E7EF")) }
            val ra = TableRow(this)
            for ((label, span, color) in grp) ra.addView(thCell(label, color, span, true))
            t.addView(ra)
            if (hasSub) {
                val rb = TableRow(this)
                for ((label, color) in sub) rb.addView(thCell(label, color, 1, false))
                t.addView(rb)
            }
            for ((rc, cells) in rows) {
                val tr = TableRow(this)
                val pcells = if (left) cells.take(fixedCols) else cells.drop(fixedCols)
                pcells.forEachIndexed { idx, c -> tr.addView(tdCell(c, rc, left && idx == 0)) }
                t.addView(tr)
            }
            return t
        }
        val leftTable = part(groups.take(split), if (hasSub) subs.take(fixedCols) else emptyList(), true)
        val rightTable = part(groups.drop(split), if (hasSub) subs.drop(fixedCols) else emptyList(), false)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(leftTable, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            // ligne épaisse de séparation fixe / défilant
            addView(View(this@ZavatraActivity).apply {
                setBackgroundColor(Color.parseColor("#1E88E5"))
            }, LinearLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT))
            addView(HorizontalScrollView(this@ZavatraActivity).apply {
                isHorizontalScrollBarEnabled = false
                addView(rightTable)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8); layoutParams = lp
        }
    }

    private fun thCell(text: String, color: Int, span: Int, group: Boolean): TextView = TextView(this).apply {
        this.text = text; setTextColor(Color.parseColor("#1F2733")); textSize = if (group) 11f else 9.5f
        setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; maxLines = 1
        minimumHeight = dp(if (group) 32 else 26)
        setPadding(dp(7), dp(6), dp(7), dp(6))
        setBackgroundColor(withA(color, if (group) 0x55 else 0x2E))
        val lp = TableRow.LayoutParams(); lp.span = span; lp.setMargins(1, 1, 1, 1); layoutParams = lp
    }

    private fun tdCell(text: String, rowColor: Int, first: Boolean): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#1F2733"))
        textSize = 11f; gravity = if (first) Gravity.CENTER_VERTICAL else Gravity.CENTER
        maxLines = 1; minimumHeight = dp(34)
        setPadding(dp(7), dp(6), dp(7), dp(6))
        setBackgroundColor(withA(rowColor, if (first) 0x66 else 0x12))
        if (first) setTypeface(typeface, Typeface.BOLD)
        val lp = TableRow.LayoutParams(); lp.setMargins(1, 1, 1, 1); layoutParams = lp
    }

    /* ════════ COUPE PAR GROUPE (Gilmore-Gomory) ════════ */
    private fun renderCoupeGroup(card: LinearLayout, label: String, color: String, entries: List<Entry>, barCm: Double) {
        val valid = entries.filter { it.lenCm > 0 && it.pcs > 0 }
        if (valid.isEmpty()) return
        fun labelOf(e: Entry) = if (e.tag.isNotBlank()) "${e.tag}${e.di + 1}" else e.ref
        val colorMap = HashMap<String, Int>()
        valid.forEach { colorMap[labelOf(it)] = DIM_COLORS[it.di % DIM_COLORS.size] }
        val pieces = valid.map { CuttingStock.Piece(Math.round(it.lenCm * 10).toInt(), it.pcs, labelOf(it)) }
        val res = try { CuttingStock.solve(pieces, (barCm * 10).toInt(), 0) } catch (e: Exception) { null } ?: return
        if (res.bars.isEmpty()) return
        val s = res.stats
        val ci = Color.parseColor(color)

        // Carte du profilé
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = strokedBg(Color.parseColor("#EEF1F6"), 14, ci)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12); layoutParams = lp
        }
        val hdr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        hdr.addView(TextView(this).apply {
            text = "🪚 $label"; setTextColor(ci); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        hdr.addView(TextView(this).apply {
            text = "${s.nbBarres} barre(s) • ${s.pctEfficacite}%"
            setTextColor(Color.parseColor("#6B7686")); textSize = 12f
        })
        block.addView(hdr)

        // Pièces nécessaires (agrégé) pour ce profilé
        val agg = LinkedHashMap<Double, Int>()
        valid.forEach { agg[it.lenCm] = (agg[it.lenCm] ?: 0) + it.pcs }
        block.addView(TextView(this).apply {
            text = "Pièces : " + agg.entries.joinToString("  ·  ") { "${numCm(it.key)}cm ×${it.value}" }
            setTextColor(Color.parseColor("#445064")); textSize = 12f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        val L = (barCm * 10).toFloat()
        res.bars.forEachIndexed { idx, bar ->
            val waste = if (bar.realWaste > 0) bar.realWaste else bar.waste
            val usedPct = Math.round((L - waste) / L * 100).toInt()
            // En-tête cliquable : replie / déplie le détail de la barre (animé)
            val baseTitle = "Barre ${idx + 1}  —  ${bar.cuts.size} pièce(s) · utilisé ${usedPct}% · reste ${r2(waste / 10)}cm"
            val detail = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val header = TextView(this).apply {
                text = "▾  $baseTitle"
                setTextColor(Color.parseColor("#1F2733")); textSize = 12f; setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(2), dp(4), dp(2), dp(4))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(8); layoutParams = lp
            }
            header.setOnClickListener {
                val show = detail.visibility != View.VISIBLE
                android.transition.TransitionManager.beginDelayedTransition(
                    block, android.transition.AutoTransition().apply { duration = 220 })
                detail.visibility = if (show) View.VISIBLE else View.GONE
                header.text = (if (show) "▾  " else "▸  ") + baseTitle
            }
            block.addView(header)
            // Barre visuelle
            val visual = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; weightSum = L
                background = strokedBg(Color.parseColor("#F4F6F9"), 6, Color.parseColor("#E2E7EF"))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)); lp.topMargin = dp(4); layoutParams = lp
            }
            bar.cuts.forEachIndexed { ci2, cut ->
                val cc = colorMap[cut.label] ?: cut.color
                visual.addView(TextView(this).apply {
                    text = when {
                        cut.length >= L / 12f -> "${cut.label}\n${numCm(cut.length / 10.0)}"
                        cut.length >= L / 26f -> cut.label
                        else -> ""
                    }
                    setTextColor(Color.WHITE); textSize = 8f; gravity = Gravity.CENTER; maxLines = 2
                    setTypeface(typeface, Typeface.BOLD)
                    setBackgroundColor(cc)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, cut.length.toFloat())
                })
                // petite ligne de séparation entre les pièces
                if (ci2 < bar.cuts.size - 1 || waste > 10) visual.addView(View(this).apply {
                    setBackgroundColor(Color.parseColor("#FFFFFF"))
                    layoutParams = LinearLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT)
                })
            }
            if (waste > 10) visual.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#E7ECF4"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, waste.toFloat())
            })
            detail.addView(visual)
            // Chips : pièces de CETTE barre, agrégées par longueur (102cm : 3 pcs)
            val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val grouped = LinkedHashMap<Int, Pair<Int, Int>>() // lenMm -> (count, color)
            for (cut in bar.cuts) {
                val cc = colorMap[cut.label] ?: cut.color
                val prev = grouped[cut.length]
                grouped[cut.length] = Pair((prev?.first ?: 0) + 1, prev?.second ?: cc)
            }
            for ((lenMm, pc) in grouped) {
                chipRow.addView(chip("${numCm(lenMm / 10.0)}cm : ${pc.first} pcs", pc.second))
            }
            if (waste > 10) chipRow.addView(chip("Chute ${r2(waste / 10)}cm", Color.parseColor("#6B7686")))
            detail.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false; addView(chipRow)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(5); layoutParams = lp
            })
            block.addView(detail)
        }
        card.addView(block)
    }

    private fun renderAccItems(card: LinearLayout, title: String, items: List<Triple<String, String, Int>>) {
        card.addView(sectionTitle(title))
        for (it in items) {
            val rowv = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = strokedBg(Color.parseColor("#FFFFFF"), 12, Color.parseColor("#E2E7EF"))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(6); layoutParams = lp
            }
            val txt = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            txt.addView(TextView(this).apply { text = it.first; setTextColor(Color.parseColor("#1F2733")); textSize = 14f; setTypeface(typeface, Typeface.BOLD) })
            txt.addView(TextView(this).apply { text = it.second; setTextColor(Color.parseColor("#6B7686")); textSize = 11f })
            rowv.addView(txt)
            rowv.addView(TextView(this).apply {
                text = "${it.third} pcs"; setTextColor(Color.parseColor("#2E9E5B")); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
            })
            card.addView(rowv)
        }
    }

    private fun renderLegend(card: LinearLayout, dims: JSONArray) {
        val legend = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (di in 0 until dims.length()) {
            val dm = dims.optJSONObject(di) ?: continue
            val ref = dimRef(dm, di); val col = DIM_COLORS[di % DIM_COLORS.size]
            legend.addView(chip("$ref ${numCm(d(dm, "hauteur_cm"))}×${numCm(d(dm, "largeur_cm"))}cm", col))
        }
        card.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(legend)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8); layoutParams = lp
        })
    }

    /* ════════ HELPERS UI ════════ */
    private fun note(text: String, color: String) = TextView(this).apply {
        this.text = text; setTextColor(Color.parseColor(color)); textSize = 12f
        background = strokedBg(Color.parseColor("#FFFFFF"), 12, Color.parseColor(color))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8); layoutParams = lp
    }
    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#1F2733")); textSize = 13f; setTypeface(typeface, Typeface.BOLD)
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
        background = strokedBg(Color.parseColor("#FFFFFF"), 10, color)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginEnd = dp(6); layoutParams = lp
    }
    private fun strokedBg(color: Int, radiusDp: Int, strokeColor: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(1).coerceAtLeast(1), strokeColor)
    }
}
