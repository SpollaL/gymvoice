package com.gymvoice.ui

import android.widget.ImageView
import coil.load
import java.io.File

fun ImageView.loadExerciseImage(imageName: String) {
    if (imageName.isEmpty()) {
        setImageDrawable(null)
        return
    }
    if (imageName.startsWith("/")) {
        load(File(imageName)) { crossfade(true) }
    } else {
        load("file:///android_asset/exercises/$imageName") { crossfade(true) }
    }
}
