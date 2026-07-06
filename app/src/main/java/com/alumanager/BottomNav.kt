package com.alumanager

import android.content.Intent
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Barre de navigation permanente (icônes + texte) partagée par les écrans principaux.
 * `active` = "home" | "plateaux" | "commande" | "presence" | "kaonty".
 */
object BottomNav {

    private const val ON = 0xFF1565C0.toInt()
    private const val OFF = 0xFF6B7686.toInt()

    // clé -> (id du conteneur, activité cible ou null)
    private data class Tab(val key: String, val id: Int, val cls: Class<*>?)

    private val tabs = listOf(
        Tab("home", R.id.navHome, HomeActivity::class.java),
        Tab("plateaux", R.id.navPlateaux, null),
        Tab("commande", R.id.navCommande, CommandeActivity::class.java),
        Tab("presence", R.id.navPresence, PresenceActivity::class.java),
        Tab("kaonty", R.id.navKaonty, KaontyActivity::class.java)
    )

    fun setup(a: AppCompatActivity, active: String) {
        val fromIdx = tabs.indexOfFirst { it.key == active }
        tabs.forEachIndexed { idx, t ->
            val tab = a.findViewById<LinearLayout>(t.id) ?: return@forEachIndexed
            val on = t.key == active
            (tab.getChildAt(0) as? ImageView)?.setColorFilter(if (on) ON else OFF)
            (tab.getChildAt(1) as? TextView)?.setTextColor(if (on) ON else OFF)
            tab.setBackgroundResource(if (on) R.drawable.nav_tab_active else 0)
            tab.setOnClickListener { if (!on) navigate(a, t, fromIdx, idx) }
        }
    }

    private fun navigate(a: AppCompatActivity, t: Tab, fromIdx: Int, toIdx: Int) {
        if (t.cls == null) {
            Toast.makeText(a, "PLATEAUX : bientôt disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(a, t.cls).addFlags(
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        a.startActivity(i)
        if (toIdx >= fromIdx) {
            a.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            a.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }
}
