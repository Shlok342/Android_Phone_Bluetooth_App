package com.example.myapplication.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.myapplication.R
import com.example.myapplication.models.FilterType
import com.example.myapplication.models.dp
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class DeviceFilterSheet(
    context: Context,
    private val currentFilter: FilterType,
    private val onFilterSelected: (FilterType) -> Unit
) : BottomSheetDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = context

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(ctx), 24.dp(ctx), 24.dp(ctx), 40.dp(ctx))
        }

        root.addView(TextView(ctx).apply {
            text = "Filter Devices"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ctx.getColor(R.color.color_text_primary))
        })

        // Divider
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                topMargin = 14.dp(ctx)
                bottomMargin = 16.dp(ctx)
            }
            setBackgroundColor(ctx.getColor(R.color.color_glass_border))
        })

        fun addOption(label: String, type: FilterType) {
            val isActive = currentFilter == type
            root.addView(MaterialButton(ctx).apply {
                text = label
                textSize = 14f
                letterSpacing = 0.02f
                isAllCaps = false
                setTextColor(ctx.getColor(if (isActive) R.color.color_text_primary else R.color.color_text_secondary))
                setBackgroundResource(if (isActive) R.drawable.bg_toggle_active else R.drawable.bg_button_glass)
                minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
                stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = 10.dp(ctx)
                }
                setOnClickListener { onFilterSelected(type); dismiss() }
            })
        }

        addOption("💾  Saved Devices", FilterType.SAVED)
        addOption("⭐  Favourites",     FilterType.FAVORITES)
        addOption("📡  Nearby Only",    FilterType.NEARBY)

        // "Clear Filter" row — only when a filter is active
        if (currentFilter != FilterType.NONE) {
            root.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                    topMargin = 6.dp(ctx); bottomMargin = 12.dp(ctx)
                }
                setBackgroundColor(ctx.getColor(R.color.color_glass_border))
            })
            root.addView(MaterialButton(ctx).apply {
                text = "✕  Clear Filter"
                textSize = 13f
                isAllCaps = false
                setTextColor(ctx.getColor(R.color.color_text_secondary))
                setBackgroundResource(R.drawable.bg_button_glass)
                minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
                stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                setOnClickListener { onFilterSelected(FilterType.NONE); dismiss() }
            })
        }

        setContentView(root)
    }
}