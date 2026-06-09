package com.example.myapplication.main_activity_helpers// Update with your actual package name
import com.example.myapplication.R
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class BluetoothBlockerOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    // Callback function to handle the button click in MainActivity
    var onEnableBtClick: (() -> Unit)? = null

    init {
        // 1. Configure the outer FrameLayout container
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(0xCC000000.toInt())
        isClickable = true
        isFocusable = true // Ensures keys/d-pad interactions are also swallowed

        // 2. Build and style the MaterialButton
        val enableBtButton = MaterialButton(context).apply {
            // Using context-safe string/color fetching since we aren't in an Activity
            text = context.getString(R.string.enable_bluetooth)
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            stateListAnimator = null

            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }

            setOnClickListener {
                onEnableBtClick?.invoke()
            }
        }

        // 3. Attach the button to this FrameLayout
        addView(enableBtButton)
    }
}
