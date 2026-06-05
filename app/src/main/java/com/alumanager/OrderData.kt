package com.alumanager

import java.util.UUID

/* ─────────────────────────────────────────────────────────────
   MODELE DE DONNEES + CATALOGUE + ETAPES + MOTEUR DE PRIX
   Porte fidelement depuis commande.js
───────────────────────────────────────────────────────────── */

data class Dimension(
    val id: String = UUID.randomUUID().toString(),
    var h: Double = 0.0,
    var l: Double = 0.0,
    var p: Double = 0.0,
    var qty: Int = 1,
    var puOverride: Double? = null   // prix unitaire manuel (sinon calcul auto)
)

data class ProductItem(
    val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val label: String,
    val config: LinkedHashMap<String, String> = LinkedHashMap(),  // libelles affiches
    val configRaw: HashMap<String, String> = HashMap(),           // valeurs brutes
    val dimensions: MutableList<Dimension> = mutableListOf()
)

/* ─── Etapes de l'assistant ─── */
data class StepOption(
    val v: String,
    val l: String,
    val d: String = "",
    val color: String? = null,
    val border: String? = null
)

data class StepCond(val key: String, val v: String? = null, val notVal: String? = null)

data class Step(
    val key: String,
    val title: String,
    val type: String,            // "options" ou "colors"
    val opts: List<StepOption>,
    val cond: StepCond? = null
)

object OrderData {

    /* ─── Couleurs d'en-tete par produit ─── */
    val productColor = mapOf(
        "fenetre" to "#4A148C",
        "lavarangana" to "#1B5E20",
        "porte" to "#E65100",
        "rideau" to "#1565C0",
        "vitrine" to "#880E4F"
    )

    val productLabels = linkedMapOf(
        "fenetre" to "Fenetre",
        "lavarangana" to "Lavarangana Inox",
        "porte" to "Porte",
        "rideau" to "Rideau Metallique",
        "vitrine" to "Vitrine Alu"
    )

    /* ─── Colonnes par produit ─── */
    val schemas = mapOf(
        "fenetre" to listOf("h", "l", "qty", "pu", "tot"),
        "lavarangana" to listOf("l", "qty", "tot"),
        "porte" to listOf("h", "l", "qty", "pu", "tot"),
        "rideau" to listOf("h", "l", "qty", "pu", "tot"),
        "vitrine" to listOf("h", "l", "p", "qty", "pu", "tot")
    )
    val colLabels = mapOf(
        "h" to "H cm", "l" to "L cm", "p" to "P cm",
        "qty" to "Qte", "pu" to "Prix U.", "tot" to "Total"
    )

    /* ─── Prix de base ─── */
    val fenetreBasePrices = mutableMapOf(
        "coulissante_promotion" to 70000.0,
        "coulissante_deuxieme" to 85000.0,
        "coulissante_premier" to 100000.0,
        "ouvrante" to 90000.0,
        "projetant" to 95000.0,
        "naco" to 80000.0,
        "fixe" to 65000.0
    )
    val fenetreSurcharges = mutableMapOf(
        "antelio_4mm" to 0.0,
        "antelio_5mm" to 0.0,
        "clair_5mm" to 0.0
    )
    val autresBasePrice = mutableMapOf(
        "lavarangana" to 45000.0,
        "porte" to 120000.0,
        "rideau" to 95000.0,
        "vitrine" to 150000.0
    )

