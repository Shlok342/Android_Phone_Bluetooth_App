package com.example.myapplication.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.example.myapplication.R
import com.example.myapplication.models.dp
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class DeviceSearchSheet(
    context: Context,
    private val onFilter: (query: String, byMac: Boolean) -> Unit,
    private val onDismissed: (hasQuery: Boolean) -> Unit
) : BottomSheetDialog(context) {

    private var searchByMac = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = context

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(ctx), 24.dp(ctx), 24.dp(ctx), 40.dp(ctx))
        }

        // ── Header ────────────────────────────────────────────────────────────
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val title = TextView(ctx).apply {
            text = ctx.getString(R.string.search_devices)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ctx.getColor(R.color.color_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val closeBtn = MaterialButton(ctx).apply {
            text = "✕"
            textSize = 14f
            setTextColor(ctx.getColor(R.color.color_text_secondary))
            setBackgroundResource(R.drawable.bg_button_glass)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            stateListAnimator = null
            setPadding(14.dp(ctx), 0, 14.dp(ctx), 0)
        }
        headerRow.addView(title)
        headerRow.addView(closeBtn)
        root.addView(headerRow)

        // ── Divider ───────────────────────────────────────────────────────────
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                topMargin = 14.dp(ctx); bottomMargin = 18.dp(ctx)
            }
            setBackgroundColor(ctx.getColor(R.color.color_glass_border))
        })

        // ── Toggle row ────────────────────────────────────────────────────────
        val toggleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16.dp(ctx) }
        }
        val nameBtn = MaterialButton(ctx).apply {
            text = ctx.getString(R.string.search_toggle_name)
            textSize = 13f; letterSpacing = 0.03f
            setTextColor(ctx.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_toggle_active)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            stateListAnimator = null
        }
        val macBtn = MaterialButton(ctx).apply {
            text = ctx.getString(R.string.search_toggle_mac)
            textSize = 13f; letterSpacing = 0.03f
            setTextColor(ctx.getColor(R.color.color_text_secondary))
            setBackgroundResource(R.drawable.bg_button_glass)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            stateListAnimator = null
        }
        toggleRow.addView(nameBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 8.dp(ctx) })
        toggleRow.addView(macBtn, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(toggleRow)

        // ── Search input ──────────────────────────────────────────────────────
        val searchInput = EditText(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 52.dp(ctx)).apply { bottomMargin = 12.dp(ctx) }
            setBackgroundResource(R.drawable.bg_edit_text_luxury)
            backgroundTintList = null
            hint = ctx.getString(R.string.search_hint_name)
            setTextColor(ctx.getColor(R.color.color_text_primary))
            setHintTextColor(ctx.getColor(R.color.color_text_tertiary))
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18.dp(ctx), 0, 18.dp(ctx), 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            maxLines = 1
        }
        root.addView(searchInput)

        // ── MAC search button (hidden in Name mode) ───────────────────────────
        val searchBtn = MaterialButton(ctx).apply {
            text = ctx.getString(R.string.search_button_label)
            textSize = 13f; letterSpacing = 0.03f
            setTextColor(ctx.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp(ctx) }
            visibility = View.GONE
        }
        root.addView(searchBtn)

        // ── Toggle logic ──────────────────────────────────────────────────────
        fun activateName() {
            searchByMac = false
            nameBtn.setBackgroundResource(R.drawable.bg_toggle_active)
            nameBtn.setTextColor(ctx.getColor(R.color.color_text_primary))
            macBtn.setBackgroundResource(R.drawable.bg_button_glass)
            macBtn.setTextColor(ctx.getColor(R.color.color_text_secondary))
            searchBtn.visibility = View.GONE
            searchInput.hint = ctx.getString(R.string.search_hint_name)
            onFilter(searchInput.text.toString().trim(), false)
        }

        fun activateMac() {
            searchByMac = true
            macBtn.setBackgroundResource(R.drawable.bg_toggle_active)
            macBtn.setTextColor(ctx.getColor(R.color.color_text_primary))
            nameBtn.setBackgroundResource(R.drawable.bg_button_glass)
            nameBtn.setTextColor(ctx.getColor(R.color.color_text_secondary))
            searchBtn.visibility = View.VISIBLE
            searchInput.hint = ctx.getString(R.string.search_hint_mac)
            onFilter("", false)   // clear current filter on mode switch
        }

        nameBtn.setOnClickListener { activateName() }
        macBtn.setOnClickListener { activateMac() }

        // ── Live filter — Name mode only ──────────────────────────────────────
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!searchByMac) onFilter(s?.toString()?.trim() ?: "", false)
            }
        })

        // ── MAC search — explicit trigger only ───────────────────────────────
        searchBtn.setOnClickListener {
            onFilter(searchInput.text.toString().trim(), true)
        }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH && searchByMac) {
                onFilter(searchInput.text.toString().trim(), true); true
            } else false
        }

        // ── Close logic — triggers external clear button appearance ──────────
        closeBtn.setOnClickListener {
            val hasQuery = searchInput.text.toString().trim().isNotEmpty()
            onDismissed(hasQuery)
            dismiss()
        }

        setContentView(root)
    }
}
