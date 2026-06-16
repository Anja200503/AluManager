package com.alumanager

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.alumanager.databinding.ActivityPresenceBinding

/** Page Présence (vide — fonctionnalités retirées). */
class PresenceActivity : AppCompatActivity() {

    private lateinit var b: ActivityPresenceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPresenceBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
    }
}