    /* ─── Definition des etapes ─── */
    val steps: Map<String, List<Step>> = mapOf(
        "fenetre" to listOf(
            Step("typeFenetre", "Type de fenetre", "options", listOf(
                StepOption("coulissante", "Coulissante", "Glisse sur rails"),
                StepOption("ouvrante", "Ouvrante", "Battante classique"),
                StepOption("projetant", "Projetant", "Ouvre vers ext."),
                StepOption("naco", "Naco", "Lames jalousies"),
                StepOption("fixe", "Fixe", "Non ouvrant")
            )),
            Step("sousType", "Qualite / Sous-type", "options", listOf(
                StepOption("promotion", "Promotion", "Entree de gamme"),
                StepOption("deuxieme", "2eme Choix", "Standard"),
                StepOption("premier", "1er Choix", "Superieur")
            ), StepCond("typeFenetre", v = "coulissante")),
            Step("couleur", "Couleur du profil", "colors", listOf(
                StepOption("blanc", "Blanc", color = "#F5F5F5", border = "#cccccc"),
                StepOption("noir", "Noir", color = "#1a1a1a"),
                StepOption("marron", "Marron", color = "#6B3A2A"),
                StepOption("gris", "Gris", color = "#808080"),
                StepOption("fauxbois", "Faux Bois", color = "#8B6914")
            )),
            Step("typeVitre", "Type de vitrage", "options", listOf(
                StepOption("antelio", "Antelio", "Reflechissant"),
                StepOption("clair", "Clair", "Transparent"),
                StepOption("fume", "Fume", "Teinte sombre"),
                StepOption("cathedrale", "Cathedrale", "Depoli deco")
            )),
            Step("epaisseur", "Epaisseur du vitrage", "options", listOf(
                StepOption("4mm", "4 mm", "Standard"),
                StepOption("5mm", "5 mm", "Intermediaire"),
                StepOption("6mm", "6 mm", "Renforce")
            ))
        ),
        "lavarangana" to listOf(
            Step("typeLava", "Type de lavarangana", "options", listOf(
                StepOption("tsotra", "Inox Tsotra", "Simple / lisse"),
                StepOption("misyvato", "Inox Misy Vato", "Avec pierres deco")
            )),
            Step("couleurPierre", "Couleur des pierres", "options", listOf(
                StepOption("mena", "Mena", "Rouge"),
                StepOption("maintso", "Maintso", "Vert"),
                StepOption("mavo", "Mavo", "Jaune"),
                StepOption("fotsy", "Fotsy", "Blanc")
            ), StepCond("typeLava", v = "misyvato")),
            Step("nbPierres", "Pierres par metre", "options", listOf(
                StepOption("1", "1 / m", "Minimal"),
                StepOption("2", "2 / m", "Classique"),
                StepOption("3", "3 / m", "Dense"),
                StepOption("4", "4 / m", "Tres dense")
            ), StepCond("typeLava", v = "misyvato"))
        ),
        "porte" to listOf(
            Step("typePorte", "Type de porte", "options", listOf(
                StepOption("coulissante", "Coulissante", "Glisse sur rail"),
                StepOption("ouvrante", "Ouvrante", "Battante classique")
            )),
            Step("sousTypePorte", "Qualite", "options", listOf(
                StepOption("promotion", "Promotion", "Entree de gamme"),
                StepOption("premier", "1er Choix", "Superieur"),
                StepOption("deuxieme", "2eme Choix", "Standard")
            ), StepCond("typePorte", v = "coulissante")),
            Step("ventaux", "Nombre de vantaux", "options", listOf(
                StepOption("1", "1 vantail", "Simple"),
                StepOption("2", "2 vantaux", "Double"),
                StepOption("3", "3 vantaux", "Triple"),
                StepOption("4", "4 vantaux", "Quadruple")
            )),
            Step("forme", "Forme", "options", listOf(
                StepOption("plein_vitre", "Plein Vitre", "Tout vitrage"),
                StepOption("demi_vitre", "Demi-Vitre", "Mi-vitre"),
                StepOption("plein_bard", "Plein Bardage", "Sans vitrage")
            ), StepCond("typePorte", v = "ouvrante")),
            Step("typeVitre", "Type de vitrage", "options", listOf(
                StepOption("fume", "Fume", "Teinte"),
                StepOption("antelio", "Antelio", "Reflechissant"),
                StepOption("cathedrale", "Cathedrale", "Depoli"),
                StepOption("clair", "Clair", "Transparent")
            ), StepCond("forme", notVal = "plein_bard")),
            Step("couleur", "Couleur du profil", "colors", listOf(
                StepOption("blanc", "Blanc", color = "#F5F5F5", border = "#cccccc"),
                StepOption("noir", "Noir", color = "#1a1a1a"),
                StepOption("marron", "Marron", color = "#6B3A2A"),
                StepOption("gris", "Gris", color = "#808080"),
                StepOption("fauxbois", "Faux Bois", color = "#8B6914")
            ))
        ),
        "rideau" to listOf(
            Step("pose", "Type de pose", "options", listOf(
                StepOption("interieure", "Interieure", "Montage interieur"),
                StepOption("exterieure", "Exterieure", "Montage exterieur")
            )),
            Step("couleur", "Couleur", "colors", listOf(
                StepOption("marron", "Marron", color = "#6B3A2A"),
                StepOption("blanc", "Blanc", color = "#F5F5F5", border = "#cccccc"),
                StepOption("noir", "Noir", color = "#1a1a1a"),
                StepOption("brut", "Tsy Miloko", color = "#b0b0b0")
            ))
        ),
        "vitrine" to listOf(
            Step("etageres", "Nombre d'etageres", "options", listOf(
                StepOption("1", "1 etagere", "Minimal"),
                StepOption("2", "2 etageres", "Compact"),
                StepOption("3", "3 etageres", "Standard"),
                StepOption("4", "4 etageres", "Large"),
                StepOption("5", "5 etageres", "Maximum")
            )),
            Step("couleur", "Couleur du cadre", "colors", listOf(
                StepOption("marron", "Marron", color = "#6B3A2A"),
                StepOption("blanc", "Blanc", color = "#F5F5F5", border = "#cccccc"),
                StepOption("noir", "Noir", color = "#1a1a1a")
            )),
            Step("typeVitrine", "Configuration", "options", listOf(
                StepOption("avec_porte", "Misy Varavarana", "Avec porte"),
                StepOption("sans_porte", "Tsy Misy Varavarana", "Sans porte")
            ))
        )
    )

