package com.whalepet.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.whalepet.R
import kotlin.math.roundToInt

/**
 * 设置界面的构件工厂。
 *
 * 只读行、开关行、滑块行、分段行、输入行与可点行各有固定外观：
 * 凡是可以点击的行都带按下高亮与右侧箭头，只读行不带任何交互暗示。
 * 间距、圆角与字号全部取自 Metrics。
 */
object Ui {

    class InputRow(val view: LinearLayout, val field: EditText, val status: TextView)

    /** 顶栏的滚动边缘材质：顶部实心、向下渐隐，内容从下面穿过时不被硬边切断。 */
    fun scrollEdge(context: Context, colorRes: Int): Drawable {
        val base = ContextCompat.getColor(context, colorRes)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(base, base, base and 0x00FFFFFF)
        )
    }

    /** 卡片：emphasis 用于区分层级，主卡比列表卡厚一档，阴影仍以 unit 为基准。 */
    fun card(context: Context, metrics: Metrics, emphasis: Float = 1f): LinearLayout {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        val background = GradientDrawable()
        background.shape = GradientDrawable.RECTANGLE
        background.cornerRadius = metrics.uf(CARD_RADIUS)
        background.setColor(ContextCompat.getColor(context, R.color.surface_card))
        container.background = background
        container.clipToOutline = true
        container.elevation = metrics.uf(CARD_ELEVATION * emphasis)
        return container
    }

    fun text(
        context: Context,
        metrics: Metrics,
        role: TextRole,
        content: String,
        colorRes: Int = R.color.text_primary
    ): TextView {
        val view = TextView(context)
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.text(role))
        view.typeface = Typefaces.weighted(role.weight)
        view.letterSpacing = role.tracking
        view.includeFontPadding = false
        view.setTextColor(ContextCompat.getColor(context, colorRes))
        view.text = content
        return view
    }

    fun sectionTitle(context: Context, metrics: Metrics, content: String): TextView {
        val view = text(context, metrics, TextRole.SUBHEAD, content, R.color.text_secondary)
        view.setPadding(
            metrics.u(PAGE_MARGIN),
            metrics.u(SECTION_TITLE_TOP),
            metrics.u(PAGE_MARGIN),
            metrics.u(SECTION_TITLE_BOTTOM)
        )
        return view
    }

    fun row(context: Context, metrics: Metrics): LinearLayout {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER_VERTICAL
        container.minimumHeight = metrics.u(ROW_MIN_HEIGHT)
        container.setPadding(
            metrics.u(PAGE_MARGIN),
            metrics.u(ROW_PADDING_V),
            metrics.u(PAGE_MARGIN),
            metrics.u(ROW_PADDING_V)
        )
        return container
    }

    fun textColumn(context: Context, metrics: Metrics, title: String, subtitle: String?): LinearLayout {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        column.addView(text(context, metrics, TextRole.BODY, title))
        if (subtitle != null) {
            val subtitleView = text(context, metrics, TextRole.SUBHEAD, subtitle, R.color.text_secondary)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = metrics.u(SUBTITLE_GAP)
            column.addView(subtitleView, params)
        }
        return column
    }

    fun settingRow(
        context: Context,
        metrics: Metrics,
        title: String,
        subtitle: String? = null,
        control: View? = null
    ): LinearLayout {
        val container = row(context, metrics)
        container.addView(
            textColumn(context, metrics, title, subtitle),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (control != null) {
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.marginStart = metrics.u(CONTROL_GAP)
            container.addView(control, params)
        }
        return container
    }

    /** 只读行：右侧次要文字，没有点击反馈，也没有箭头。 */
    fun valueRow(context: Context, metrics: Metrics, title: String, value: String): LinearLayout {
        return settingRow(
            context,
            metrics,
            title,
            null,
            text(context, metrics, TextRole.BODY, value, R.color.text_secondary)
        )
    }

    /** 可点行：整行响应点击，带按下高亮与右侧箭头，必要时在箭头前显示强调文字。 */
    fun actionRow(
        context: Context,
        metrics: Metrics,
        title: String,
        subtitle: String? = null,
        actionLabel: String? = null,
        onClick: () -> Unit
    ): LinearLayout {
        val container = settingRow(context, metrics, title, subtitle)
        applyRipple(context, container)
        if (actionLabel != null) {
            val label = text(context, metrics, TextRole.BODY, actionLabel, R.color.accent)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.marginStart = metrics.u(CONTROL_GAP)
            container.addView(label, params)
        }
        val chevron = text(context, metrics, TextRole.HEADLINE, CHEVRON, R.color.text_tertiary)
        chevron.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        container.addView(
            chevron,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = metrics.u(CHEVRON_GAP) }
        )
        container.isClickable = true
        container.isFocusable = true
        container.contentDescription =
            listOfNotNull(title, subtitle, actionLabel).joinToString("，")
        container.setOnClickListener { onClick() }
        return container
    }

    fun switchRow(
        context: Context,
        metrics: Metrics,
        title: String,
        subtitle: String? = null,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val toggle = MaterialSwitch(context)
        toggle.isChecked = checked
        toggle.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        toggle.setOnCheckedChangeListener { _, value -> onChange(value) }
        val row = settingRow(context, metrics, title, subtitle, toggle)
        applyRipple(context, row)
        row.isClickable = true
        row.isFocusable = true
        row.contentDescription = listOfNotNull(title, subtitle).joinToString("，")
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        return row
    }

    fun sliderRow(
        context: Context,
        metrics: Metrics,
        title: String,
        valueLabel: String,
        from: Float,
        to: Float,
        step: Float,
        value: Float,
        format: (Float) -> String,
        onValue: (Float) -> Unit
    ): LinearLayout {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.minimumHeight = metrics.u(ROW_MIN_HEIGHT)
        container.setPadding(
            metrics.u(PAGE_MARGIN),
            metrics.u(ROW_PADDING_V),
            metrics.u(PAGE_MARGIN),
            metrics.u(ROW_PADDING_V)
        )
        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(
            text(context, metrics, TextRole.BODY, title),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val valueView = text(context, metrics, TextRole.BODY, valueLabel, R.color.text_secondary)
        header.addView(valueView)
        container.addView(header)

        val slider = Slider(context)
        slider.valueFrom = from
        slider.valueTo = to
        slider.stepSize = step
        slider.value = value.coerceIn(from, to)
        slider.contentDescription = title
        slider.trackActiveTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.accent)
        )
        slider.thumbTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.accent)
        )
        slider.addOnChangeListener { _, updated, _ ->
            valueView.text = format(updated)
            onValue(updated)
        }
        container.addView(slider)
        return container
    }

    fun segmentRow(context: Context, metrics: Metrics, title: String, control: View): LinearLayout {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(
            metrics.u(PAGE_MARGIN),
            metrics.u(ROW_PADDING_V),
            metrics.u(PAGE_MARGIN),
            metrics.u(ROW_PADDING_V)
        )
        container.addView(text(context, metrics, TextRole.BODY, title))
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = metrics.u(SEGMENT_GAP)
        container.addView(control, params)
        return container
    }

    /**
     * 输入行：标题在上，输入框在下，右侧是显示切换与保存状态。
     * 逐字输入时状态显示为未保存，写入完成后变为已保存，用户随时知道密钥是否生效。
     */
    fun inputRow(
        context: Context,
        metrics: Metrics,
        title: String,
        hint: String,
        value: String,
        secret: Boolean,
        status: String,
        onChanged: (String) -> Unit
    ): InputRow {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(
            metrics.u(PAGE_MARGIN),
            metrics.u(ROW_PADDING_V),
            metrics.u(PAGE_MARGIN),
            metrics.u(ROW_PADDING_V)
        )
        container.addView(text(context, metrics, TextRole.SUBHEAD, title, R.color.text_secondary))

        val line = LinearLayout(context)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL

        val field = EditText(context)
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.text(TextRole.BODY))
        field.typeface = Typeface.create(Typefaces.weighted(TextRole.BODY.weight), Typeface.NORMAL)
        field.includeFontPadding = false
        field.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        field.setHintTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
        field.highlightColor = ContextCompat.getColor(context, R.color.accent_soft)
        field.background = null
        field.hint = hint
        field.setText(value)
        field.isSingleLine = true
        field.maxLines = 1
        field.inputType = if (secret) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        var visible = !secret
        val toggle = text(
            context,
            metrics,
            TextRole.SUBHEAD,
            context.getString(
                if (visible) R.string.action_field_hide else R.string.action_field_show
            ),
            R.color.accent
        )
        toggle.setPadding(
            metrics.u(TOGGLE_PADDING),
            metrics.u(TOGGLE_PADDING),
            metrics.u(TOGGLE_PADDING),
            metrics.u(TOGGLE_PADDING)
        )
        applyRipple(context, toggle)
        toggle.setOnClickListener {
            visible = !visible
            field.inputType = if (visible) {
                InputType.TYPE_CLASS_TEXT
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            field.setSelection(field.text.length)
            toggle.text = context.getString(if (visible) R.string.action_field_hide else R.string.action_field_show)
        }
        val statusView = text(context, metrics, TextRole.CAPTION, status, R.color.text_secondary)

        line.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(toggle)
        container.addView(line)

        val statusParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        statusParams.topMargin = metrics.u(STATUS_GAP)
        container.addView(statusView, statusParams)

        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(text: android.text.Editable?) {
                onChanged(text?.toString().orEmpty())
            }
        })
        return InputRow(container, field, statusView)
    }

    fun addDivider(card: LinearLayout, context: Context, metrics: Metrics) {
        val divider = View(context)
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.separator))
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            metrics.dp(HAIRLINE_DP).roundToInt().coerceAtLeast(1)
        )
        params.marginStart = metrics.u(PAGE_MARGIN)
        card.addView(divider, params)
    }

    private fun applyRipple(context: Context, view: View) {
        val value = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            view.setBackgroundResource(value.resourceId)
        }
    }

    const val PAGE_MARGIN = 2f

    private const val CARD_RADIUS = 2.4f
    private const val CARD_ELEVATION = 0.6f
    private const val ROW_MIN_HEIGHT = 6.6f
    private const val ROW_PADDING_V = 1.5f
    private const val SUBTITLE_GAP = 0.4f
    private const val CONTROL_GAP = 1.4f
    private const val CHEVRON_GAP = 1f
    private const val SEGMENT_GAP = 1.2f
    private const val STATUS_GAP = 0.6f
    private const val TOGGLE_PADDING = 1.2f
    private const val SECTION_TITLE_TOP = 3.4f
    private const val SECTION_TITLE_BOTTOM = 1.2f
    private const val HAIRLINE_DP = 0.5f
    private const val CHEVRON = "\u203A"

    /** 行内副标题跟随系统设置变化时更新。 */
    fun updateSubtitle(row: LinearLayout, content: String) {
        val column = row.getChildAt(0) as? LinearLayout ?: return
        val subtitle = column.getChildAt(1) as? TextView ?: return
        subtitle.text = content
    }

}
