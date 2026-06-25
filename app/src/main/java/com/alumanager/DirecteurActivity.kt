package com.alumanager

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.alumanager.databinding.ActivityDirecteurBinding
import org.json.JSONArray
import org.json.JSONObject

/** Directeur IA — conseiller d'affaires basé sur Claude, alimenté par les données de l'app. */
class DirecteurActivity : AppCompatActivity() {

    private lateinit var b: ActivityDirecteurBinding
    private val GET_URL = "https://alu.xo.je/get_commandes.php"
    private val convo = JSONArray()
    private var contextText = "(données en cours de chargement)"
    private var busy = false

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceOutput = true
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) send(spoken)
        }
    }

    private val SYSTEM = """
        Tu es le directeur-conseil d'AluManager, une entreprise de menuiserie aluminium à Madagascar.
        Tu analyses les données réelles de l'entreprise (commandes, trésorerie/caisse "Kaonty") et tu réponds
        en français, de façon claire, concrète et chiffrée. Monnaie : Ariary (Ar).
        Donne des constats précis puis des recommandations actionnables. Sois direct et utile.
        Si une donnée manque, dis-le simplement. N'invente pas de chiffres.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDirecteurBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.btnKey.setOnClickListener { showKeyDialog() }
        b.btnSend.setOnClickListener { send(b.input.text.toString().trim()) }
        b.btnBriefing.setOnClickListener {
            send("Fais-moi un briefing complet de mon entreprise : rentabilité, trésorerie (Kaonty), créances (restes à payer), et 3 recommandations concrètes.")
        }
        b.btnMic.setOnClickListener { startListening() }
        b.btnVoice.setOnClickListener { toggleVoice() }
        b.btnVoice.setOnLongClickListener { showVoicePicker(); true }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRENCH
                tts?.setSpeechRate(0.98f); tts?.setPitch(1.0f)
                ttsReady = true
                applyVoice()
            }
        }
        addBubble("Bonjour 👋 Je suis votre directeur-conseil. Posez une question (texte ou 🎤) ou lancez un briefing complet.", false)
        loadContext()
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }

    private fun toggleVoice() {
        voiceOutput = !voiceOutput
        b.btnVoice.text = if (voiceOutput) "🔊" else "🔇"
        if (!voiceOutput) tts?.stop()
        Toast.makeText(this, if (voiceOutput) "Lecture vocale activée" else "Lecture vocale coupée", Toast.LENGTH_SHORT).show()
    }

    private fun startListening() {
        tts?.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez maintenant…")
        }
        try { speechLauncher.launch(intent) }
        catch (e: Exception) { Toast.makeText(this, "Reconnaissance vocale indisponible sur ce téléphone", Toast.LENGTH_LONG).show() }
    }

    private fun frenchVoices(): List<Voice> = try {
        (tts?.voices ?: emptySet<Voice>())
            .filter { it.locale?.language == "fr" && !it.isNetworkConnectionRequired || it.locale?.language == "fr" }
            .sortedWith(compareBy<Voice>({ if (it.isNetworkConnectionRequired) 1 else 0 }).thenByDescending { it.quality })
    } catch (e: Exception) { emptyList() }

    private fun applyVoice() {
        val t = tts ?: return
        val voices = frenchVoices()
        if (voices.isEmpty()) return
        val saved = prefs().getString("ttsVoice", "")
        val v = voices.firstOrNull { it.name == saved } ?: voices.first()
        try { t.voice = v; prefs().edit().putString("ttsVoice", v.name).apply() } catch (e: Exception) {}
    }

    private fun showVoicePicker() {
        val voices = frenchVoices()
        if (voices.isEmpty()) {
            AlertDialog.Builder(this, R.style.NeonDialog)
                .setTitle("Voix française")
                .setMessage("Aucune voix française trouvée sur ce téléphone.\n\nInstalle-en une : Réglages Android → Langues et saisie → Synthèse vocale → moteur Google → installer la voix « Français ».")
                .setPositiveButton("OK", null).show()
            return
        }
        val current = prefs().getString("ttsVoice", "")
        val labels = voices.map { v ->
            val off = if (v.isNetworkConnectionRequired) "en ligne" else "hors-ligne"
            val q = when { v.quality >= 500 -> "très bonne"; v.quality >= 400 -> "bonne"; v.quality >= 300 -> "normale"; else -> "basique" }
            "${v.locale?.country ?: "FR"} · qualité $q · $off"
        }.toTypedArray()
        var sel = voices.indexOfFirst { it.name == current }.let { if (it < 0) 0 else it }
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("🔊 Choisir la voix")
            .setSingleChoiceItems(labels, sel) { _, which -> sel = which }
            .setPositiveButton("Choisir & tester") { _, _ ->
                val v = voices[sel]
                try { tts?.voice = v } catch (e: Exception) {}
                prefs().edit().putString("ttsVoice", v.name).apply()
                speakOut("Bonjour, je suis votre directeur. Voici la voix sélectionnée.")
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun speakOut(text: String) {
        val t = tts ?: return
        if (!ttsReady) return
        t.stop()
        var i = 0; var first = true
        while (i < text.length) {
            val end = minOf(i + 3500, text.length)
            t.speak(text.substring(i, end), if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "dir$i")
            first = false; i = end
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun prefs() = getSharedPreferences("ai", MODE_PRIVATE)
    private fun apiKey() = prefs().getString("apikey", "") ?: ""

    /* ── contexte entreprise ── */
    private fun loadContext() {
        Thread {
            val sb = StringBuilder()
            try {
                val body = Net.get("$GET_URL?t=${System.currentTimeMillis()}")
                val json = Net.extractJson(body)?.let { JSONObject(it) }
                if (json != null && json.optBoolean("success")) {
                    val st = json.optJSONObject("stats") ?: JSONObject()
                    sb.append("COMMANDES — nb: ${st.optString("nb","0")}, CA total: ${st.optString("ca","0")} Ar, ")
                    sb.append("avances: ${st.optString("av","0")} Ar, restes à payer (créances): ${st.optString("re","0")} Ar\n")
                    val cmds = json.optJSONArray("commandes") ?: JSONArray()
                    val typeCount = HashMap<String, Int>()
                    val unpaid = ArrayList<String>()
                    for (i in 0 until cmds.length()) {
                        val c = cmds.optJSONObject(i) ?: continue
                        val prods = c.optJSONArray("produits") ?: JSONArray()
                        for (j in 0 until prods.length()) {
                            val t = prods.optJSONObject(j)?.optString("product_id") ?: continue
                            typeCount[t] = (typeCount[t] ?: 0) + 1
                        }
                        val reste = c.optString("reste_ar","0").toDoubleOrNull() ?: 0.0
                        if (reste > 0 && unpaid.size < 15) {
                            unpaid.add("- ${c.optString("client_nom")} (${c.optString("reference")}) : reste ${reste.toLong()} Ar, livraison ${c.optString("date_livraison")}")
                        }
                    }
                    if (typeCount.isNotEmpty())
                        sb.append("Produits commandés par type: " + typeCount.entries.joinToString(", ") { "${it.key}=${it.value}" } + "\n")
                    if (unpaid.isNotEmpty()) sb.append("Commandes non soldées :\n" + unpaid.joinToString("\n") + "\n")
                } else sb.append("Commandes : données indisponibles.\n")
            } catch (e: Exception) { sb.append("Commandes : non récupérées (hors-ligne).\n") }

            // Kaonty (local)
            try {
                val ops = JSONArray(getSharedPreferences("kaonty", MODE_PRIVATE).getString("ops", "[]"))
                var inAlu = 0.0; var outAlu = 0.0; var inPlt = 0.0; var outPlt = 0.0
                for (i in 0 until ops.length()) {
                    val o = ops.optJSONObject(i) ?: continue
                    val cat = o.optString("cat", "alu"); val amt = o.optDouble("amount", 0.0)
                    val isIn = o.optString("type") == "in"
                    if (cat == "plateaux") { if (isIn) inPlt += amt else outPlt += amt }
                    else { if (isIn) inAlu += amt else outAlu += amt }
                }
                sb.append("KAONTY (caisse) — Aluminium: solde ${(inAlu-outAlu).toLong()} Ar (versé ${inAlu.toLong()}, sorti ${outAlu.toLong()}) ; ")
                sb.append("Plateaux: solde ${(inPlt-outPlt).toLong()} Ar (versé ${inPlt.toLong()}, sorti ${outPlt.toLong()}).\n")
            } catch (e: Exception) { }

            contextText = sb.toString().ifBlank { "(aucune donnée)" }
            runOnUiThread { b.status.text = "Données chargées ✓" }
        }.start()
    }

    /* ── chat ── */
    private fun send(text: String) {
        if (text.isBlank() || busy) return
        if (apiKey().isBlank()) { showKeyDialog(); return }
        b.input.setText("")
        addBubble(text, true)
        val typing = addBubble("…", false)
        busy = true; b.status.text = "Le directeur réfléchit…"
        Thread {
            convo.put(JSONObject().put("role", "user").put("content", text))
            val system = SYSTEM + "\n\nDONNÉES ACTUELLES DE L'ENTREPRISE :\n" + contextText
            val (ok, answer) = AnthropicClient.chat(apiKey(), system, convo)
            runOnUiThread {
                busy = false; b.status.text = "Données chargées ✓"
                typing.text = answer
                if (ok) {
                    convo.put(JSONObject().put("role", "assistant").put("content", answer))
                    if (voiceOutput) speakOut(answer)
                }
                b.scroll.post { b.scroll.fullScroll(android.view.View.FOCUS_DOWN) }
            }
        }.start()
    }

    private fun addBubble(text: String, user: Boolean): TextView {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor(if (user) "#08101F" else "#EAF2FF"))
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = if (user) btnGrad("#27FFC4", "#21E6FF")
            else strokedBg(Color.parseColor("#101A30"), 14, Color.parseColor("#2A3C66"))
            if (user) setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            lp.gravity = if (user) Gravity.END else Gravity.START
            lp.leftMargin = if (user) dp(40) else 0
            lp.rightMargin = if (user) 0 else dp(40)
            layoutParams = lp
        }
        b.chat.addView(tv)
        b.scroll.post { b.scroll.fullScroll(android.view.View.FOCUS_DOWN) }
        return tv
    }

    private fun showKeyDialog() {
        val pad = dp(16)
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8), pad, 0) }
        v.addView(TextView(this).apply {
            text = "Colle ta clé API Claude (commence par sk-ant-…). Elle est stockée sur ce téléphone uniquement."
            setTextColor(Color.parseColor("#8A97C2")); textSize = 12f
        })
        val et = EditText(this).apply {
            setText(apiKey()); hint = "sk-ant-..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(Color.parseColor("#EAF2FF")); setHintTextColor(Color.parseColor("#5A688F"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = strokedBg(Color.parseColor("#0B1326"), 12, Color.parseColor("#243456"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10); layoutParams = lp
        }
        v.addView(et)
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("🔑 Clé API Claude")
            .setView(v)
            .setPositiveButton("Enregistrer") { _, _ ->
                prefs().edit().putString("apikey", et.text.toString().trim()).apply()
                Toast.makeText(this, "Clé enregistrée", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun strokedBg(color: Int, radiusDp: Int, strokeColor: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(1).coerceAtLeast(1), strokeColor)
    }
    private fun btnGrad(c1: String, c2: String) = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
    ).apply { cornerRadius = dp(14).toFloat() }
}
