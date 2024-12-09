package com.chemecador.signpdf.ui.view.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var hasDrawn = false
    private var backgroundBitmap: Bitmap? = null
    private val path = Path()
    private val paint: Paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 5f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    lateinit var onStartDrawing: (() -> Unit)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                performClick()
                onStartDrawing.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                hasDrawn = true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
            }
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
        canvas.drawPath(path, paint)
    }

    fun setBackgroundBitmap(bitmap: Bitmap?) {
        backgroundBitmap = bitmap
        invalidate()
    }

    fun clearDrawing() {
        path.reset()
        hasDrawn = false
        invalidate()
    }

    fun isEmpty(): Boolean {
        return !hasDrawn
    }

    fun exportBitmap(): Bitmap {
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        backgroundBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.drawPath(path, paint)
        return resultBitmap
    }
}

