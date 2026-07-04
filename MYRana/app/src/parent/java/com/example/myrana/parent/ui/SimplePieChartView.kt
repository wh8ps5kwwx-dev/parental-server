package com.example.myrana.parent.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/** رسم دائري بسيط — مقارنة نسب (تعليمي / ترفيهي). */
class SimplePieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class Slice(
        val label: String,
        val value: Float,
        val color: Int,
    )

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 26f
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
    }

    private var slices: List<Slice> = emptyList()

    fun setData(items: List<Slice>) {
        slices = items.filter { it.value > 0f }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty()) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("لا توجد بيانات", width / 2f, height / 2f, labelPaint)
            return
        }

        val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
        val pad = 12f
        val legendH = slices.size * 34f + 8f
        val size = max(1f, minOf(width.toFloat(), height.toFloat() - legendH) - pad * 2)
        val cx = width / 2f
        val cy = pad + size / 2f
        val rect = RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)

        var start = -90f
        for (slice in slices) {
            val sweep = (slice.value / total) * 360f
            slicePaint.color = slice.color
            canvas.drawArc(rect, start, sweep, true, slicePaint)
            start += sweep
        }

        val hole = size * 0.42f
        canvas.drawCircle(cx, cy, hole / 2f, holePaint)

        val pct = ((slices.maxByOrNull { it.value }?.value ?: 0f) / total * 100).toInt()
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.color = Color.BLACK
        labelPaint.textSize = 32f
        canvas.drawText("$pct%", cx, cy + 10f, labelPaint)

        var y = cy + size / 2f + pad + 20f
        for (slice in slices) {
            legendPaint.color = slice.color
            canvas.drawCircle(pad + 8f, y - 8f, 8f, legendPaint)
            legendPaint.color = Color.DKGRAY
            val min = slice.value.toInt()
            canvas.drawText("${slice.label} — ${min} د", pad + 24f, y, legendPaint)
            y += 34f
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredH = (260 * resources.displayMetrics.density).toInt()
        val h = resolveSize(desiredH, heightMeasureSpec)
        val w = resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec)
        setMeasuredDimension(w, h)
    }
}
