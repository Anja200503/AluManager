package com.alumanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.alumanager.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = auth.currentUser
        val name = user?.displayName ?: "Utilisateur"
        val societe = decodeSociete(user?.photoUrl)

        binding.heroGreeting.text = "Bonjour, $name"
        if (societe.isNotEmpty()) binding.heroCompany.text = societe
        binding.btnAccount.text = name.trim().take(1).uppercase().ifBlank { "A" }

        // Date du jour en francais
        val fmt = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH)
        binding.heroDate.text = fmt.format(Date()).replaceFirstChar { it.uppercase() }

        // Panneau compte / deconnexion
        binding.btnAccount.setOnClickListener { showAccountDialog(name, user?.email ?: "—", societe) }

        // Navigation permanente
        BottomNav.setup(this, "home")
    }

    private fun decodeSociete(photoUrl: Uri?): String {
        val s = photoUrl?.toString() ?: return ""
        return if (s.startsWith("https://societe/")) {
            Uri.decode(s.removePrefix("https://societe/"))
        } else s
    }

    private fun showAccountDialog(name: String, email: String, societe: String) {
        val msg = buildString {
            append("$name\n\n")
            append("$email")
            if (societe.isNotEmpty()) append("\n\n$societe")
        }
        AlertDialog.Builder(this, R.style.NeonDialog)
            .setTitle("Mon compte")
            .setMessage(msg)
            .setPositiveButton("Deconnexion") { _, _ -> logout() }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun logout() {
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finishAffinity()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
