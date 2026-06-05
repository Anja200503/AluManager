package com.alumanager

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Ecran de lancement (routeur).
 * Etape 1 : on ouvre directement l'accueil (le home).
 * La connexion sera rebranchee a une etape ulterieure.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