    /* ─── Logique de visibilite des etapes (conditions) ─── */
    fun isVisible(step: Step, raw: Map<String, String>): Boolean {
        val c = step.cond ?: return true
        val cur = raw[c.key]
        if (c.v != null) return cur == c.v
        if (c.notVal != null) return cur != null && cur != c.notVal
        return true
    }

    fun visibleIndices(pid: String, raw: Map<String, String>): List<Int> {
        val list = steps[pid] ?: return emptyList()
        return list.indices.filter { isVisible(list[it], raw) }
    }

    fun nextVisible(pid: String, from: Int, raw: Map<String, String>): Int {
        val list = steps[pid] ?: return -1
        for (i in from + 1 until list.size) if (isVisible(list[i], raw)) return i
        return -1
    }

    fun prevVisible(pid: String, from: Int, raw: Map<String, String>): Int {
        val list = steps[pid] ?: return -1
        for (i in from - 1 downTo 0) if (isVisible(list[i], raw)) return i
        return -1
    }

    /* ════════════ MOTEUR DE PRIX ════════════ */

    private fun prixFenetre(prod: ProductItem): Double {
        val raw = prod.configRaw
        val type = raw["typeFenetre"] ?: ""
        if (type != "coulissante") {
            return fenetreBasePrices[type] ?: fenetreBasePrices["coulissante_deuxieme"]!!
        }
        val sous = raw["sousType"] ?: "deuxieme"
        var price = fenetreBasePrices["coulissante_$sous"] ?: fenetreBasePrices["coulissante_deuxieme"]!!
        val vitre = raw["typeVitre"] ?: ""
        val ep = raw["epaisseur"] ?: ""
        when {
            vitre == "antelio" && ep == "4mm" -> price += fenetreSurcharges["antelio_4mm"] ?: 0.0
            vitre == "antelio" && ep == "5mm" -> price += fenetreSurcharges["antelio_5mm"] ?: 0.0
            vitre == "clair" && ep == "5mm" -> price += fenetreSurcharges["clair_5mm"] ?: 0.0
        }
        return price
    }

    private fun prixBase(prod: ProductItem): Double {
        return if (prod.productId == "fenetre") prixFenetre(prod)
        else autresBasePrice[prod.productId] ?: 0.0
    }

    /** Surface effective en m2 (h x l). */
    private fun effectiveArea(prod: ProductItem, d: Dimension): Double {
        if (d.h <= 0 || d.l <= 0) return 0.0
        return (d.h / 100.0) * (d.l / 100.0)
    }

    /** Prix unitaire calcule automatiquement (nombre brut). */
    fun calcUnitPrice(prod: ProductItem, d: Dimension): Double {
        val price = prixBase(prod)
        return when (prod.productId) {
            "lavarangana" -> if (d.l > 0) (d.l / 100.0) * price else 0.0
            "vitrine" -> if (d.h > 0 && d.l > 0 && d.p > 0)
                (d.h / 100.0) * (d.l / 100.0) * (d.p / 100.0) * price else 0.0
            else -> effectiveArea(prod, d) * price
        }
    }

    /** Prix unitaire effectif : override manuel si present, sinon auto. */
    fun unitPrice(prod: ProductItem, d: Dimension): Double {
        val o = d.puOverride
        return if (o != null && o >= 0) o else calcUnitPrice(prod, d)
    }

    fun rowTotal(prod: ProductItem, d: Dimension): Double = unitPrice(prod, d) * d.qty

    fun productTotal(prod: ProductItem): Double = prod.dimensions.sumOf { rowTotal(prod, it) }
}
