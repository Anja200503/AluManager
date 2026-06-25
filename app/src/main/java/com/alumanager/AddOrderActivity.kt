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
import android.widget.ImageView
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
    private val DELETE_URL = "https://alu.xo.je/delete_commande.php"

    private var editId = 0
    private var editNom = ""
    private var editLieu = ""
    private var editDateCmd = ""
    private var editDateLiv = ""
    private var editAvance = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAddOrderBinding.inflate(layoutInflater)
        setContentView(b.root)

        loadPrices()
        intent.getStringExtra("edit")?.let { loadForEdit(it) }
        if (editId > 0) b.screenTitle.text = "Modifier la commande"
        b.btnBack.setOnClickListener { finish() }
        b.btnAddProduct.setOnClickListener { showProductWizard() }
        b.btnValidate.setOnClickListener { showClientDialog() }
        b.btnSettings.setOnClickListener { showPriceSettings() }

        renderAll()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(v: Double): String = "%,d".format(Math.round(v)).replace(",", " ")

    /* ════════════ PARAMÈTRES DE PRIX ════════════ */
    private val FEN_LBL = linkedMapOf(
        "coulissante_promotion" to "Coulissante Promotion",
        "coulissante_deuxieme" to "Coulissante 2e Choix",
        "coulissante_premier" to "Coulissante 1er Choix",
        "ouvrante" to "Ouvrante", "projetant" to "Projetant",
        "naco" to "Naco", "fixe" to "Fixe"
    )
    private val SUR_LBL = linkedMapOf(
        "antelio_4mm" to "Surplus Antelio 4mm",
        "antelio_5mm" to "Surplus Antelio 5mm",
        "clair_5mm" to "Surplus Clair 5mm"
    )
    private val AUT_LBL = linkedMapOf(
        "lavarangana" to "Lavarangana Inox", "porte" to "Porte",
        "rideau" to "Rideau Métallique", "vitrine" to "Vitrine Alu"
    )
    private val FEN_DEF = linkedMapOf(
        "coulissante_promotion" to 70000.0, "coulissante_deuxieme" to 85000.0,
        "coulissante_premier" to 100000.0, "ouvrante" to 90000.0,
        "projetant" to 95000.0, "naco" to 80000.0, "fixe" to 65000.0
    )
    private val SUR_DEF = linkedMapOf("antelio_4mm" to 0.0, "antelio_5mm" to 0.0, "clair_5mm" to 0.0)
    private val AUT_DEF = linkedMapOf("lavarangana" to 45000.0, "porte" to 120000.0, "rideau" to 95000.0, "vitrine" to 150000.0)

    private fun prefsPrix() = getSharedPreferences("alu_prices", MODE_PRIVATE)

    private fun loadPrices() {
        val p = prefsPrix()
        FEN_LBL.keys.forEach { k -> p.getFloat("fbp_$k", -1f).let { if (it >= 0) OrderData.fenetreBasePrices[k] = it.toDouble() } }
        SUR_LBL.keys.forEach { k -> p.getFloat("fsur_$k", -1f).let { if (it >= 0) OrderData.fenetreSurcharges[k] = it.toDouble() } }
        AUT_LBL.keys.forEach { k -> p.getFloat("abp_$k", -1f).let { if (it >= 0) OrderData.autresBasePrice[k] = it.toDouble() } }
    }

    private fun showPriceSettings() {
        val fields = LinkedHashMap<String, EditText>()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }

        fun section(t: String) = root.addView(TextView(this).apply {
            text = t; setTextColor(Color.parseColor("#0E86C9")); textSize = 13f; setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(14); layoutParams = lp
        })
        fun row(key: String, label: String, value: Double) {
            root.addView(TextView(this).apply {
                text = label; setTextColor(Color.parseColor("#6A7488")); textSize = 12f
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(8); layoutParams = lp
            })
            val et = EditText(this).apply {
                setText(Math.round(value).toString())
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextColor(Color.parseColor("#18202E")); setHintTextColor(Color.parseColor("#97A0B2"))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = strokedBg(Color.parseColor("#F2F5FB"), 12, Color.parseColor("#D7DDEA"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            root.addView(et); fields[key] = et
        }

        section("FENÊTRE — Prix de base (Ar)")
        FEN_LBL.forEach { (k, lbl) -> row("fbp_$k", lbl, OrderData.fenetreBasePrices[k] ?: 0.0) }
        section("FENÊTRE — Surplus vitrage (Ar)")
        SUR_LBL.forEach { (k, lbl) -> row("fsur_$k", lbl, OrderData.fenetreSurcharges[k] ?: 0.0) }
        section("AUTRES PRODUITS (Ar)")
        AUT_LBL.forEach { (k, lbl) -> row("abp_$k", lbl, OrderData.autresBasePrice[k] ?: 0.0) }

        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("⚙️ Paramètres de prix")
            .setView(wrapScroll(root))
            .setPositiveButton("Enregistrer") { _, _ ->
                val e = prefsPrix().edit()
                FEN_LBL.keys.forEach { k ->
                    val v = fields["fbp_$k"]?.text?.toString()?.toDoubleOrNull() ?: return@forEach
                    OrderData.fenetreBasePrices[k] = v; e.putFloat("fbp_$k", v.toFloat())
                }
                SUR_LBL.keys.forEach { k ->
                    val v = fields["fsur_$k"]?.text?.toString()?.toDoubleOrNull() ?: return@forEach
                    OrderData.fenetreSurcharges[k] = v.coerceAtLeast(0.0); e.putFloat("fsur_$k", v.toFloat())
                }
                AUT_LBL.keys.forEach { k ->
                    val v = fields["abp_$k"]?.text?.toString()?.toDoubleOrNull() ?: return@forEach
                    OrderData.autresBasePrice[k] = v; e.putFloat("abp_$k", v.toFloat())
                }
                e.apply()
                renderAll()
                Toast.makeText(this, "Prix mis à jour ✓", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Réinitialiser") { _, _ ->
                FEN_DEF.forEach { (k, v) -> OrderData.fenetreBasePrices[k] = v }
                SUR_DEF.forEach { (k, v) -> OrderData.fenetreSurcharges[k] = v }
                AUT_DEF.forEach { (k, v) -> OrderData.autresBasePrice[k] = v }
                prefsPrix().edit().clear().apply()
                renderAll()
                Toast.makeText(this, "Prix réinitialisés", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

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
            background = strokedBg(Color.parseColor("#FFFFFF"), 18, Color.parseColor("#D7DDEA"))
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
        header.addView(numBadge)
        val pimg = productImageRes(prod.productId)
        if (pimg != 0) header.addView(ImageView(this).apply {
            val s = dp(44)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { leftMargin = dp(8) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = circleBg(Color.parseColor("#33FFFFFF"), Color.parseColor("#80FFFFFF"))
            clipToOutline = true
            setImageResource(pimg)
        })
        header.addView(titleBox)
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
            setTextColor(Color.parseColor("#0E86C9"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val subTotal = TextView(this).apply {
            text = "Sous-total : ${fmt(OrderData.productTotal(prod))} Ar"
            setTextColor(Color.parseColor("#18202E"))
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
                        "h" -> if (dim.h > 0) numIn(dim.h) else ""
                        "l" -> if (dim.l > 0) numIn(dim.l) else ""
                        "p" -> if (dim.p > 0) numIn(dim.p) else ""
                        else -> dim.qty.toString()
                    }, c.uppercase(), c != "qty")
                    et.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, colWeight(c))
                    et.addTextChangedListener(simpleWatcher { txt ->
                        val v = txt.replace(',', '.')
                        val n = v.toDoubleOrNull() ?: 0.0
                        when (c) {
                            "h" -> { dim.h = n; dim.puOverride = null }
                            "l" -> { dim.l = n; dim.puOverride = null }
                            "p" -> { dim.p = n; dim.puOverride = null }
                            "qty" -> dim.qty = (v.toDoubleOrNull()?.toInt() ?: 1).coerceAtLeast(1)
                        }
                        refresh()
                    })
                    row.addView(et)
                }
                "pu" -> {
                    val auto = OrderData.calcUnitPrice(prod, dim)
                    val et = numField(if (auto > 0) fmt(auto) else "", "— Ar", false)
                    et.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, colWeight(c))
                    et.addTextChangedListener(simpleWatcher { txt ->
                        if (et.hasFocus()) {
                            val raw = txt.replace(',', '.').replace(Regex("[^0-9.]"), "")
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
                        setTextColor(Color.parseColor("#0E9E72"))
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

    private fun numField(value: String, hint: String, decimal: Boolean = true): EditText {
        return EditText(this).apply {
            setText(value)
            this.hint = hint
            if (decimal) {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.,")
            } else {
                inputType = InputType.TYPE_CLASS_NUMBER
            }
            textSize = 13f
            setTextColor(Color.parseColor("#18202E"))
            setHintTextColor(Color.parseColor("#97A0B2"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = strokedBg(Color.parseColor("#F2F5FB"), 10, Color.parseColor("#D7DDEA"))
        }
    }

    /** Affiche un nombre cm : entier sans décimale, sinon avec virgule. */
    private fun numIn(v: Double): String =
        if (v == Math.floor(v)) v.toInt().toString() else v.toString().replace('.', ',')

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
            container.addView(productOptionCard(id, label) {
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
            background = strokedBg(Color.parseColor("#EDEFF6"), 12, Color.parseColor("#D7DDEA"))
            setTextColor(Color.parseColor("#6A7488"))
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
        setTextColor(Color.parseColor("#18202E"))
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(10))
    }

    /* ─── Images produit (fichiers res/drawable/prod_<id>.png|jpg|webp) ─── */
    private val productEmoji = mapOf(
        "fenetre" to "🪟", "lavarangana" to "🛡️", "porte" to "🚪",
        "rideau" to "🏪", "vitrine" to "🏬"
    )

    private fun productImageRes(pid: String): Int =
        resources.getIdentifier("prod_$pid", "drawable", packageName)

    /** Carte de choix d'un produit avec sa photo (ou pastille de secours si absente). */
    private fun productOptionCard(pid: String, label: String, onClick: () -> Unit): View {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = strokedBg(Color.parseColor("#FFFFFF"), 16, Color.parseColor("#D7DDEA"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
        val box = dp(84)
        val res = productImageRes(pid)
        if (res != 0) {
            ll.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(box, box)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = circleBg(Color.parseColor("#F2F5FB"), Color.parseColor("#21E6FF"))
                clipToOutline = true
                setImageResource(res)
            })
        } else {
            ll.addView(TextView(this).apply {
                text = productEmoji[pid] ?: "📦"
                gravity = Gravity.CENTER
                textSize = 34f
                background = circleBg(
                    Color.parseColor(OrderData.productColor[pid] ?: "#E9EDF6"),
                    Color.parseColor("#33FFFFFF")
                )
                layoutParams = LinearLayout.LayoutParams(box, box)
            })
        }
        val tb = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(14), 0, dp(8), 0)
        }
        tb.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#18202E"))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        })
        tb.addView(TextView(this).apply {
            text = "Toucher pour configurer"
            setTextColor(Color.parseColor("#6A7488"))
            textSize = 12f
        })
        ll.addView(tb)
        ll.addView(TextView(this).apply {
            text = "›"
            setTextColor(Color.parseColor("#0E86C9"))
            textSize = 24f
            setPadding(dp(4), 0, dp(4), 0)
        })
        return ll
    }

    private fun optionCard(title: String, desc: String, selected: Boolean = false, onClick: () -> Unit): View {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = strokedBg(
                if (selected) Color.parseColor("#E9EDF6") else Color.parseColor("#FFFFFF"), 14,
                if (selected) Color.parseColor("#8B5CFF") else Color.parseColor("#D7DDEA")
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
        ll.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#18202E"))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        })
        if (desc.isNotEmpty()) ll.addView(TextView(this).apply {
            text = desc
            setTextColor(Color.parseColor("#6A7488"))
            textSize = 12f
        })
        return ll
    }

    private fun colorCard(o: StepOption, selected: Boolean, onClick: () -> Unit): View {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = strokedBg(
                if (selected) Color.parseColor("#E9EDF6") else Color.parseColor("#FFFFFF"), 14,
                if (selected) Color.parseColor("#8B5CFF") else Color.parseColor("#D7DDEA")
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
            setTextColor(Color.parseColor("#18202E"))
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
        val nom = clientField("Nom du client").apply { if (editId > 0) setText(editNom) }
        val lieu = clientField("Lieu / Adresse").apply { if (editId > 0) setText(editLieu) }
        val dateCmd = dateField("Date commande", if (editId > 0) editDateCmd else today())
        val dateLiv = dateField("Date livraison", if (editId > 0) editDateLiv else "")
        val avance = clientField("Avance (Ar)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            if (editId > 0 && editAvance > 0) setText(Math.round(editAvance).toString())
        }
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

    /* ════════════ CHARGEMENT POUR ÉDITION ════════════ */
    private fun loadForEdit(jsonStr: String) {
        try {
            val o = JSONObject(jsonStr)
            editId = o.optString("id").toIntOrNull() ?: 0
            editNom = o.optString("client_nom")
            editLieu = o.optString("client_lieu")
            editDateCmd = o.optString("date_commande")
            editDateLiv = o.optString("date_livraison")
            editAvance = o.optString("avance_ar").toDoubleOrNull() ?: 0.0
            val prods = o.optJSONArray("produits") ?: return
            for (i in 0 until prods.length()) {
                val p = prods.optJSONObject(i) ?: continue
                val item = ProductItem(
                    productId = p.optString("product_id"),
                    label = p.optString("product_label")
                )
                val cfg = p.optJSONObject("config_parsed")
                    ?: try { JSONObject(p.optString("config_json", "{}")) } catch (e: Exception) { JSONObject() }
                cfg.keys().forEach { k -> item.config[k] = cfg.optString(k) }
                val dims = p.optJSONArray("dimensions") ?: JSONArray()
                for (j in 0 until dims.length()) {
                    val dd = dims.optJSONObject(j) ?: continue
                    val dim = Dimension(
                        h = dd.optString("hauteur_cm", "0").toDoubleOrNull() ?: 0.0,
                        l = dd.optString("largeur_cm", "0").toDoubleOrNull() ?: 0.0,
                        p = dd.optString("profondeur_cm", "0").toDoubleOrNull() ?: 0.0,
                        qty = (dd.optString("quantite", "1").toDoubleOrNull()?.toInt() ?: 1).coerceAtLeast(1)
                    )
                    val pu = dd.optString("prix_unitaire", "0").toDoubleOrNull() ?: 0.0
                    if (pu > 0) dim.puOverride = pu
                    item.dimensions.add(dim)
                }
                commande.add(item)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur de chargement de la commande", Toast.LENGTH_SHORT).show()
        }
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
            .setView(TextView(this).apply { text = "  Enregistrement en cours..."; setTextColor(Color.parseColor("#18202E")); setPadding(dp(20), dp(30), dp(20), dp(30)) })
            .setCancelable(false).create()
        progress.show()

        Thread {
            var ok = false; var reference = ""; var err = ""
            try {
                val body = payload.toString()
                // 1) GET d'abord : openresty sert la page de challenge (un POST sans
                //    cookie est rejete par le proxy avec 400). On resout le cookie __test.
                var cookie: String? = null
                val pre = getText(SAVE_URL)
                if (isChallenge(pre.second)) cookie = solveAesChallenge(pre.second)
                // 2) POST avec le cookie
                var resp = postJson(SAVE_URL, body, cookie)
                // 3) Si le cookie a expire entre-temps, re-resoudre et renvoyer une fois
                if (isChallenge(resp.second)) {
                    val c2 = solveAesChallenge(resp.second)
                    if (c2 != null) resp = postJson(SAVE_URL, body, c2)
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
            // En mode édition : la nouvelle commande est créée, on supprime l'ancienne.
            if (ok && editId > 0) deleteOldOrder(editId)
            runOnUiThread {
                progress.dismiss()
                if (ok) showSuccess(reference, nom, total, avance, reste)
                else AlertDialog.Builder(this, R.style.NeonDialog)
                    .setTitle("Erreur").setMessage(err).setPositiveButton("OK", null).show()
            }
        }.start()
    }

    private fun deleteOldOrder(id: Int) {
        try {
            var cookie: String? = null
            val pre = getText(DELETE_URL)
            if (isChallenge(pre.second)) cookie = solveAesChallenge(pre.second)
            postJson(DELETE_URL, JSONObject().put("id", id).toString(), cookie)
        } catch (e: Exception) { /* ignore */ }
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
            .setTitle(if (editId > 0) "Commande modifiée !" else "Commande enregistree !")
            .setMessage(msg)
            .setPositiveButton("Nouvelle commande") { _, _ -> commande.clear(); renderAll() }
            .setNegativeButton("Fermer") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#0E86C9"))
        textSize = 15f; setTypeface(typeface, Typeface.BOLD); setPadding(0, 0, 0, dp(8))
    }

    private fun clientField(hint: String) = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.parseColor("#18202E"))
        setHintTextColor(Color.parseColor("#97A0B2"))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = strokedBg(Color.parseColor("#F2F5FB"), 12, Color.parseColor("#D7DDEA"))
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

    private fun circleBg(color: Int, strokeColor: Int) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color); setStroke(dp(2).coerceAtLeast(1), strokeColor)
    }

    private fun roundedTopBg(color: Int, radiusDp: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        val r = dp(radiusDp).toFloat()
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    }

    /* ════════════ RESEAU + CHALLENGE ANTI-BOT ════════════ */
    private val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** GET simple (sert a recuperer la page de challenge avant le POST). */
    private fun getText(urlStr: String): Pair<Int, String> {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000; readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/html,application/json,*/*")
            setRequestProperty("User-Agent", UA)
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to text
    }

    private fun postJson(urlStr: String, body: String, cookie: String?): Pair<Int, String> {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20000; readTimeout = 20000
            instanceFollowRedirects = true
            setFixedLengthStreamingMode(bytes.size)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Referer", urlStr)
            if (cookie != null) setRequestProperty("Cookie", "__test=$cookie")
        }
        conn.outputStream.use { it.write(bytes) }
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
