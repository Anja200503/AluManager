package com.alumanager

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.alumanager.databinding.ActivityCommandeBinding
import org.json.JSONArray
import org.json.JSONObject

class CommandeActivity : AppCompatActivity() {

    private lateinit var b: ActivityCommandeBinding
    private val GET_URL = "https://alu.xo.je/get_commandes.php"
    private val DELETE_URL = "https://alu.xo.je/delete_commande.php"

    private var all = JSONArray()

    private val shortType = mapOf(
        "fenetre" to "Fen", "porte" to "Pte", "lavarangana" to "Lav",
        "rideau" to "Rid", "vitrine" to "Vit"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCommandeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }
        b.btnRefresh.setOnClickListener { load() }
        b.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun afterTextChanged(s: Editable?) { render() }
        })
        load()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(v: Double): String = "%,d".format(Math.round(v)).replace(",", " ")
    private fun d(o: JSONObject, key: String): Double = o.optString(key, "0").toDoubleOrNull() ?: 0.0
    private fun fmtDate(s: String?): String {
        if (s.isNullOrBlank()) return "—"
        val p = s.split("-")
        return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else s
    }

    /* ════════════ CHARGEMENT ════════════ */
    private fun load() {
        b.listContainer.removeAllViews()
        b.emptyState.text = "Chargement…"
        b.emptyState.visibility = View.VISIBLE
        b.listContainer.addView(b.emptyState)
        Thread {
            var err: String? = null
            var commandes = JSONArray()
            var stats = JSONObject()
            try {
                val body = Net.get(GET_URL)
                val js = Net.extractJson(body)
                if (js == null) { err = "Réponse serveur invalide" }
                else {
                    val o = JSONObject(js)
                    if (!o.optBoolean("success", false)) err = o.optString("error", "Erreur serveur")
                    else { commandes = o.optJSONArray("commandes") ?: JSONArray(); stats = o.optJSONObject("stats") ?: JSONObject() }
                }
            } catch (e: Exception) { err = e.localizedMessage ?: "Erreur réseau" }
            runOnUiThread {
                if (err != null) {
                    b.emptyState.text = "⚠️ $err"
                    b.emptyState.visibility = View.VISIBLE
                } else {
                    all = commandes
                    b.statNb.text = stats.optString("nb", "0")
                    b.statCA.text = fmt(d(stats, "ca"))
                    b.statAv.text = fmt(d(stats, "av"))
                    b.statRe.text = fmt(d(stats, "re"))
                    render()
                }
            }
        }.start()
    }

    /* ════════════ RENDU LISTE ════════════ */
    private fun render() {
        val q = b.searchInput.text.toString().lowercase().trim()
        b.listContainer.removeAllViews()
        val filtered = mutableListOf<JSONObject>()
        for (i in 0 until all.length()) {
            val c = all.optJSONObject(i) ?: continue
            if (q.isEmpty() ||
                c.optString("client_nom").lowercase().contains(q) ||
                c.optString("reference").lowercase().contains(q) ||
                c.optString("client_lieu").lowercase().contains(q)
            ) filtered.add(c)
        }
        if (filtered.isEmpty()) {
            b.emptyState.text = if (all.length() == 0) "📋 Aucune commande" else "Aucun résultat"
            b.emptyState.visibility = View.VISIBLE
            b.listContainer.addView(b.emptyState)
            return
        }
        filtered.forEach { b.listContainer.addView(buildCard(it)) }
    }

    private fun buildCard(c: JSONObject): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.glass_card)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12)
            layoutParams = lp
        }
        // En-tete : reference + statut
        val head = row()
        head.addView(TextView(this).apply {
            text = "🔖 ${c.optString("reference")}"
            setTextColor(Color.parseColor("#21E6FF"))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        head.addView(TextView(this).apply {
            text = c.optString("statut", "confirmee")
            setTextColor(Color.parseColor("#8A97C2"))
            textSize = 11f
        })
        card.addView(head)
        // Client + lieu
        card.addView(line("👤 ${c.optString("client_nom")}", "#EAF2FF", 15f, true, dp(8)))
        card.addView(line("📍 ${c.optString("client_lieu")}", "#8A97C2", 13f, false, dp(2)))
        // Meta
        val np = c.optString("nb_produits", "0")
        card.addView(line("📅 ${fmtDate(c.optString("date_commande"))}   🚚 ${fmtDate(c.optString("date_livraison"))}", "#8A97C2", 12f, false, dp(8)))
        card.addView(line("📦 $np produit(s)   💰 ${fmt(d(c, "total_ar"))} Ar", "#EAF2FF", 13f, false, dp(4)))
        // Badge reste
        val reste = d(c, "reste_ar")
        card.addView(TextView(this).apply {
            text = if (reste <= 0) "✅ Soldé" else "⏳ Reste : ${fmt(reste)} Ar"
            setTextColor(if (reste <= 0) Color.parseColor("#27FFC4") else Color.parseColor("#FFC34D"))
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
        })
        // Footer : boutons (2 lignes)
        val f1 = row().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(14) }
        f1.addView(actionBtn("📄 Détail", "#21E6FF") { showDetail(c) })
        f1.addView(spacer())
        f1.addView(actionBtn("🧾 Facture PDF", "#27FFC4") { showInvoice(c) })
        card.addView(f1)
        val f2 = row().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8) }
        f2.addView(actionBtn("🔧 Zavatra", "#FFC34D") { openZavatra(c) })
        f2.addView(spacer())
        f2.addView(actionBtn("🗑️ Supprimer", "#FF4D9D") { showDeleteConfirm(c) })
        card.addView(f2)
        return card
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    private fun spacer() = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) }
    private fun line(t: String, color: String, size: Float, bold: Boolean, top: Int): TextView =
        TextView(this).apply {
            text = t; setTextColor(Color.parseColor(color)); textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = top; layoutParams = lp
        }
    private fun actionBtn(label: String, color: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(color))
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = strokedBg(Color.parseColor("#0E1730"), 12, Color.parseColor(color))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }

    /* ════════════ SUPPRESSION ════════════ */
    private fun showDeleteConfirm(c: JSONObject) {
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("Supprimer la commande")
            .setMessage("Supprimer « ${c.optString("reference")} » ?\nCette action est définitive.")
            .setPositiveButton("Supprimer") { _, _ -> doDelete(c.optString("id")) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun doDelete(id: String) {
        val progress = AlertDialog.Builder(this, R.style.NeonDialog)
            .setView(TextView(this).apply {
                text = "  Suppression…"; setTextColor(Color.parseColor("#EAF2FF")); setPadding(dp(20), dp(28), dp(20), dp(28))
            }).setCancelable(false).create()
        progress.show()
        Thread {
            var ok = false; var err = ""
            try {
                val body = Net.post(DELETE_URL, JSONObject().put("id", id.toIntOrNull() ?: 0).toString())
                val js = Net.extractJson(body)
                if (js != null) {
                    val o = JSONObject(js); ok = o.optBoolean("success", false); err = o.optString("error", "Erreur")
                } else err = "Réponse invalide"
            } catch (e: Exception) { err = e.localizedMessage ?: "Erreur réseau" }
            runOnUiThread {
                progress.dismiss()
                if (ok) load()
                else AlertDialog.Builder(this, R.style.NeonDialog)
                    .setTitle("Erreur").setMessage(err).setPositiveButton("OK", null).show()
            }
        }.start()
    }

    /* ════════════ DETAIL ════════════ */
    private fun showDetail(c: JSONObject) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        root.addView(TextView(this).apply {
            text = "👤 ${c.optString("client_nom")}  •  📍 ${c.optString("client_lieu")}"
            setTextColor(Color.parseColor("#8A97C2")); textSize = 12f
        })
        val produits = c.optJSONArray("produits") ?: JSONArray()
        for (i in 0 until produits.length()) {
            val p = produits.optJSONObject(i) ?: continue
            val pc = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = strokedBg(Color.parseColor("#0E1730"), 14, Color.parseColor("#243456"))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(10); layoutParams = lp
            }
            pc.addView(TextView(this).apply {
                text = "${i + 1}. ${p.optString("product_label")}"
                setTextColor(Color.parseColor("#EAF2FF")); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
            })
            val cfg = p.optJSONObject("config_parsed")
            if (cfg != null && cfg.length() > 0) {
                val parts = cfg.keys().asSequence().map { cfg.optString(it) }.filter { it.isNotEmpty() }.joinToString(" · ")
                pc.addView(TextView(this).apply { text = parts; setTextColor(Color.parseColor("#8A97C2")); textSize = 11f })
            }
            val dims = p.optJSONArray("dimensions") ?: JSONArray()
            for (j in 0 until dims.length()) {
                val dm = dims.optJSONObject(j) ?: continue
                val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm"); val pr = d(dm, "profondeur_cm")
                val qty = dm.optString("quantite", "1")
                val dimTxt = buildString {
                    if (h > 0) append("H${h.toInt()} ")
                    if (l > 0) append("L${l.toInt()} ")
                    if (pr > 0) append("P${pr.toInt()} ")
                    append("× $qty")
                }
                pc.addView(TextView(this).apply {
                    text = "   • $dimTxt  =  ${fmt(d(dm, "prix_total"))} Ar"
                    setTextColor(Color.parseColor("#C7D2F2")); textSize = 12f
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = dp(4); layoutParams = lp
                })
            }
            pc.addView(TextView(this).apply {
                text = "Sous-total : ${fmt(d(p, "total_ar"))} Ar"
                setTextColor(Color.parseColor("#27FFC4")); textSize = 13f; setTypeface(typeface, Typeface.BOLD)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(6); layoutParams = lp
            })
            root.addView(pc)
        }
        root.addView(TextView(this).apply {
            text = "TOTAL : ${fmt(d(c, "total_ar"))} Ar\nAvance : ${fmt(d(c, "avance_ar"))} Ar   Reste : ${fmt(d(c, "reste_ar"))} Ar"
            setTextColor(Color.parseColor("#21E6FF")); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12); layoutParams = lp
        })
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("Détail ${c.optString("reference")}")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Fermer", null)
            .show()
    }

    /* ════════════ FACTURE PDF ════════════ */
    private fun showInvoice(c: JSONObject) {
        try {
            val file = InvoicePdf.generate(this, c)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(Intent.createChooser(view, "Ouvrir la facture"))
            } catch (e: Exception) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "Partager la facture"))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur PDF : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /* ════════════ ZAVATRA (nécessaire de pièces) ════════════ */
    private fun openZavatra(c: JSONObject) {
        startActivity(Intent(this, ZavatraActivity::class.java).putExtra("cmd", c.toString()))
    }

    /* ════════════ COUPE (Gilmore-Gomory) ════════════ */
    private fun showCoupe(c: JSONObject) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val barInput = coupeField("Longueur barre (cm)", "600")
        val kerfInput = coupeField("Trait de scie (mm)", "3")
        root.addView(barInput); root.addView(kerfInput)
        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12); layoutParams = lp
        }
        root.addView(results)

        val calc = TextView(this).apply {
            text = "Calculer la coupe"
            setTextColor(Color.parseColor("#08101F")); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.btn_primary)
            setPadding(0, dp(14), 0, dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12); layoutParams = lp
        }
        root.addView(calc)

        calc.setOnClickListener {
            val barCm = barInput.text.toString().toIntOrNull() ?: 600
            val kerf = kerfInput.text.toString().toIntOrNull() ?: 3
            val pieces = extractPieces(c)
            results.removeAllViews()
            if (pieces.isEmpty()) {
                results.addView(line("⚠️ Aucune dimension exploitable dans cette commande.", "#FFC34D", 13f, false, 0))
                return@setOnClickListener
            }
            results.addView(line("Calcul Gilmore-Gomory…", "#8A97C2", 13f, false, 0))
            Thread {
                var res: CuttingStock.Result? = null; var err: String? = null
                try { res = CuttingStock.solve(pieces, barCm * 10, kerf) }
                catch (e: Exception) { err = e.localizedMessage }
                runOnUiThread {
                    results.removeAllViews()
                    if (err != null) results.addView(line("❌ $err", "#FF4D9D", 13f, false, 0))
                    else renderCoupe(results, res!!)
                }
            }.start()
        }

        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("Coupe ${c.optString("reference")}")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun coupeField(hint: String, value: String) = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = InputType.TYPE_CLASS_NUMBER
        setTextColor(Color.parseColor("#EAF2FF")); setHintTextColor(Color.parseColor("#5A688F"))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = strokedBg(Color.parseColor("#0B1326"), 12, Color.parseColor("#243456"))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8); layoutParams = lp
    }

    private fun extractPieces(c: JSONObject): List<CuttingStock.Piece> {
        val pieces = mutableListOf<CuttingStock.Piece>()
        val produits = c.optJSONArray("produits") ?: JSONArray()
        for (i in 0 until produits.length()) {
            val p = produits.optJSONObject(i) ?: continue
            val type = shortType[p.optString("product_id")] ?: p.optString("product_id").take(3)
            val dims = p.optJSONArray("dimensions") ?: JSONArray()
            for (j in 0 until dims.length()) {
                val dm = dims.optJSONObject(j) ?: continue
                val qty = dm.optString("quantite", "1").toIntOrNull() ?: 1
                val h = Math.round(d(dm, "hauteur_cm") * 10).toInt()
                val l = Math.round(d(dm, "largeur_cm") * 10).toInt()
                if (h > 10) pieces.add(CuttingStock.Piece(h, qty, "$type H${d(dm, "hauteur_cm").toInt()}cm"))
                if (l > 10) pieces.add(CuttingStock.Piece(l, qty, "$type L${d(dm, "largeur_cm").toInt()}cm"))
            }
        }
        return pieces
    }

    private fun renderCoupe(container: LinearLayout, res: CuttingStock.Result) {
        val s = res.stats
        // Stats
        val statsRow = row()
        statsRow.addView(coupeStat(s.nbBarres.toString(), "Barres", "#21E6FF"))
        statsRow.addView(coupeStat(s.nbPieces.toString(), "Pièces", "#EAF2FF"))
        statsRow.addView(coupeStat("${s.pctEfficacite}%", "Efficacité", "#27FFC4"))
        statsRow.addView(coupeStat("${s.pctChute}%", "Chute", "#FF4D9D"))
        container.addView(statsRow)
        container.addView(line("${s.nbPatrons} patron(s) • barre ${s.barLength / 10}cm • trait ${s.kerf}mm", "#8A97C2", 11f, false, dp(6)))

        val L = s.barLength.toFloat()
        res.bars.forEachIndexed { idx, bar ->
            val waste = if (bar.realWaste > 0) bar.realWaste else bar.waste
            container.addView(line("Barre ${idx + 1}  •  ${bar.cuts.size} pièce(s)  •  chute ${Math.round(waste)}mm", "#C7D2F2", 12f, false, dp(12)))
            val visual = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = L
                background = strokedBg(Color.parseColor("#0B1326"), 6, Color.parseColor("#243456"))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26))
                lp.topMargin = dp(4); layoutParams = lp
            }
            for (cut in bar.cuts) {
                visual.addView(TextView(this).apply {
                    text = if (cut.length >= 600) cut.length.toString() else ""
                    setTextColor(Color.WHITE); textSize = 8f; gravity = Gravity.CENTER
                    setBackgroundColor(cut.color)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, cut.length.toFloat())
                })
                // trait de scie
                visual.addView(View(this).apply {
                    setBackgroundColor(Color.parseColor("#060912"))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, s.kerf.toFloat())
                })
            }
            if (waste > 10) visual.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#1A2440"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, waste.toFloat())
            })
            container.addView(visual)
        }
    }

    private fun coupeStat(value: String, label: String, color: String): View {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        ll.addView(TextView(this).apply {
            text = value; setTextColor(Color.parseColor(color)); textSize = 17f; setTypeface(typeface, Typeface.BOLD)
        })
        ll.addView(TextView(this).apply { text = label; setTextColor(Color.parseColor("#8A97C2")); textSize = 10f })
        return ll
    }

    /* ════════════ FORMES ════════════ */
    private fun strokedBg(color: Int, radiusDp: Int, strokeColor: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1).coerceAtLeast(1), strokeColor)
    }
}
