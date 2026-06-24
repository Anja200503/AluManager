package com.alumanager

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Génère une facture PDF (A4) à partir d'une commande (JSON get_commandes). */
object InvoicePdf {

    private const val W = 595
    private const val H = 842
    private const val M = 40f

    private fun fmt(v: Double) = "%,d".format(Math.round(v)).replace(",", " ")
    private fun d(o: JSONObject, k: String) = o.optString(k, "0").toDoubleOrNull() ?: 0.0
    private fun fmtDate(s: String): String {
        val p = s.split("-"); return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else s
    }

    fun generate(ctx: Context, cmd: JSONObject): File {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c = page.canvas
        var y: Float

        val title = Paint().apply { color = Color.parseColor("#F2F5FB"); textSize = 24f; isFakeBoldText = true; isAntiAlias = true }
        val brand = Paint().apply { color = Color.parseColor("#4A42D6"); textSize = 16f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.RIGHT }
        val sub = Paint().apply { color = Color.GRAY; textSize = 9f; isAntiAlias = true; textAlign = Paint.Align.RIGHT }
        val lbl = Paint().apply { color = Color.GRAY; textSize = 9f; isAntiAlias = true }
        val txt = Paint().apply { color = Color.parseColor("#222222"); textSize = 10f; isAntiAlias = true }
        val txtB = Paint().apply { color = Color.parseColor("#222222"); textSize = 10f; isFakeBoldText = true; isAntiAlias = true }
        val txtR = Paint(txt).apply { textAlign = Paint.Align.RIGHT }
        val headBg = Paint().apply { color = Color.parseColor("#4A42D6") }
        val headTx = Paint().apply { color = Color.WHITE; textSize = 9.5f; isFakeBoldText = true; isAntiAlias = true }
        val headTxR = Paint(headTx).apply { textAlign = Paint.Align.RIGHT }
        val rule = Paint().apply { color = Color.parseColor("#DDDDDD"); strokeWidth = 0.8f }
        val prod = Paint().apply { color = Color.parseColor("#4A148C"); textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
        val prodR = Paint(prod).apply { textAlign = Paint.Align.RIGHT }

        // colonnes
        val xDim = M; val xDesc = 88f; val xQty = 360f; val xPU = 460f; val xTot = W - M

        fun newPage() {
            doc.finishPage(page); pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()); c = page.canvas
        }
        fun itemsHeader(yy: Float): Float {
            c.drawRect(M, yy, W - M, yy + 20f, headBg)
            val ty = yy + 14f
            c.drawText("Dim", xDim + 4, ty, headTx)
            c.drawText("Désignation", xDesc, ty, headTx)
            c.drawText("Qté", xQty, ty, headTxR)
            c.drawText("P.U.", xPU, ty, headTxR)
            c.drawText("Total", xTot, ty, headTxR)
            return yy + 28f
        }

        /* ── En-tête ── */
        y = M
        c.drawText("FACTURE", M, y + 18, title)
        c.drawText("ALU MANAGER", W - M, y + 10, brand)
        c.drawText("Gestion professionnelle aluminium", W - M, y + 24, sub)
        y += 44f
        c.drawLine(M, y, W - M, y, rule); y += 18f

        c.drawText("Référence : ${cmd.optString("reference")}", M, y, txtB)
        c.drawText("Date : ${fmtDate(cmd.optString("date_commande"))}", W - M, y, txtR)
        y += 14f
        c.drawText("Statut : ${cmd.optString("statut", "confirmée")}", M, y, txt)
        c.drawText("Livraison : ${fmtDate(cmd.optString("date_livraison"))}", W - M, y, txtR)
        y += 20f

        c.drawText("CLIENT", M, y, lbl); y += 14f
        c.drawText(cmd.optString("client_nom"), M, y, txtB); y += 14f
        c.drawText(cmd.optString("client_lieu"), M, y, txt); y += 18f

        y = itemsHeader(y)

        /* ── Produits ── */
        val produits = cmd.optJSONArray("produits")
        var grandTotal = 0.0
        if (produits != null) for (i in 0 until produits.length()) {
            val p = produits.optJSONObject(i) ?: continue
            if (y > H - M - 60) { newPage(); y = M; y = itemsHeader(y) }
            val pTotal = d(p, "total_ar"); grandTotal += pTotal
            // ligne produit
            c.drawText("${i + 1}. ${p.optString("product_label")}", xDim, y, prod)
            c.drawText("${fmt(pTotal)} Ar", xTot, y, prodR)
            y += 13f
            val cfg = p.optJSONObject("config_parsed")
            if (cfg != null) {
                val cv = cfg.keys().asSequence().map { cfg.optString(it) }.filter { it.isNotEmpty() }.joinToString(" · ")
                if (cv.isNotEmpty()) { c.drawText(cv, xDesc, y, lbl); y += 13f }
            }
            // dimensions
            val dims = p.optJSONArray("dimensions")
            if (dims != null) for (di in 0 until dims.length()) {
                val dm = dims.optJSONObject(di) ?: continue
                if (y > H - M - 30) { newPage(); y = M; y = itemsHeader(y) }
                val ref = dm.optString("dim_ref").ifBlank { "L${di + 1}" }
                val h = d(dm, "hauteur_cm"); val l = d(dm, "largeur_cm"); val pr = d(dm, "profondeur_cm")
                val dimStr = buildString {
                    if (h > 0) append("${num(h)}×")
                    append(num(l))
                    if (pr > 0) append("×${num(pr)}")
                    append(" cm")
                }
                c.drawText(ref, xDim + 4, y, txt)
                c.drawText(dimStr, xDesc, y, txt)
                c.drawText(dm.optString("quantite", "1"), xQty, y, txtR)
                c.drawText(fmt(d(dm, "prix_unitaire")), xPU, y, txtR)
                c.drawText(fmt(d(dm, "prix_total")), xTot, y, txtR)
                y += 14f
            }
            c.drawLine(M, y, W - M, y, rule); y += 14f
        }

        /* ── Totaux ── */
        if (y > H - M - 90) { newPage(); y = M }
        val total = d(cmd, "total_ar").let { if (it > 0) it else grandTotal }
        val avance = d(cmd, "avance_ar"); val reste = d(cmd, "reste_ar")
        y += 6f
        val boxX = W - M - 230
        c.drawText("Total général", boxX, y, txt); c.drawText("${fmt(total)} Ar", xTot, y, txtR); y += 16f
        c.drawText("Avance", boxX, y, txt); c.drawText("${fmt(avance)} Ar", xTot, y, txtR); y += 16f
        c.drawRect(boxX, y, W - M, y + 24f, headBg)
        c.drawText("RESTE À PAYER", boxX + 6, y + 16, headTx)
        c.drawText("${fmt(reste)} Ar", xTot - 6, y + 16, headTxR)
        y += 44f

        c.drawText("Merci de votre confiance — ALU MANAGER", M, H - M, lbl)

        doc.finishPage(page)
        val dir = File(ctx.cacheDir, "factures"); dir.mkdirs()
        val safe = cmd.optString("reference").replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "facture" }
        val file = File(dir, "Facture_$safe.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun num(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else ((Math.round(v * 100) / 100.0).toString())
}
