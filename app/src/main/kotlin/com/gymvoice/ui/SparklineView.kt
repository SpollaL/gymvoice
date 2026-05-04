package com.gymvoice.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.gymvoice.R

class SparklineView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        var dataPoints: List<Float> = emptyList()
            set(value) {
                field = value
                invalidate()
            }

        var trendUp: Boolean? = null
            set(value) {
                field = value
                updateColors()
                invalidate()
            }

        private val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 3f
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }

        private val dotPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        private val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        private val emptyPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 36f
            }

        init {
            updateColors()
        }

        private fun updateColors() {
            val lineColor =
                when (trendUp) {
                    true -> ContextCompat.getColor(context, R.color.green)
                    false -> ContextCompat.getColor(context, R.color.red)
                    null -> ContextCompat.getColor(context, R.color.mauve)
                }
            linePaint.color = lineColor
            dotPaint.color = lineColor
            fillPaint.color = lineColor and 0x00FFFFFF or 0x22000000
            emptyPaint.color = ContextCompat.getColor(context, R.color.overlay0)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (dataPoints.isEmpty()) {
                canvas.drawText("no data", width / 2f, height / 2f + 12f, emptyPaint)
                return
            }
            if (dataPoints.size == 1) {
                canvas.drawCircle(width / 2f, height / 2f, 6f, dotPaint)
                return
            }

            val pad = 20f
            val w = width.toFloat() - pad * 2
            val h = height.toFloat() - pad * 2
            val minVal = dataPoints.min()
            val maxVal = dataPoints.max()
            val range = maxVal - minVal

            fun xOf(i: Int) = pad + i * w / (dataPoints.size - 1)

            fun yOf(v: Float) = pad + h - if (range < 0.001f) h / 2f else (v - minVal) / range * h

            val fill =
                Path().apply {
                    moveTo(xOf(0), height.toFloat())
                    lineTo(xOf(0), yOf(dataPoints[0]))
                    for (i in 1 until dataPoints.size) lineTo(xOf(i), yOf(dataPoints[i]))
                    lineTo(xOf(dataPoints.size - 1), height.toFloat())
                    close()
                }
            canvas.drawPath(fill, fillPaint)

            val line =
                Path().apply {
                    moveTo(xOf(0), yOf(dataPoints[0]))
                    for (i in 1 until dataPoints.size) lineTo(xOf(i), yOf(dataPoints[i]))
                }
            canvas.drawPath(line, linePaint)

            for (i in dataPoints.indices) {
                canvas.drawCircle(xOf(i), yOf(dataPoints[i]), 5f, dotPaint)
            }
        }
    }
