package com.alumanager

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.alumanager.databinding.ActivityAddOrderBinding
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AddOrderActivity : AppCompatActivity() {

    private lateinit var b: ActivityAddOrderBinding
    private val commande = mutableListOf<ProductItem>()

    private val SAVE_URL = "https://alu.xo.je/save_commande.php"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAddOrderBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }
        b.btnAddProduct.setOnClickListener { showProductWizard() }
        b.btnValidate.setOnClickListener { showClientDialog() }

        renderAll()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(v: Double): String = "%,d".format(Math.round(v)).replace(",", " ")

    /* ════════════ RENDU GLOBAL ════════════ */
    private fun renderAll() {
        b.productsContainer.removeAllViews()
        if (commande.isEmpty()) {
            b.emptyState.visibility = View.VISIBLE
            b.productsContainer.addView(b.emptyState)
            b.summaryBar.visibility = View.GONE
        } else {
            commande.forEachIndexed { i, prod ->
                b.productsContainer.addView(buildProductCard(prod, i + 1))
            }
            b.summaryBar.visibility = View.VISIBLE
        }
        updateStats()
    }

    private fun updateStats() {
        var lignes = 0; var qty = 0; var total = 0.0
        commande.forEach { p ->
            lignes += p.dimensions.size
            p.dimensions.forEach { d -> qty += d.qty; total += OrderData.rowTotal(p, d) }
        }
        b.statProduits.text = commande.size.toString()
        b.statLignes.text = lignes.toString()
        b.statQty.text = qty.toString()
        b.statTotal.text = fmt(total)
        b.grandTotal.text = "${fmt(total)} Ar"
    }

    private fun grandTotal(): Double = commande.sumOf { OrderData.productTotal(it) }

    /* ════════════ CARTE PRODUIT ════════════ */
    private fun buildProductCard(prod: ProductItem, num: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = strokedBg(Color.parseColor("#101A30"), 18, Color.parseColor("#2A3C66"))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            layoutParams = lp
            elevation = dp(2).toFloat()
        }

        // En-tete
        val headerColor = Color.parseColor(OrderData.productColor[prod.productId] ?: "#4A148C")
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedTopBg(headerColor, 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val numBadge = TextView(this).apply {
            text = num.toString()
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = roundedBg(Color.parseColor("#40FFFFFF"), 14)
            val s = dp(28)
            layoutParams = LinearLayout.LayoutParams(s, s)
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(10), 0, dp(8), 0)
        }
        titleBox.addView(TextView(this).apply {
            text = prod.label
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        })
        if (prod.config.isNotEmpty()) {
            titleBox.addView(TextView(this).apply {
                text = prod.config.values.joinToString(" · ")
                setTextColor(Color.parseColor("#E8E0FF"))
                textSize = 11f
            })
        }
        val totalBadge = TextView(this).apply {
            text = "${fmt(OrderData.productTotal(prod))} Ar"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
        }
        val delBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(12), 0, 0, 0)
            setOnClickListener { confirmDeleteProduct(prod) }
        }
        header.addView(numBadge); header.addView(titleBox)
        header.addView(totalBadge); header.addView(delBtn)
        card.addView(header)

        // Corps : tableau dimensions
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val cols = OrderData.schemas[prod.productId] ?: listOf("h", "l", "qty", "tot")

        // Ligne d'en-tete de colonnes
        val colHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cols.forEach { c ->
            colHeader.addView(TextView(this).apply {
                text = OrderData.colLabels[c]
                setTextColor(Color.parseColor("#999999"))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, colWeight(c))
            })
        }
        colHeader.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        body.addView(colHeader)

        val rowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(rowsContainer)
        card.addView(body)

        // Pied : ajouter dimension + sous-total
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(12))
        }
        val addRowBtn = TextView(this).apply {
            text = "+ Ajouter dimension"
            setTextColor(Color.parseColor("#21E6FF"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val subTotal = TextView(this).apply {
            text = "Sous-total : ${fmt(OrderData.productTotal(prod))} Ar"
            setTextColor(Color.parseColor("#EAF2FF"))
            textSize = 13f
        }
        footer.addView(addRowBtn); footer.addView(subTotal)
        card.addView(footer)

        // Rendu des dimensions existantes
        fun refreshCardTotals() {
            val t = OrderData.productTotal(prod)
            totalBadge.text = "${fmt(t)} Ar"
            subTotal.text = "Sous-total : ${fmt(t)} Ar"
            updateStats()
        }
        prod.dimensions.forEach { d ->
            rowsContainer.addView(buildDimensionRow(prod, d, cols) { refreshCardTotals() })
        }
        addRowBtn.setOnClickListener {
            val dim = Dimension()
            prod.dimensions.add(dim)
            rowsContainer.addView(buildDimensionRow(prod, dim, cols) { refreshCardTotals() })
            refreshCardTotals()
        }

        return card
    }

    private fun colWeight(c: String): Float = when (c) {
        "tot" -> 2.2f
        "pu" -> 2.2f
        else -> 1.4f
    }

    /* ════════════ LIGNE DIMENSION ════════════ */
    private fun buildDimensionRow(
        prod: ProductItem, dim: Dimension, cols: List<String>, onChange: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        var puField: EditText? = null
        var totView: TextView? = null

        fun refresh() {
            // met a jour PU auto si non force et champ non focus
            puField?.let { pf ->
                if (!pf.hasFocus() && dim.puOverride == null) {
                    val auto = OrderData.calcUnitPrice(prod, dim)
                    pf.setText(if (auto > 0) fmt(auto) else "")
                }
            }
            totView?.text = if (OrderData.unitPrice(prod, dim) > 0)
                "${fmt(OrderData.rowTotal(prod, dim))} Ar" else "—"
            onChange()
        }

        cols.forEach { c ->
            when (c) {
                "h", "l", "p", "qty" -> {
                    val et = numField(when (c) {
                        "h" -> if (dim.h > 0) dim.h.toInt().toString() else ""
                        "l" -> if (dim.l > 0) dim.l.toInt().toString() else ""
                        "p" -> if (dim.p > 0) dim.p.toInt().toString() else ""
                        else -> dim.qty.toString()
                    }, c.uppercase())
                    et.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, colWeight(c))
                    et.addTextChangedListener(simpleWatcher { txt ->
                        val n = txt.toDoubleOrNull() ?: 0.0
                        when (c) {
                            "h" -> { dim.h = n; dim.puOverride = null }
                            "l" -> { dim.l = n; dim.puOverride = null }
                            "p" -> { dim.p = n; dim.puOverride = null }
                            "qty" -> dim.qty = (txt.toIntOrNull() ?: 1).coerceAtLeast(1)
                        }
                        refresh()
                    })
                    row.addView(et)
                }
                "pu" -> {
                    val auto = OrderData.calcUnitPrice(prod, dim)
                    val et = numField(if (auto > 0) fmt(auto) else "", "— Ar")
                    et.inputType = InputType.TYPE_CLASS_NUMBER
                    et.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, colWeight(c))
                    et.addTextChangedListener(simpleWatcher { txt ->
                        if (et.hasFocus()) {
                            val raw = txt.replace(Regex("[^0-9.]"), "")
                            val n = raw.toDoubleOrNull()
                            dim.puOverride = if (n != null && n >= 0) n else null
                            totView?.text = if (OrderData.unitPrice(prod, dim) > 0)
                                "${fmt(OrderData.rowTotal(prod, dim))} Ar" else "—"
                            onChange()
                        }
                    })
                    puField = et
                    row.addView(et)
                }
                "tot" -> {
                    val tv = TextView(this).apply {
                        text = if (OrderData.unitPrice(prod, dim) > 0)
                            "${fmt(OrderData.rowTotal(prod, dim))} Ar" else "—"
                        setTextColor(Color.parseColor("#27FFC4"))
                        textSize = 13f
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.END
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, colWeight(c))
                    }
                    totView = tv
                    row.addView(tv)
                }
            }
        }

        // Bouton supprimer ligne
        row.addView(TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#CC3333"))
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                prod.dimensions.remove(dim)
                (row.parent as? ViewGroup)?.removeView(row)
                onChange()
            }
        })

        return row
    }

    private fun numField(value: String, hint: String): EditText {
        return EditText(this).apply {
            setText(value)
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 13f
            setTextColor(Color.parseColor("#EAF2FF"))
            setHintTextColor(Color.parseColor("#5A688F"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = strokedBg(Color.parseColor("#0B1326"), 10, Color.parseColor("#243456"))
        }
    }

    private fun simpleWatcher(onText: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { onText(s?.toString() ?: "") }
    }

    private fun confirmDeleteProduct(prod: ProductItem) {
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setMessage("Supprimer \"${prod.label}\" ?")
            .setPositiveButton("Supprimer") { _, _ -> commande.remove(prod); renderAll() }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /* ════════════ ASSISTANT PRODUIT ════════════ */
    private var wizPid: String? = null
    private var wizStep = -1
    private val wizRaw = HashMap<String, String>()
    private val wizLbl = LinkedHashMap<String, String>()
    private var wizDialog: AlertDialog? = null

    private fun showProductWizard() {
        wizPid = null; wizStep = -1; wizRaw.clear(); wizLbl.clear()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        wizDialog = AlertDialog.Builder(this, R.style.NeonDialog).setView(wrapScroll(container)).create()
        renderWizardStep0(container)
        wizDialog?.show()
    }

    private fun wrapScroll(v: View): View {
        val sc = android.widget.ScrollView(this)
        sc.addView(v)
        return sc
    }

    private fun renderWizardStep0(container: LinearLayout) {
        container.removeAllViews()
        container.addView(wizTitle("Choisissez le type de produit"))
        OrderData.productLabels.forEach { (id, label) ->
            container.addView(optionCard(label, "") {
                wizPid = id
                wizRaw.clear(); wizLbl.clear()
                wizStep = OrderData.visibleIndices(id, wizRaw).firstOrNull() ?: 0
                renderWizardCurrent(container)
            })
        }
    }

    private fun renderWizardCurrent(container: LinearLayout) {
        val pid = wizPid ?: return
        val steps = OrderData.steps[pid] ?: return
        val step = steps[wizStep]
        val vis = OrderData.visibleIndices(pid, wizRaw)
        val pos = vis.indexOf(wizStep) + 1

        container.removeAllViews()
        container.addView(wizTitle("${step.title}  ($pos/${vis.size})"))

        step.opts.forEach { o ->
            val selected = wizRaw[step.key] == o.v
            if (step.type == "colors") {
                container.addView(colorCard(o, selected) { pickOption(container, step, o) })
            } else {
                container.addView(optionCard(o.l, o.d, selected) { pickOption(container, step, o) })
            }
        }

        // Bouton retour
        container.addView(Button(this).apply {
            text = "‹ Retour"
            background = strokedBg(Color.parseColor("#14203F"), 12, Color.parseColor("#243456"))
            setTextColor(Color.parseColor("#8A97C2"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            layoutParams = lp
            setOnClickListener {
                val prev = OrderData.prevVisible(pid, wizStep, wizRaw)
                if (prev == -1) { wizPid = null; wizStep = -1; renderWizardStep0(container) }
                else { wizStep = prev; renderWizardCurrent(container) }
            }
        })
    }

    private fun pickOption(container: LinearLayout, step: Step, o: StepOption) {
        val pid = wizPid ?: return
        wizRaw[step.key] = o.v
        wizLbl[step.key] = o.l
        // efface les etapes conditionnelles dependantes
        OrderData.steps[pid]?.forEach { s ->
            if (s.cond?.key == step.key) { wizRaw.remove(s.key); wizLbl.remove(s.key) }
        }
        val nxt = OrderData.nextVisible(pid, wizStep, wizRaw)
        if (nxt == -1) finalizeProduct()
        else { wizStep = nxt; renderWizardCurrent(container) }
    }

    private fun finalizeProduct() {
        val pid = wizPid ?: return
        val prod = ProductItem(
            productId = pid,
            label = OrderData.productLabels[pid] ?: pid,
            config = LinkedHashMap(wizLbl),
            configRaw = HashMap(wizRaw)
        )
        prod.dimensions.add(Dimension())   // une ligne par defaut
        commande.add(prod)
        wizDialog?.dismiss()
        renderAll()
        Toast.makeText(this, "${prod.label} ajoute", Toast.LENGTH_SHORT).show()
    }

    private fun wizTitle(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#EAF2FF"))
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(10))
    }

    private fun optionCard(title: String, desc: String, selected: Boolean = false, onClick: () -> Unit): View {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = strokedBg(
                if (selected) Color.parseColor("#1B2A4F") else Color.parseColor("#0E1730"), 14,
                if (selected) Color.parseColor("#8B5CFF") else Color.parseColor("#243456")
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
        ll.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#EAF2FF"))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        })
        if (desc.isNotEmpty()) ll.addView(TextView(this).apply {
            text = desc
            setTextColor(Color.parseColor("#8A97C2"))
            textSize = 12f
        })
        return ll
    }

    private fun colorCard(o: StepOption, selected: Boolean, onClick: () -> Unit): View {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = strokedBg(
                if (selected) Color.parseColor("#1B2A4F") else Color.parseColor("#0E1730"), 14,
                if (selected) Color.parseColor("#8B5CFF") else Color.parseColor("#243456")
            )
            setPadding(dp(14), dp(10), dp(14), dp(10))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
        ll.addView(View(this).apply {
            background = roundedBg(Color.parseColor(o.color ?: "#CCCCCC"), 12)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        })
        ll.addView(TextView(this).apply {
            text = "   ${o.l}"
            setTextColor(Color.parseColor("#EAF2FF"))
            textSize = 15f
        })
        return ll
    }

    /* ════════════ CLIENT + ENREGISTREMENT ════════════ */
    private fun showClientDialog() {
        if (commande.isEmpty()) { Toast.makeText(this, "Commande vide", Toast.LENGTH_SHORT).show(); return }

        val total = grandTotal()
        val pad = dp(16)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, 0)
        }
        v.addView(label("Total : ${fmt(total)} Ar"))
        val nom = clientField("Nom du client")
        val lieu = clientField("Lieu / Adresse")
        val dateCmd = dateField("Date commande", today())
        val dateLiv = dateField("Date livraison", "")
        val avance = clientField("Avance (Ar)").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        v.addView(nom); v.addView(lieu); v.addView(dateCmd); v.addView(dateLiv); v.addView(avance)

        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("Information client")
            .setView(wrapScroll(v))
            .setPositiveButton("Confirmer", null)
            .setNegativeButton("Annuler", null)
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (nom.text.isBlank() || lieu.text.isBlank() ||
                            dateCmd.text.isBlank() || dateLiv.text.isBlank()
                        ) {
                            Toast.makeText(this@AddOrderActivity, "Remplissez tous les champs", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val av = avance.text.toString().toDoubleOrNull() ?: 0.0
                        dismiss()
                        saveOrder(
                            nom.text.toString().trim(), lieu.text.toString().trim(),
                            dateCmd.text.toString(), dateLiv.text.toString(), total, av
                        )
                    }
                }
            }.show()
    }

    private fun saveOrder(
        nom: String, lieu: String, dateCmd: String, dateLiv: String, total: Double, avance: Double
    ) {
        val reste = (total - avance).coerceAtLeast(0.0)
        val payload = JSONObject().apply {
            put("client_nom", nom)
            put("client_lieu", lieu)
            put("date_commande", dateCmd)
            put("date_livraison", dateLiv)
            put("total", total)
            put("avance", avance)
            put("reste", reste)
            put("produits", JSONArray().apply {
                commande.forEach { p ->
                    put(JSONObject().apply {
                        put("productId", p.productId)
                        put("label", p.label)
                        put("colorClass", p.productId)
                        put("basePrice", OrderData.unitPrice(p, p.dimensions.firstOrNull() ?: Dimension()))
                        put("config", JSONObject().apply { p.config.forEach { (k, vv) -> put(k, vv) } })
                        put("dimensions", JSONArray().apply {
                            p.dimensions.forEach { d ->
                                put(JSONObject().apply {
                                    put("id", d.id); put("h", d.h); put("l", d.l)
                                    put("p", d.p); put("qty", d.qty)
                                })
                            }
                        })
                    })
                }
            })
        }

        val progress = AlertDialog.Builder(this, R.style.NeonDialog)
            .setView(TextView(this).apply { text = "  Enregistrement en cours..."; setTextColor(Color.parseColor("#EAF2FF")); setPadding(dp(20), dp(30), dp(20), dp(30)) })
            .setCancelable(false).create()
        progress.show()

        Thread {
            var ok = false; var reference = ""; var err = ""
            try {
                val body = payload.toString()
                var resp = postJson(SAVE_URL, body, null)
                // Challenge anti-bot de l'hebergeur (InfinityFree / aes.js) :
                // on resout l'AES, on pose le cookie __test, puis on renvoie (2 essais max).
                repeat(2) {
                    if (isChallenge(resp.second)) {
                        val cookie = solveAesChallenge(resp.second)
                        if (cookie != null) resp = postJson(SAVE_URL, body, cookie)
                    }
                }
                // Certains hebergeurs prefixent la reponse de notices/HTML : on extrait le JSON.
                val jsonStr = extractJson(resp.second)
                if (jsonStr == null) {
                    val snippet = resp.second.replace(Regex("<[^>]*>"), " ")
                        .replace(Regex("\\s+"), " ").trim().take(180)
                    err = "Serveur (HTTP ${resp.first}) : " +
                        (if (snippet.isBlank()) "reponse vide" else snippet)
                } else {
                    val json = JSONObject(jsonStr)
                    ok = json.optBoolean("success", false)
                    reference = json.optString("reference", "")
                    err = json.optString("error", json.optString("message", "Erreur serveur"))
                }
            } catch (e: Exception) {
                err = e.localizedMessage ?: "Erreur reseau"
            }
            runOnUiThread {
                progress.dismiss()
                if (ok) showSuccess(reference, nom, total, avance, reste)
                else AlertDialog.Builder(this, R.style.NeonDialog)
                    .setTitle("Erreur").setMessage(err).setPositiveButton("OK", null).show()
            }
        }.start()
    }

    private fun showSuccess(ref: String, nom: String, total: Double, avance: Double, reste: Double) {
        val msg = buildString {
            append("Reference : $ref\n\n")
            append("👤 Client : $nom\n")
            append("💰 Total : ${fmt(total)} Ar\n")
            append("✅ Avance : ${fmt(avance)} Ar\n")
            append("⏳ Reste : ${fmt(reste)} Ar")
        }
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("Commande enregistree !")
            .setMessage(msg)
            .setPositiveButton("Nouvelle commande") { _, _ -> commande.clear(); renderAll() }
            .setNegativeButton("Fermer") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#21E6FF"))
        textSize = 15f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 0, 0, dp(8))
    }

    private fun clientField(hint: String) = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.parseColor("#EAF2FF"))
        setHintTextColor(Color.parseColor("#5A688F"))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = strokedBg(Color.parseColor("#0B1326"), 12, Color.parseColor("#243456"))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        layoutParams = lp
    }

    private fun dateField(hint: String, initial: String): EditText {
        val et = clientField(hint)
        et.setText(initial)
        et.isFocusable = false
        et.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                et.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        return et
    }

    private fun today(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
    }

    /* ════════════ FORMES ════════════ */
    private fun roundedBg(color: Int, radiusDp: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun strokedBg(color: Int, radiusDp: Int, strokeColor: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1).coerceAtLeast(1), strokeColor)
    }

    private fun roundedTopBg(color: Int, radiusDp: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        val r = dp(radiusDp).toFloat()
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    }

    /* ════════════ RESEAU + CHALLENGE ANTI-BOT ════════════ */
    private fun postJson(urlStr: String, body: String, cookie: String?): Pair<Int, String> {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20000; readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
            )
            if (cookie != null) setRequestProperty("Cookie", "__test=$cookie")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to text
    }

    private fun isChallenge(s: String): Boolean =
        s.contains("toNumbers(") || s.contains("slowAES") || s.contains("aes.js")

    /** Extrait le 1er objet JSON valide d'une reponse (ignore HTML/notices autour). */
    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val cand = text.substring(start, end + 1)
        return try { JSONObject(cand); cand } catch (e: Exception) { null }
    }

    /** Resout le challenge JS (aes.js) : cookie __test = hex(AES-CBC-decrypt(c, key=a, iv=b)). */
    private fun solveAesChallenge(html: String): String? {
        val nums = Regex("toNumbers\\(\"([0-9a-fA-F]+)\"\\)")
            .findAll(html).map { it.groupValues[1] }.toList()
        if (nums.size < 3) return null
        return try {
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(hexToBytes(nums[0]), "AES"),
                IvParameterSpec(hexToBytes(nums[1]))
            )
            bytesToHex(cipher.doFinal(hexToBytes(nums[2])))
        } catch (e: Exception) {
            null
        }
    }

    private fun hexToBytes(s: String): ByteArray =
        ByteArray(s.length / 2) {
            ((Character.digit(s[it * 2], 16) shl 4) + Character.digit(s[it * 2 + 1], 16)).toByte()
        }

    private fun bytesToHex(b: ByteArray): String =
        b.joinToString("") { "%02x".format(it) }
}
