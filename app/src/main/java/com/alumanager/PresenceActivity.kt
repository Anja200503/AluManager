package com.alumanager

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.alumanager.databinding.ActivityPresenceBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestion de présence des employés + pointage (entrée/sortie) validé par empreinte digitale.
 * Données stockées localement (SharedPreferences).
 *
 * Note : Android ne permet pas d'identifier un employé par son empreinte ; l'empreinte du
 * téléphone sert à *valider/sécuriser* le pointage de l'employé sélectionné.
 */
class PresenceActivity : AppCompatActivity() {

    private lateinit var b: ActivityPresenceBinding
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val hmFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
    private val dateFmt = SimpleDateFormat("dd/MM", Locale.FRANCE)
    private val fullFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPresenceBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.btnAddEmp.setOnClickListener { showAddEmployee() }
        b.statDate.text = dateFmt.format(Date())
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun prefs() = getSharedPreferences("presence", MODE_PRIVATE)

    private fun loadEmployees(): JSONArray =
        try { JSONArray(prefs().getString("employees", "[]")) } catch (e: Exception) { JSONArray() }
    private fun saveEmployees(a: JSONArray) = prefs().edit().putString("employees", a.toString()).apply()
    private fun loadPointages(): JSONArray =
        try { JSONArray(prefs().getString("pointages", "[]")) } catch (e: Exception) { JSONArray() }
    private fun savePointages(a: JSONArray) = prefs().edit().putString("pointages", a.toString()).apply()

