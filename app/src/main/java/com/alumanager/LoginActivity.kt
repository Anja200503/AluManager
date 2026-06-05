package com.alumanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.alumanager.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Liste deroulante des societes
        val societes = resources.getStringArray(R.array.societes)
        val adapter = android.widget.ArrayAdapter(this, R.layout.spinner_item, societes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.regSociete.adapter = adapter

        // Onglets connexion / inscription
        binding.tabLogin.setOnClickListener { switchTab(true) }
        binding.tabRegister.setOnClickListener { switchTab(false) }

        binding.btnLogin.setOnClickListener { doLogin() }
        binding.btnRegister.setOnClickListener { doRegister() }

        switchTab(true)
    }

    private fun switchTab(isLogin: Boolean) {
        binding.formLogin.visibility = if (isLogin) View.VISIBLE else View.GONE
        binding.formRegister.visibility = if (isLogin) View.GONE else View.VISIBLE
        binding.tabLogin.isSelected = isLogin
        binding.tabRegister.isSelected = !isLogin
        binding.tabLogin.setBackgroundResource(
            if (isLogin) R.drawable.tab_active else android.R.color.transparent
        )
        binding.tabRegister.setBackgroundResource(
            if (!isLogin) R.drawable.tab_active else android.R.color.transparent
        )
        hideError()
    }

    private fun showError(msg: String) {
        binding.authError.text = msg
        binding.authError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.authError.visibility = View.GONE
    }

    private fun mapError(code: String?, fallback: String): String = when (code) {
        "ERROR_INVALID_EMAIL" -> "Adresse email invalide."
        "ERROR_USER_NOT_FOUND" -> "Aucun compte trouve avec cet email."
        "ERROR_WRONG_PASSWORD" -> "Mot de passe incorrect."
        "ERROR_INVALID_CREDENTIAL" -> "Email ou mot de passe incorrect."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Cet email est deja utilise."
        "ERROR_WEAK_PASSWORD" -> "Le mot de passe doit contenir au moins 6 caracteres."
        "ERROR_TOO_MANY_REQUESTS" -> "Trop de tentatives. Reessaie plus tard."
        "ERROR_NETWORK_REQUEST_FAILED" -> "Erreur reseau. Verifie ta connexion."
        else -> fallback
    }

    private fun setLoadingLogin(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = if (loading) "Connexion..." else "Se connecter"
    }

    private fun setLoadingRegister(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.btnRegister.text = if (loading) "Creation..." else "Creer mon compte"
    }

    private fun doLogin() {
        hideError()
        val email = binding.loginEmail.text.toString().trim()
        val pass = binding.loginPassword.text.toString()
        if (email.isEmpty() || pass.isEmpty()) {
            showError("Remplissez tous les champs."); return
        }
        setLoadingLogin(true)
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { goHome() }
            .addOnFailureListener { e ->
                setLoadingLogin(false)
                val code = (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
                showError(mapError(code, "Erreur : ${e.localizedMessage}"))
            }
    }

    private fun doRegister() {
        hideError()
        val name = binding.regName.text.toString().trim()
        val email = binding.regEmail.text.toString().trim()
        val societe = binding.regSociete.selectedItem?.toString() ?: ""
        val pass = binding.regPassword.text.toString()
        val confirm = binding.regConfirm.text.toString()

        if (name.isEmpty() || email.isEmpty()) {
            showError("Remplissez tous les champs."); return
        }
        if (binding.regSociete.selectedItemPosition == 0) {
            showError("Veuillez choisir votre societe / lieu."); return
        }
        if (pass.length < 6) {
            showError("Le mot de passe doit contenir au moins 6 caracteres."); return
        }
        if (pass != confirm) {
            showError("Les mots de passe ne correspondent pas."); return
        }

        setLoadingRegister(true)
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .setPhotoUri(android.net.Uri.parse("https://societe/${android.net.Uri.encode(societe)}"))
                    .build()
                result.user?.updateProfile(profile)
                    ?.addOnCompleteListener { goHome() }
                    ?: goHome()
            }
            .addOnFailureListener { e ->
                setLoadingRegister(false)
                val code = (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
                showError(mapError(code, "Erreur : ${e.localizedMessage}"))
            }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
