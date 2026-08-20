package com.hackerman.sohacksrev2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Circular map overlay with a long-press move/resize mode. */
@SuppressLint("ClickableViewAccessibility")
class QuickBubbleView(context: Context) : AppCompatTextView(context) {
    var onExecute: (() -> Unit)? = null
    var onEditModeChanged: ((Boolean) -> Unit)? = null
    var onGeometryChanged: ((positionX: Float, positionY: Float, sizeDp: Int) -> Unit)? = null

    private var bubbleColor = QuickBubble.DEFAULT_COLOR
    private var editing = false
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var pinchStartDistance = 0f
    private var pinchStartSize = 0
    private var geometryChanged = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            if (editing) {
                setEditing(false)
                publishGeometry()
            } else {
                performClick()
                onExecute?.invoke()
            }
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            if (!editing) setEditing(true)
        }
    })

    init {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        maxLines = 2
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        elevation = 6.dp.toFloat()
        setPadding(5.dp, 5.dp, 5.dp, 5.dp)
    }

    fun bind(bubble: QuickBubble) {
        text = bubble.name
        contentDescription = "Schnelloption ${bubble.name}"
        bubbleColor = bubble.color
        val size = bubble.sizeDp.dp
        layoutParams = ViewGroup.LayoutParams(size, size)
        updateBackground()
        post { placeAt(bubble.positionX, bubble.positionY) }
    }

    fun setEditing(enabled: Boolean) {
        if (editing == enabled) return
        editing = enabled
        updateBackground()
        animate().scaleX(if (enabled) 1.06f else 1f)
            .scaleY(if (enabled) 1.06f else 1f)
            .setDuration(120L)
            .start()
        onEditModeChanged?.invoke(enabled)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                geometryChanged = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (editing && event.pointerCount >= 2) {
                pinchStartDistance = pointerDistance(event)
                pinchStartSize = width
            }
            MotionEvent.ACTION_MOVE -> if (editing) {
                if (event.pointerCount >= 2 && pinchStartDistance > 0f) {
                    resizeTo((pinchStartSize * pointerDistance(event) / pinchStartDistance).roundToInt())
                } else {
                    moveBy(event.rawX - lastRawX, event.rawY - lastRawY)
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                }
                geometryChanged = true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                pinchStartDistance = 0f
                lastRawX = event.rawX
                lastRawY = event.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (editing && geometryChanged) publishGeometry()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun moveBy(deltaX: Float, deltaY: Float) {
        val container = parent as? View ?: return
        x = (x + deltaX).coerceIn(0f, (container.width - width).coerceAtLeast(0).toFloat())
        y = (y + deltaY).coerceIn(0f, (container.height - height).coerceAtLeast(0).toFloat())
    }

    private fun resizeTo(requestedPixels: Int) {
        val container = parent as? View ?: return
        val size = requestedPixels.coerceIn(
            QuickBubble.MIN_SIZE_DP.dp,
            minOf(QuickBubble.MAX_SIZE_DP.dp, container.width, container.height)
        )
        val centerX = x + width / 2f
        val centerY = y + height / 2f
        layoutParams = layoutParams.apply {
            width = size
            height = size
        }
        x = (centerX - size / 2f).coerceIn(0f, (container.width - size).coerceAtLeast(0).toFloat())
        y = (centerY - size / 2f).coerceIn(0f, (container.height - size).coerceAtLeast(0).toFloat())
    }

    private fun placeAt(positionX: Float, positionY: Float) {
        val container = parent as? View ?: return
        x = positionX.coerceIn(0f, 1f) * (container.width - width).coerceAtLeast(0)
        y = positionY.coerceIn(0f, 1f) * (container.height - height).coerceAtLeast(0)
    }

    private fun publishGeometry() {
        val container = parent as? View ?: return
        val freeWidth = (container.width - width).coerceAtLeast(1)
        val freeHeight = (container.height - height).coerceAtLeast(1)
        onGeometryChanged?.invoke(
            (x / freeWidth).coerceIn(0f, 1f),
            (y / freeHeight).coerceIn(0f, 1f),
            (width / resources.displayMetrics.density).roundToInt()
        )
    }

    private fun updateBackground() {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bubbleColor)
            setStroke(
                if (editing) 3.dp else 1.dp,
                if (editing) Color.WHITE else ColorUtils.setAlphaComponent(Color.WHITE, 70)
            )
        }
        setTextColor(if (ColorUtils.calculateLuminance(bubbleColor) > 0.48) Color.BLACK else Color.WHITE)
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).roundToInt()
}
