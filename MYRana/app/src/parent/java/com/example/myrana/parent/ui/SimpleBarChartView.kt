package com.example.myrana.parent.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * رسم بياني بسيط بدون مكتبات خارجية — أعمدة عمودية مع تسميات وخط مرجعي اختياري (المعدل).
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
    private val referencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B5CF6")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val referenceLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6D28D9")
        textSize = 22f
        textAlign = Paint.Align.LEFT
    }

    private var entries: List<BarEntry> = emptyList()
    private var unitSuffix: String = ""
    private var referenceValue: Float? = null
    private var referenceLabel: String = ""
    private var compactLabels: Boolean = false

    fun setData(items: List<BarEntry>, unit: String = "") {
        entries = items
        unitSuffix = unit
        compactLabels = items.size > 14
        if (compactLabels) {
            labelPaint.textSize = 20f
            valuePaint.textSize = 18f
        } else {
            labelPaint.textSize = 28f
            valuePaint.textSize = 24f
        }
        invalidate()
    }

    fun setReferenceLine(value: Float?, label: String = "") {
        referenceValue = value?.takeIf { it > 0f }
        referenceLabel = label
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
        val labelH = if (compactLabels) 36f else 48f
        val valueH = 32f
        val chartTop = pad + valueH
        val chartBottom = height - pad - labelH
        val chartH = max(1f, chartBottom - chartTop)
        val maxVal = max(1f, entries.maxOf { it.value }.coerceAtLeast(referenceValue ?: 0f))
        val barW = (width - pad * 2) / entries.size.coerceAtLeast(1)
        val gap = barW * 0.15f
        val actualBarW = barW - gap

        canvas.drawRect(0f, chartTop, width.toFloat(), chartBottom, bgPaint)

        referenceValue?.let { ref ->
            val y = chartBottom - (ref / maxVal) * chartH
            canvas.drawLine(pad, y, width - pad, y, referencePaint)
            if (referenceLabel.isNotBlank()) {
                canvas.drawText(referenceLabel, pad + 4f, y - 6f, referenceLabelPaint)
            }
        }

        entries.forEachIndexed { i, entry ->
            val left = pad + i * barW + gap / 2f
            val right = left + actualBarW
            val barHeight = (entry.value / maxVal) * chartH
            val top = chartBottom - barHeight

            barPaint.color = entry.color
            canvas.drawRoundRect(RectF(left, top, right, chartBottom), 8f, 8f, barPaint)

            if (!compactLabels || i % 2 == 0 || entries.size <= 14) {
                val valueText = if (unitSuffix.isNotEmpty()) {
                    "${entry.value.toInt()}$unitSuffix"
                } else {
                    entry.value.toInt().toString()
                }
                if (entry.value > 0f) {
                    canvas.drawText(valueText, (left + right) / 2f, top - 6f, valuePaint)
                }

                val shortLabel = when {
                    entry.label.length > 6 -> entry.label.take(5) + "…"
                    else -> entry.label
                }
                canvas.drawText(shortLabel, (left + right) / 2f, height - pad, labelPaint)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredH = (220 * resources.displayMetrics.density).toInt()
        val h = resolveSize(desiredH, heightMeasureSpec)
        val w = resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec)
        setMeasuredDimension(w, h)
    }
}
