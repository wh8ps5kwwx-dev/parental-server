package com.example.myrana.parent.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * رسم بياني بسيط بدون مكتبات خارجية — أعمدة عمودية مع تسميات.
 */
class SimpleBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class BarEntry(
        val label: String,
        val value: Float,
        val color: Int = Color.parseColor("#4CAF50"),
    )

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F5")
    }

    private var entries: List<BarEntry> = emptyList()
    private var unitSuffix: String = ""

    fun setData(items: List<BarEntry>, unit: String = "") {
        entries = items
        unitSuffix = unit
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("لا توجد بيانات", width / 2f, height / 2f, labelPaint)
            return
        }

        val pad = 16f
        val labelH = 48f
        val valueH = 32f
        val chartTop = pad + valueH
        val chartBottom = height - pad - labelH
        val chartH = max(1f, chartBottom - chartTop)
        val maxVal = max(1f, entries.maxOf { it.value })
        val barW = (width - pad * 2) / entries.size.coerceAtLeast(1)
        val gap = barW * 0.15f
        val actualBarW = barW - gap

        canvas.drawRect(0f, chartTop, width.toFloat(), chartBottom, bgPaint)

        entries.forEachIndexed { i, entry ->
            val left = pad + i * barW + gap / 2f
            val right = left + actualBarW
            val barHeight = (entry.value / maxVal) * chartH
            val top = chartBottom - barHeight

            barPaint.color = entry.color
            canvas.drawRoundRect(RectF(left, top, right, chartBottom), 8f, 8f, barPaint)

            val valueText = if (unitSuffix.isNotEmpty()) {
                "${entry.value.toInt()}$unitSuffix"
            } else {
                entry.value.toInt().toString()
            }
            canvas.drawText(valueText, (left + right) / 2f, top - 6f, valuePaint)

            val shortLabel = if (entry.label.length > 6) entry.label.take(5) + "…" else entry.label
            canvas.drawText(shortLabel, (left + right) / 2f, height - pad, labelPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredH = (220 * resources.displayMetrics.density).toInt()
        val h = resolveSize(desiredH, heightMeasureSpec)
        val w = resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec)
        setMeasuredDimension(w, h)
    }
}
