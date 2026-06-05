package com.alumanager

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Reseau partage avec resolution du challenge anti-bot de l'hebergeur
 * InfinityFree (aes.js). Regle d'or : GET d'abord pour obtenir le cookie
 * __test (un POST sans cookie est rejete par openresty en 400), puis POST.
 */
object Net {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun isChallenge(s: String): Boolean =
        s.contains("toNumbers(") || s.contains("slowAES") || s.contains("aes.js")

    /** Extrait le 1er objet JSON valide d'une reponse (ignore HTML/notices autour). */
    fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val cand = text.substring(start, end + 1)
        return try { JSONObject(cand); cand } catch (e: Exception) { null }
    }

    private fun hexToBytes(s: String): ByteArray =
        ByteArray(s.length / 2) {
            ((Character.digit(s[it * 2], 16) shl 4) + Character.digit(s[it * 2 + 1], 16)).toByte()
        }

    private fun bytesToHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    /** Resout le challenge JS : cookie __test = hex(AES-CBC-decrypt(c, key=a, iv=b)). */
    fun solveAesChallenge(html: String): String? {
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
        } catch (e: Exception) { null }
    }

    private fun request(
        urlStr: String, method: String, body: String?, cookie: String?
    ): Pair<Int, String> {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000; readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json, text/html, */*")
            setRequestProperty("Referer", urlStr)
            if (cookie != null) setRequestProperty("Cookie", "__test=$cookie")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                val bytes = body.toByteArray(Charsets.UTF_8)
                setFixedLengthStreamingMode(bytes.size)
                outputStream.use { it.write(bytes) }
            }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to text
    }

    /** GET avec resolution du challenge. Retourne le corps (idealement du JSON). */
    fun get(urlStr: String): String {
        var resp = request(urlStr, "GET", null, null)
        if (isChallenge(resp.second)) {
            val cookie = solveAesChallenge(resp.second)
            if (cookie != null) resp = request(urlStr, "GET", null, cookie)
        }
        return resp.second
    }

    /** POST JSON : GET d'abord pour le cookie, puis POST (1 retry si re-challenge). */
    fun post(urlStr: String, body: String): String {
        var cookie: String? = null
        val pre = request(urlStr, "GET", null, null)
        if (isChallenge(pre.second)) cookie = solveAesChallenge(pre.second)
        var resp = request(urlStr, "POST", body, cookie)
        if (isChallenge(resp.second)) {
            val c2 = solveAesChallenge(resp.second)
            if (c2 != null) resp = request(urlStr, "POST", body, c2)
        }
        return resp.second
    }
}
