package com.steelfire.assault

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Build
import android.view.WindowInsets
import android.view.View

class MainActivity : Activity() {
    private var gameView: GameView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        gameView = GameView(this)
        setContentView(gameView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            gameView?.setWindowInsets(readSafeInsets(insets))
            insets
        }
        window.decorView.requestApplyInsets()
    }

    private fun readSafeInsets(insets: WindowInsets): ViewportTransform.Insets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val system = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return ViewportTransform.Insets(system.left, system.top, system.right, system.bottom)
        }
        @Suppress("DEPRECATION")
        return ViewportTransform.Insets(
            insets.systemWindowInsetLeft,
            insets.systemWindowInsetTop,
            insets.systemWindowInsetRight,
            insets.systemWindowInsetBottom
        )
    }

    override fun onPause() {
        gameView?.pauseForLifecycle()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        gameView?.resumeFromLifecycle()
    }
}

