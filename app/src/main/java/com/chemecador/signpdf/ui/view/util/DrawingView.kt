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


    interface OnDrawListener {
        fun onDrawStateChanged(hasDrawn: Boolean)
    }

    private var drawListener: OnDrawListener? = null
    private var hasDrawn = false
    private var backgroundBitmap: Bitmap? = null
    private val path = Path()
    private val paint: Paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 5f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                performClick()
            }

            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                hasDrawn = true
                drawListener?.onDrawStateChanged(true)

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

    fun setOnDrawListener(listener: OnDrawListener) {
        drawListener = listener
    }

    fun setSignatureColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun clearDrawing() {
        drawListener?.onDrawStateChanged(false)
        hasDrawn = false
        path.reset()
        invalidate()
    }

    fun getBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    fun isEmpty() = !hasDrawn
}

