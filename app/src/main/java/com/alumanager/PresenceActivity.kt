package com.alumanager

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import java.io.File
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

    private val FACE_THRESHOLD = 0.62f
    private var pendingFile: File? = null
    private var pendingEmpId: String? = null
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockFmt = SimpleDateFormat("HH:mm:ss · EEEE dd/MM/yyyy", Locale.FRANCE)
    private val clockTick = object : Runnable {
        override fun run() {
            b.clock.text = clockFmt.format(Date()).replaceFirstChar { it.uppercase() }
            clockHandler.postDelayed(this, 1000)
        }
    }
    private val faceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            val path = res.data?.getStringExtra("path")
            if (path != null) { pendingFile = File(path); processPhoto() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPresenceBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.btnAddEmp.setOnClickListener { showAddEmployee() }
        b.statDate.text = dateFmt.format(Date())
        render()
    }

    override fun onResume() { super.onResume(); clockHandler.post(clockTick); render() }
    override fun onPause() { super.onPause(); clockHandler.removeCallbacks(clockTick) }

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
        b.listContainer.addView(faceButton())
        for (i in 0 until emps.length()) {
            val e = emps.optJSONObject(i) ?: continue
            b.listContainer.addView(buildCard(e))
        }
    }

    private fun faceButton() = TextView(this).apply {
        text = "📷  Pointer par reconnaissance faciale"
        gravity = Gravity.CENTER; setTextColor(Color.parseColor("#08101F")); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
        background = btnGrad("#21E6FF", "#8B5CFF")
        setPadding(dp(12), dp(14), dp(12), dp(14))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(12); layoutParams = lp
        setOnClickListener { launchFaceCapture(null) }
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

        // bouton pointage (empreinte) pleine largeur
        val pointBtn = TextView(this).apply {
            text = if (present) "👆 Pointer Sortie" else "👆 Pointer Entrée"
            gravity = Gravity.CENTER; setTextColor(Color.parseColor("#08101F")); textSize = 13f; setTypeface(typeface, Typeface.BOLD)
            background = btnGrad(if (present) "#FFC34D" else "#27FFC4", if (present) "#FF8A3D" else "#21E6FF")
            setPadding(dp(10), dp(12), dp(10), dp(12))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12); layoutParams = lp
            setOnClickListener { pointer(emp, present) }
        }
        card.addView(pointBtn)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8); layoutParams = lp
        }
        val hasFace = emp.has("face")
        row2.addView(actionBtn(if (hasFace) "📷 Visage ✓" else "📷 Visage") { launchFaceCapture(emp.optString("id")) }.apply {
            (layoutParams as LinearLayout.LayoutParams).weight = 2f
            if (hasFace) setTextColor(Color.parseColor("#27FFC4"))
        })
        row2.addView(spacer())
        row2.addView(actionBtn("🕘") { showHistory(emp) })
        row2.addView(spacer())
        row2.addView(actionBtn("🗑️") { confirmDelete(emp) })
        card.addView(row2)
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
        authenticate(emp.optString("nom")) { recordPointage(emp) }
    }

    private fun recordPointage(emp: JSONObject) {
        val present = isPresent(emp.optString("id"))
        val type = if (present) "out" else "in"
        val libelle = if (type == "in") "Entrée" else "Sortie"
        val arr = loadPointages()
        arr.put(JSONObject().apply {
            put("empId", emp.optString("id")); put("ts", System.currentTimeMillis()); put("type", type)
        })
        savePointages(arr); render()
        toast("$libelle enregistrée — ${emp.optString("nom")}")
    }

    /* ════════ RECONNAISSANCE FACIALE ════════ */
    private fun launchFaceCapture(empId: String?) {
        pendingEmpId = empId
        if (empId == null && !FaceRecognizer.available(this)) { showModelMissing(); return }
        val emps = loadEmployees()
        val title = if (empId != null) {
            var n = "employé"
            for (i in 0 until emps.length()) {
                val e = emps.optJSONObject(i) ?: continue
                if (e.optString("id") == empId) n = e.optString("nom")
            }
            "Enregistrer le visage : $n"
        } else "Pointage par reconnaissance faciale"
        faceLauncher.launch(Intent(this, FaceCaptureActivity::class.java).putExtra("title", title))
    }

    private fun processPhoto() {
        val f = pendingFile ?: return
        val bmp = decodeRotated(f)
        if (bmp == null) { toast("Photo illisible"); f.delete(); return }
        if (!FaceRecognizer.available(this)) { showModelMissing(); f.delete(); return }
        toast("Analyse du visage…")
        FaceRecognizer.embed(this, bmp) { emb ->
            f.delete()
            if (emb == null) { toast("Aucun visage détecté, réessayez"); return@embed }
            val id = pendingEmpId
            if (id != null) enrollFace(id, emb) else identifyAndPoint(emb)
        }
    }

    private fun enrollFace(empId: String, emb: FloatArray) {
        val emps = loadEmployees()
        for (i in 0 until emps.length()) {
            val e = emps.optJSONObject(i) ?: continue
            if (e.optString("id") == empId) {
                val arr = JSONArray(); for (v in emb) arr.put(v.toDouble())
                e.put("face", arr); saveEmployees(emps); render()
                toast("Visage enregistré ✓"); return
            }
        }
    }

    private fun identifyAndPoint(emb: FloatArray) {
        val emps = loadEmployees()
        var best: JSONObject? = null; var bestSim = -1f
        for (i in 0 until emps.length()) {
            val e = emps.optJSONObject(i) ?: continue
            val fa = e.optJSONArray("face") ?: continue
            val v = FloatArray(fa.length()) { fa.optDouble(it).toFloat() }
            val sim = FaceRecognizer.cosine(emb, v)
            if (sim > bestSim) { bestSim = sim; best = e }
        }
        if (best != null && bestSim >= FACE_THRESHOLD) {
            recordPointage(best!!)
            toast("Reconnu : ${best!!.optString("nom")} (${(bestSim * 100).toInt()}%)")
        } else {
            toast("Visage non reconnu" + if (best != null) " (${(bestSim * 100).toInt()}%)" else "")
        }
    }

    private fun showModelMissing() {
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("Modèle facial manquant")
            .setMessage("Le modèle de reconnaissance (.tflite) n'est pas encore installé dans l'app.\n\nFournis le fichier et je l'intègre.")
            .setPositiveButton("OK", null).show()
    }

    private fun decodeRotated(f: File): Bitmap? {
        return try {
            val o = BitmapFactory.Options().apply { inSampleSize = 2 }
            var bmp = BitmapFactory.decodeFile(f.absolutePath, o) ?: return null
            val rot = when (ExifInterface(f.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rot != 0f) {
                val m = android.graphics.Matrix().apply { postRotate(rot) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            bmp
        } catch (e: Exception) { null }
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
