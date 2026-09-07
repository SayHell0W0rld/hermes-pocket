package com.hermes.mobile.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.hermes.mobile.R
import com.hermes.mobile.data.HermesTab

class TabBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {
    private val strip = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    var onTabSelected: ((HermesTab) -> Unit)? = null
    var onAddClicked: (() -> Unit)? = null

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        addView(strip)
    }

    fun render(tabs: List<HermesTab>, activeId: String?) {
        strip.removeAllViews()
        tabs.forEach { tab -> strip.addView(tabButton(tab, tab.id == activeId)) }
        strip.addView(addButton())
    }

    private fun tabButton(tab: HermesTab, active: Boolean): TextView {
        return TextView(context).apply {
            text = tab.title.ifBlank { tab.id.take(10) }
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            minWidth = dp(72)
            maxWidth = dp(110)
            includeFontPadding = false
            typeface = Typeface.create(Typeface.SANS_SERIF, if (active) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundResource(R.drawable.bg_tab_item)
            setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, 1)
            setTextColor(
                if (active) context.getColor(R.color.hermes_accent)
                else context.getColor(R.color.hermes_text)
            )
            setOnClickListener { onTabSelected?.invoke(tab) }
        }
    }

    private fun addButton(): TextView {
        return TextView(context).apply {
            text = "+"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundResource(R.drawable.bg_tab_add)
            setTextColor(context.getColor(R.color.hermes_text))
            setOnClickListener { onAddClicked?.invoke() }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
