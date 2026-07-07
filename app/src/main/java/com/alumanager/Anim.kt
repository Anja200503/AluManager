package com.alumanager

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

/** Micro-interactions modernes (retour tactile fluide). */
object Anim {

    /** Réduit légèrement l'objet à l'appui puis rebondit au relâchement. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressScale(vararg views: View) {
        for (v in views) {
            v.setOnTouchListener { view, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN ->
                        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f)
                            .setInterpolator(OvershootInterpolator(2.2f)).setDuration(260).start()
                }
                false // ne consomme pas : le clic reste actif
            }
        }
    }
}
