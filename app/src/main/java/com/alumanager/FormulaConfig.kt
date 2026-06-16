package com.alumanager

import android.content.Context

/**
 * Paramètres des formules de coupe (Zavatra), classés par type de produit.
 * Valeurs par défaut = formules d'origine. Modifiables et persistées (SharedPreferences).
 */
object FormulaConfig {

    data class Param(val key: String, val label: String, val def: Double)
    data class Group(val title: String, val params: List<Param>)

    val GROUPS: List<Group> = listOf(
        Group("Fenêtre/Porte Coulissante", listOf(
            Param("coul_bati_h", "Bati H = H +", 6.0),
            Param("coul_bati_l", "Bati L = L +", 6.0),
            Param("coul_ouvrH_promo", "Ouvrant H (Promo) = H -", 4.1),
            Param("coul_ouvrL_promo_add", "Ouvrant L (Promo) = (L + ?) / div", 1.0),
            Param("coul_ouvr_div", "Diviseur Ouvrant L", 2.0),
            Param("coul_ouvrH_choix", "Ouvrant H (1er/2e) = H -", 5.2),
            Param("coul_vitre", "Vitre = Ouvrant -", 8.3)
        )),
        Group("Fixe", listOf(
            Param("fixe_bati_h", "Bati H = H +", 6.0),
            Param("fixe_bati_l", "Bati L = L +", 6.0),
            Param("fixe_para_h", "Paraclose H = H -", 5.6),
            Param("fixe_para_l", "Paraclose L = L -", 5.6),
            Param("fixe_vitre", "Vitre = Paraclose -", 5.0)
        )),
        Group("Projetant", listOf(
            Param("proj_bati_h", "Bati H = H +", 6.0),
            Param("proj_bati_l", "Bati L = L +", 6.0),
            Param("proj_ouvr_h", "Ouvrant H = H -", 4.6),
            Param("proj_ouvr_l", "Ouvrant L = L -", 4.6),
            Param("proj_vitre", "Vitre = Ouvrant -", 6.5)
        )),
        Group("Naco", listOf(
            Param("naco_bati_h", "Bati H = H +", 6.0),
            Param("naco_bati_l", "Bati L = L +", 6.0),
            Param("naco_lame_h", "Lame H (fixe, cm)", 10.0),
            Param("naco_lame_l", "Lame L = L -", 15.0)
        )),
        Group("Porte 1 vantail", listOf(
            Param("p1v_bati_h", "Bati H = H +", 3.0),
            Param("p1v_bati_l", "Bati L = L +", 6.0),
            Param("p1v_ouvr_h", "Ouvrant H = H -", 2.7),
            Param("p1v_ouvr_l", "Ouvrant L = L -", 4.6),
            Param("p1v_inter", "Intermédiaire = Ouvrant L -", 6.3),
            Param("p1v_bard_seuil", "Bardage : seuil H (cm)", 200.0),
            Param("p1v_bard_court", "Bardage L si H ≤ seuil", 72.0),
            Param("p1v_bard_long", "Bardage L si H > seuil", 82.0),
            Param("p1v_bard_div", "Bardage Qty = arrondi(Inter / ?)", 9.0)
        ))
    )

    private val DEFAULTS: Map<String, Double> = GROUPS.flatMap { it.params }.associate { it.key to it.def }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("formules", Context.MODE_PRIVATE)

    fun value(ctx: Context, key: String): Double =
        prefs(ctx).getFloat(key, (DEFAULTS[key] ?: 0.0).toFloat()).toDouble()

    fun all(ctx: Context): Map<String, Double> = DEFAULTS.keys.associateWith { value(ctx, it) }

    fun save(ctx: Context, values: Map<String, Double>) {
        val e = prefs(ctx).edit()
        for ((k, v) in values) e.putFloat(k, v.toFloat())
        e.apply()
    }

    fun reset(ctx: Context) = prefs(ctx).edit().clear().apply()
}
