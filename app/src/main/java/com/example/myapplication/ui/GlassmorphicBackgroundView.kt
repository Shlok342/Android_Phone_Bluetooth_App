package com.example.myapplication.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.graphics.toColorInt
import com.example.myapplication.ble.BleState
import com.example.myapplication.classic.ClassicState

class GlassmorphicBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val colorIdle       = "#7C3AED".toColorInt()
    private val colorConnecting = "#D97706".toColorInt()
    private val colorConnected  = "#059669".toColorInt()
    private val colorFailed     = "#DC2626".toColorInt()
    private val colorBg         = "#0D0E11".toColorInt()

    var currentGlowColor: Int = colorIdle
        set(value) {
            if (field != value) {
                field = value
                updateGlowShader()
                invalidate()
            }
        }

    var glowAlpha: Float = 0.32f
        set(value) {
            if (field != value) {
                field = value
                updateGlowShader()
                invalidate()
            }
        }
    private var fromColor        = colorIdle
    private var targetColor      = colorIdle


    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Breathing animator ─────────────────────────────────────────────────
    private val breathingAnimator = ValueAnimator.ofFloat(0.25f, 0.44f).apply {
        duration = 3400L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            glowAlpha = it.animatedValue as Float
            invalidate()
        }
    }

    // ── Color transition animator ──────────────────────────────────────────
    private val colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 750L
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { anim ->
            currentGlowColor = blendColors(fromColor, targetColor, anim.animatedValue as Float)
            invalidate()
        }
    }

    init {
        setBackgroundColor(colorBg)
    }

    // ── Public state transitions ───────────────────────────────────────────
    fun transitionToState(state: BleState) = transitionColor(
        when (state) {
            BleState.IDLE, BleState.DISCONNECTED                           -> colorIdle
            BleState.CONNECTING, BleState.BONDING,
            BleState.DISCOVERING_SERVICES                                  -> colorConnecting
            BleState.READY                                                 -> colorConnected
            BleState.FAILED                                                -> colorFailed
        }
    )

    fun transitionToClassicState(state: ClassicState) = transitionColor(
        when (state) {
            ClassicState.IDLE, ClassicState.DISCONNECTED  -> colorIdle
            ClassicState.CONNECTING                       -> colorConnecting
            ClassicState.CONNECTED                        -> colorConnected
            is ClassicState.RECONNECTING                  -> colorConnecting
            is ClassicState.FAILED                        -> colorFailed
        }
    )

    private fun transitionColor(newColor: Int) {
        if (newColor == targetColor) return
        fromColor   = currentGlowColor
        targetColor = newColor
        if (colorAnimator.isRunning) colorAnimator.cancel()
        colorAnimator.start()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        breathingAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breathingAnimator.cancel()
        colorAnimator.cancel()
    }

    // ── Draw ──────────────────────────────────────────────────────────────
    // 1. Preallocate your arrays and coordinates at the class level!
    private val gradientPositions = floatArrayOf(0f, 0.42f, 1f)
    private val gradientColors = IntArray(3)

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    // 2. Move size calculations to onSizeChanged so they run ONLY when dimensions flip
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w * 0.50f
        cy = h * 0.30f
        radius = w * 0.70f
        updateGlowShader()
    }

    // 3. Use custom setters to regenerate the shader ONLY when variables change


    // 4. The isolated setup function
    private fun updateGlowShader() {
        // Safety check: don't build the shader if the view hasn't laid out yet
        if (radius <= 0f) return

        gradientColors[0] = setAlpha(currentGlowColor, (glowAlpha * 255).toInt())
        gradientColors[1] = setAlpha(currentGlowColor, (glowAlpha * 70).toInt())
        gradientColors[2] = Color.TRANSPARENT

        glowPaint.shader = RadialGradient(
            cx, cy, radius,
            gradientColors,
            gradientPositions,
            Shader.TileMode.CLAMP
        )
    }

    // 5. Look how fast and lightweight this is now!
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(cx, cy, radius, glowPaint)
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inv = 1f - ratio
        return Color.argb(
            (Color.alpha(from) * inv + Color.alpha(to) * ratio).toInt(),
            (Color.red(from)   * inv + Color.red(to)   * ratio).toInt(),
            (Color.green(from) * inv + Color.green(to) * ratio).toInt(),
            (Color.blue(from)  * inv + Color.blue(to)  * ratio).toInt()
        )
    }

    private fun setAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}