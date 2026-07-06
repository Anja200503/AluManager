package com.alumanager

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Barre de navigation permanente (texte seul) partagée par les écrans principaux.
 * `active` = "plateaux" | "commande" | "presence" | "kaonty" | "" (accueil).
 */
object BottomNav {

    fun setup(a: AppCompatActivity, active: String) {
        wire(a, R.id.navPlateaux, "plateaux", active)
        wire(a, R.id.navCommande, "commande", active)
        wire(a, R.id.navPresence, "presence", active)
        wire(a, R.id.navKaonty, "kaonty", active)
    }

    private fun wire(a: AppCompatActivity, id: Int, key: String, active: String) {
        val tv = a.findViewById<TextView>(id) ?: return
        if (key == active) {
            tv.setTextColor(Color.parseColor("#1565C0"))
            tv.setTypeface(Typeface.DEFAULT_BOLD)
            tv.setBackgroundResource(R.drawable.nav_tab_active)
        } else {
            tv.setTextColor(Color.parseColor("#6B7686"))
            tv.setTypeface(Typeface.DEFAULT_BOLD)
            tv.setBackgroundResource(0)
        }
        tv.setOnClickListener { if (key != active) navigate(a, key) }
    }

    private fun navigate(a: AppCompatActivity, key: String) {
        val cls = when (key) {
            "commande" -> CommandeActivity::class.java
            "presence" -> PresenceActivity::class.java
            "kaonty" -> KaontyActivity::class.java
            else -> null
        }
        if (cls == null) {
            Toast.makeText(a, "PLATEAUX : bientôt disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(a, cls).addFlags(
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        a.startActivity(i)
        a.overridePendingTransition(0, 0)
    }
}
