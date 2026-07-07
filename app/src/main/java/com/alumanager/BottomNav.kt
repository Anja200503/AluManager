package com.alumanager

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Barre de navigation permanente (icônes + texte) partagée par les écrans principaux.
 * L'animation fait glisser seulement le CONTENU (la barre du bas reste fixe).
 * `active` = "home" | "plateaux" | "commande" | "presence" | "kaonty".
 */
object BottomNav {

    const val EXTRA_DIR = "nav_slide_dir"
    private const val ON = 0xFF1565C0.toInt()
    private const val OFF = 0xFF6B7686.toInt()

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
        animateEnter(a)
    }

    /** Ouvre une activité en glissant le contenu (barre fixe). dir = +1 (droite) / -1 (gauche). */
    fun open(a: AppCompatActivity, cls: Class<*>, dir: Int) {
        a.startActivity(Intent(a, cls).putExtra(EXTRA_DIR, dir))
        a.overridePendingTransition(0, 0)
    }

    private fun navigate(a: AppCompatActivity, t: Tab, fromIdx: Int, toIdx: Int) {
        if (t.cls == null) {
            Toast.makeText(a, "PLATEAUX : bientôt disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = if (toIdx >= fromIdx) 1 else -1
        a.startActivity(
            Intent(a, t.cls)
                .putExtra(EXTRA_DIR, dir)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        a.overridePendingTransition(0, 0)
    }

    /** À appeler depuis onNewIntent pour rejouer le glissement sur un onglet déjà ouvert. */
    fun reenter(a: AppCompatActivity) = animateEnter(a)

    /** Glisse le contenu (tous les frères de la barre) sans bouger la barre. */
    private fun animateEnter(a: AppCompatActivity) {
        val dir = a.intent.getIntExtra(EXTRA_DIR, 0)
        if (dir == 0) return
        a.intent.removeExtra(EXTRA_DIR)
        val bar = a.findViewById<View>(R.id.bottomNav) ?: return
        val root = bar.parent as? ViewGroup ?: return
        val w = a.resources.displayMetrics.widthPixels.toFloat()
        for (i in 0 until root.childCount) {
            val ch = root.getChildAt(i)
            if (ch === bar) continue
            ch.translationX = dir * w
            ch.animate().translationX(0f).setDuration(300)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }
}
