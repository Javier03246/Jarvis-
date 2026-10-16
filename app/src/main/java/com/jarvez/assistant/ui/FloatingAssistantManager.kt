package com.jarvez.assistant.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.example.R
import com.jarvez.assistant.command.AssistantState
import com.jarvez.assistant.core.JarvezAssistantEngine
import com.jarvez.assistant.util.Logger

class FloatingAssistantManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var isOverlayShowing = false

    fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showFloatingBubble() {
        if (isOverlayShowing || !canDrawOverlay()) return

        try {
            val engine = JarvezAssistantEngine.getInstance(context)

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val sizeDp = 58
            val density = context.resources.displayMetrics.density
            val sizePx = (sizeDp * density).toInt()

            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 30
                y = 200
            }

            val imageView = ImageView(context).apply {
                setImageResource(R.drawable.ic_launcher_foreground)
                setBackgroundResource(R.drawable.ic_launcher_background)
                setPadding(8, 8, 8, 8)
                clipToOutline = true
                elevation = 12f
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isMoving = false

            imageView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isMoving = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(v, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoving) {
                            // Tap on floating bubble: Start listening for command
                            Logger.i("FloatingOverlay", "Floating bubble tapped! Starting voice listening...")
                            engine.startVoiceListening()
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(imageView, params)
            floatingView = imageView
            isOverlayShowing = true
            Logger.i("FloatingOverlay", "Floating bubble added to window")
        } catch (e: Exception) {
            Logger.e("FloatingOverlay", "Error showing floating bubble: ${e.message}", e)
        }
    }

    fun removeFloatingBubble() {
        if (!isOverlayShowing || floatingView == null) return
        try {
            windowManager.removeView(floatingView)
            floatingView = null
            isOverlayShowing = false
            Logger.i("FloatingOverlay", "Floating bubble removed from window")
        } catch (e: Exception) {
            Logger.e("FloatingOverlay", "Error removing floating bubble: ${e.message}", e)
        }
    }
}
