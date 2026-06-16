package com.alumanager

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Appel direct de l'API Claude (Messages) en HTTP — léger, sans SDK lourd.
 * Modèle par défaut : claude-opus-4-8. La clé API est fournie par l'utilisateur.
 */
object AnthropicClient {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-opus-4-8"
    private const val VERSION = "2023-06-01"

    /**
     * @param messages JSONArray de {role:"user"|"assistant", content:"..."}
     * @return Pair(ok, texteOuErreur)
     */
    fun chat(apiKey: String, system: String, messages: JSONArray, maxTokens: Int = 3000): Pair<Boolean, String> {
        return try {
            val body = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", maxTokens)
                put("system", system)
                put("messages", messages)
            }.toString()
            val bytes = body.toByteArray(Charsets.UTF_8)
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000; readTimeout = 60000
                setFixedLengthStreamingMode(bytes.size)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", VERSION)
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299) {
                val msg = try { JSONObject(text).optJSONObject("error")?.optString("message") } catch (e: Exception) { null }
                return false to "Erreur API (HTTP $code) : ${msg ?: text.take(200)}"
            }
            val content = JSONObject(text).optJSONArray("content") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val b = content.optJSONObject(i) ?: continue
                if (b.optString("type") == "text") sb.append(b.optString("text"))
            }
            true to sb.toString().ifBlank { "(réponse vide)" }
        } catch (e: Exception) {
            false to "Erreur réseau : ${e.localizedMessage ?: "inconnue"}"
        }
    }
}
