package com.alumanager

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Ecran de lancement (routeur).
 * Si l'utilisateur est deja connecte -> Accueil.
 * Sinon -> Connexion.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        val next = if (user != null) HomeActivity::class.java else LoginActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }
}
