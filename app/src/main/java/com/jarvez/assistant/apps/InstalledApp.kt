package com.jarvez.assistant.apps

import android.content.Intent
import android.graphics.drawable.Drawable

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null,
    val launchIntent: Intent? = null
)