    /* ── pointages utils ── */
    private fun lastTodayPointage(empId: String): JSONObject? {
        val today = dayFmt.format(Date())
        val arr = loadPointages()
        var best: JSONObject? = null
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            if (p.optString("empId") != empId) continue
            if (dayFmt.format(Date(p.optLong("ts"))) != today) continue
            if (best == null || p.optLong("ts") > best!!.optLong("ts")) best = p
        }
        return best
    }
    private fun isPresent(empId: String): Boolean = lastTodayPointage(empId)?.optString("type") == "in"

    /* ════════ RENDU ════════ */
    private fun render() {
        val emps = loadEmployees()
        var present = 0
        for (i in 0 until emps.length()) if (isPresent(emps.optJSONObject(i).optString("id"))) present++
        b.statEmp.text = emps.length().toString()
        b.statPresent.text = present.toString()

        b.listContainer.removeAllViews()
        if (emps.length() == 0) { b.listContainer.addView(b.emptyState); return }
        for (i in 0 until emps.length()) {
            val e = emps.optJSONObject(i) ?: continue
            b.listContainer.addView(buildCard(e))
        }
    }

    private fun buildCard(emp: JSONObject): View {
        val id = emp.optString("id")
        val present = isPresent(id)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.glass_card)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12); layoutParams = lp
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        // avatar initiale
        top.addView(TextView(this).apply {
            text = emp.optString("nom").take(1).uppercase()
            gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 18f; setTypeface(typeface, Typeface.BOLD)
            background = strokedBg(Color.parseColor(if (present) "#1B3A2E" else "#1B2A4F"), 22, Color.parseColor(if (present) "#27FFC4" else "#8B5CFF"))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        })
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(12), 0, dp(8), 0)
        }
        info.addView(TextView(this).apply { text = emp.optString("nom"); setTextColor(Color.parseColor("#EAF2FF")); textSize = 16f; setTypeface(typeface, Typeface.BOLD) })
        val poste = emp.optString("poste")
        if (poste.isNotEmpty()) info.addView(TextView(this).apply { text = poste; setTextColor(Color.parseColor("#8A97C2")); textSize = 12f })
        val statusTxt = if (present) {
            val inT = lastTodayPointage(id)?.optLong("ts") ?: 0L
            "🟢 Présent depuis ${hmFmt.format(Date(inT))}"
        } else "⚪ Absent"
        info.addView(TextView(this).apply {
            text = statusTxt; setTextColor(Color.parseColor(if (present) "#27FFC4" else "#8A97C2")); textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
        })
        top.addView(info)
        card.addView(top)

        // boutons
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12); layoutParams = lp
        }
        val pointBtn = TextView(this).apply {
            text = if (present) "👆 Pointer Sortie" else "👆 Pointer Entrée"
            gravity = Gravity.CENTER; setTextColor(Color.parseColor("#08101F")); textSize = 13f; setTypeface(typeface, Typeface.BOLD)
            background = btnGrad(if (present) "#FFC34D" else "#27FFC4", if (present) "#FF8A3D" else "#21E6FF")
            setPadding(dp(10), dp(12), dp(10), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f)
            setOnClickListener { pointer(emp, present) }
        }
        row.addView(pointBtn)
        row.addView(spacer())
        row.addView(actionBtn("🕘") { showHistory(emp) }.apply {
            (layoutParams as LinearLayout.LayoutParams).weight = 1f
        })
        row.addView(spacer())
        row.addView(actionBtn("🗑️") { confirmDelete(emp) }.apply {
            (layoutParams as LinearLayout.LayoutParams).weight = 1f
        })
        card.addView(row)
        return card
    }

    private fun spacer() = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) }

    private fun actionBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#EAF2FF")); textSize = 15f
        background = strokedBg(Color.parseColor("#0E1730"), 12, Color.parseColor("#243456"))
        setPadding(dp(10), dp(12), dp(10), dp(12))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { onClick() }
    }

    /* ════════ POINTAGE (empreinte) ════════ */
    private fun pointer(emp: JSONObject, present: Boolean) {
        val type = if (present) "out" else "in"
        val libelle = if (type == "in") "Entrée" else "Sortie"
        authenticate("${emp.optString("nom")} — $libelle") {
            val arr = loadPointages()
            arr.put(JSONObject().apply {
                put("empId", emp.optString("id")); put("ts", System.currentTimeMillis()); put("type", type)
            })
            savePointages(arr)
            render()
            toast("$libelle enregistrée pour ${emp.optString("nom")}")
        }
    }

    private fun authenticate(subtitle: String, onOk: () -> Unit) {
        val mgr = BiometricManager.from(this)
        val can = mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            AlertDialog.Builder(this, R.style.NeonDialog)
                .setTitle("Empreinte indisponible")
                .setMessage("Aucune empreinte n'est configurée sur cet appareil.\n\nValider le pointage manuellement ?")
                .setPositiveButton("Valider") { _, _ -> onOk() }
                .setNegativeButton("Annuler", null)
                .show()
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON)
                        toast("Empreinte : $msg")
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Pointage par empreinte")
            .setSubtitle(subtitle)
            .setNegativeButtonText("Annuler")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    /* ════════ EMPLOYÉS ════════ */
    private fun showAddEmployee() {
        val pad = dp(16)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8), pad, 0) }
        val nom = field("Nom complet")
        val poste = field("Poste / Fonction")
        root.addView(nom); root.addView(poste)
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("➕ Nouvel employé")
            .setView(root)
            .setPositiveButton("Ajouter") { _, _ ->
                val n = nom.text.toString().trim()
                if (n.isEmpty()) { toast("Nom requis"); return@setPositiveButton }
                val arr = loadEmployees()
                arr.put(JSONObject().apply {
                    put("id", "e" + System.currentTimeMillis())
                    put("nom", n); put("poste", poste.text.toString().trim())
                })
                saveEmployees(arr); render(); toast("Employé ajouté")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmDelete(emp: JSONObject) {
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("Supprimer")
            .setMessage("Supprimer ${emp.optString("nom")} et ses pointages ?")
            .setPositiveButton("Supprimer") { _, _ ->
                val id = emp.optString("id")
                val emps = loadEmployees(); val keep = JSONArray()
                for (i in 0 until emps.length()) { val e = emps.optJSONObject(i); if (e.optString("id") != id) keep.put(e) }
                saveEmployees(keep)
                val pts = loadPointages(); val kp = JSONArray()
                for (i in 0 until pts.length()) { val p = pts.optJSONObject(i); if (p.optString("empId") != id) kp.put(p) }
                savePointages(kp)
                render(); toast("Supprimé")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showHistory(emp: JSONObject) {
        val id = emp.optString("id")
        val arr = loadPointages()
        val list = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) { val p = arr.optJSONObject(i); if (p.optString("empId") == id) list.add(p) }
        list.sortByDescending { it.optLong("ts") }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        if (list.isEmpty()) root.addView(TextView(this).apply { text = "Aucun pointage."; setTextColor(Color.parseColor("#8A97C2")); textSize = 14f })
        for (p in list.take(40)) {
            val isIn = p.optString("type") == "in"
            root.addView(TextView(this).apply {
                text = "${if (isIn) "🟢 Entrée" else "🔴 Sortie"}   ${fullFmt.format(Date(p.optLong("ts")))}"
                setTextColor(Color.parseColor(if (isIn) "#27FFC4" else "#FFC34D")); textSize = 13f
                setPadding(0, dp(6), 0, dp(6))
            })
        }
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("Historique — ${emp.optString("nom")}")
            .setView(wrapScroll(root))
            .setPositiveButton("Fermer", null)
            .show()
    }

    /* ════════ HELPERS UI ════════ */
    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.parseColor("#EAF2FF")); setHintTextColor(Color.parseColor("#5A688F"))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = strokedBg(Color.parseColor("#0B1326"), 12, Color.parseColor("#243456"))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10); layoutParams = lp
    }
    private fun wrapScroll(v: View) = android.widget.ScrollView(this).apply { addView(v) }
    private fun strokedBg(color: Int, radiusDp: Int, strokeColor: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(1).coerceAtLeast(1), strokeColor)
    }
    private fun btnGrad(c1: String, c2: String) = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
    ).apply { cornerRadius = dp(14).toFloat() }
}
