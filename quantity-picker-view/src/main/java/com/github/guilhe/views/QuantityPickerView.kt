package com.github.guilhe.views

import android.animation.Animator
import android.animation.FloatEvaluator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.graphics.Paint.Align
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Suppress("unused", "MemberVisibilityCanBePrivate", "SameParameterValue")
class QuantityPickerView : View {

    enum class Button {
        ADD, REMOVE
    }

    private val defaultMaxAlpha = 255
    private val defaultBackgroundColor = Color.rgb(0xE5, 0xF0, 0xC7)
    private var defaultInterpolator: TimeInterpolator = DecelerateInterpolator()
    private var initializing: Boolean = false
    private val darkenColorFilter: ColorFilter = PorterDuffColorFilter(0x48000000, PorterDuff.Mode.SRC_ATOP)
    private var pickerBackgroundColor: Int = 0
    private var labelAlpha: Int = defaultMaxAlpha
    private var isClosing: Boolean = false
    private var isAnimating: Boolean = false
    private var translateAnimator: ValueAnimator? = null
    private var alphaAnimator: ValueAnimator? = null
    private var btnRemovePosition: Float = 0f

    private lateinit var textLabelPaint: Paint
    private lateinit var pickerPaint: Paint
    private lateinit var btnAddPaint: Paint
    private lateinit var btnRemovePaint: Paint
    private lateinit var backgroundLineRect: RectF
    private lateinit var removeButtonRect: RectF
    private lateinit var addButtonRect: RectF
    private lateinit var btnRemove: Bitmap
    private lateinit var btnAdd: Bitmap

    private val clickActionThreshold = 50
    private var startX: Float = 0f
    private var startY: Float = 0f
    private var pressedButton: Button? = null

    interface QuantityPickerViewChangeListener {
        fun onChanged(view: QuantityPickerView, value: Int)
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setupView(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setupView(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        setupView(context, attrs)
    }

    private fun setupView(context: Context, attrs: AttributeSet) {
        initializing = true
        var customTypeFace: Typeface? = null
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.QuantityPickerView, 0, 0)
        try {
            min = a.getInt(R.styleable.QuantityPickerView_min, 0)
            max = a.getInt(R.styleable.QuantityPickerView_max, Integer.MAX_VALUE)
            value = a.getInt(R.styleable.QuantityPickerView_value, min)
            textLabelSize = a.getDimension(R.styleable.QuantityPickerView_textLabelSize, textLabelSize.toFloat()).toInt()
            textLabelFormatter = a.getString(R.styleable.QuantityPickerView_textLabelFormatter) ?: textLabelFormatter
            val fontResId = a.getResourceId(R.styleable.QuantityPickerView_textLabelFont, -1)
            if (fontResId != -1) {
                try {
                    customTypeFace = ResourcesCompat.getFont(context, fontResId)
                } catch (e: Exception) {
                }
            }
            pickerBackgroundColor = a.getColor(R.styleable.QuantityPickerView_backgroundColor, -1)
            if (pickerBackgroundColor == -1) {
                pickerBackgroundColor = defaultBackgroundColor
            }
            setButtonRemove(a.getResourceId(R.styleable.QuantityPickerView_btnRemove, R.drawable.default_btn_remove))
            setButtonAdd(a.getResourceId(R.styleable.QuantityPickerView_btnAdd, R.drawable.default_btn_add))
            autoToggle = a.getBoolean(R.styleable.QuantityPickerView_autoToggle, true)
            isOpen = a.getBoolean(R.styleable.QuantityPickerView_isOpen, false)
        } finally {
            a.recycle()
        }

        labelAlpha = if (isOpen) defaultMaxAlpha else 0
        isFocusable = true
        isFocusableInTouchMode = true

        backgroundLineRect = RectF()
        textLabelPaint = Paint(ANTI_ALIAS_FLAG).apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.BLACK
            textSize = textLabelSize.toFloat()
            typeface = customTypeFace ?: this.typeface
        }
        pickerPaint = Paint(ANTI_ALIAS_FLAG).apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        btnRemovePaint = Paint(ANTI_ALIAS_FLAG).apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        btnAddPaint = Paint(ANTI_ALIAS_FLAG).apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
    }

    //region setters & getters
    fun setLimits(min: Int, max: Int) {
        this.min = min
        this.max = max
    }

    var max: Int = Integer.MAX_VALUE
        set(value) {
            field = value
            invalidate()
        }

    var min: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var value: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var showLabel: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    // If you want to define something like "x unit." call this method with formatter = "%s unit."
    var textLabelFormatter: String = "%s"
        set(value) {
            field = value
            invalidate()
        }

    // Values in DP
    var textLabelSize: Int = dpToPx(20)
        set(value) {
            field = value
            invalidate()
        }

    // When minimum value is reached the view closes automatically
    var autoToggle: Boolean = true

    var isOpen: Boolean = false
        private set

    var valueListener: QuantityPickerViewChangeListener? = null
    //endregion

    //region background
    /**
     * Values in HEX, ex 0xFF
     *
     * @param red   hex value for red
     * @param green hex value for green
     * @param blue  hex value for blue
     */
    fun setBackgroundColor(red: Int, green: Int, blue: Int) {
        setBackgroundColor(0xFF, red, green, blue)
    }

    /**
     * Values in HEX, ex 0xFF
     *
     * @param alpha hex value for alpha
     * @param red   hex value for red
     * @param green hex value for green
     * @param blue  hex value for blue
     */
    fun setBackgroundColor(alpha: Int, red: Int, green: Int, blue: Int) {
        pickerBackgroundColor = Color.argb(alpha, red, green, blue)
        invalidate()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun setBackgroundColor(color: Color) {
        setBackgroundColor(color.toArgb())
    }

    /**
     * You can simulate the use of this method with by calling [.setBackgroundColor] with ContextCompat:
     * setBackgroundColor(ContextCompat.getColor(resId));
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    fun setBackgroundColorResource(@ColorRes resId: Int) {
        setBackgroundColor(context.getColor(resId))
    }

    override fun setBackgroundColor(color: Int) {
        pickerBackgroundColor = color
        invalidate()
    }
    //endregion background

    //region buttons
    fun setButtonRemoveBitmap(bitmap: Bitmap) {
        setButtonRemoveBitmap(bitmap, true)
    }

    private fun setButtonRemoveBitmap(bitmap: Bitmap, requestLayout: Boolean) {
        btnRemove = bitmap
        if (requestLayout) {
            requestLayout()
        }
    }

    private fun setButtonRemove(@DrawableRes resId: Int) {
        val d = ContextCompat.getDrawable(context, resId)
        d?.let {
            btnRemove = Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
            it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
            it.draw(Canvas(btnRemove))
            setButtonAddBitmap(btnRemove)
        }
    }

    fun setButtonAddBitmap(bitmap: Bitmap) {
        setButtonAddBitmap(bitmap, true)
    }

    private fun setButtonAddBitmap(bitmap: Bitmap, requestLayout: Boolean) {
        btnAdd = bitmap
        if (requestLayout) {
            requestLayout()
        }
    }

    private fun setButtonAdd(@DrawableRes resId: Int) {
        val d = ContextCompat.getDrawable(context, resId)
        d?.let {
            btnAdd = Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
            it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
            it.draw(Canvas(btnAdd))
            setButtonAddBitmap(btnAdd)
        }
    }
    //endregion buttons

    //region animation
    private fun setAnimationInterpolator(interpolator: TimeInterpolator) {
        defaultInterpolator = interpolator
    }

    fun toggle(duration: Long = 500L) {
        toggle(duration, defaultInterpolator)
    }

    fun toggle(duration: Long, interpolator: TimeInterpolator) {
        if (translateAnimator == null || !translateAnimator!!.isRunning) {
            translateAnimator = getAnimator(
                    btnRemovePosition,
                    if (isOpen) width.toFloat() - btnAdd.width else 0f,
                    duration,
                    interpolator,
                    AnimatorUpdateListener { valueAnimator ->
                        val value = valueAnimator.animatedValue as Float
                        translateRemoveButton(value)
                        updateButtonsRect()
                    }).also {
                it.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationEnd(animation: Animator) {
                        isAnimating = false
                        isOpen = btnRemovePosition == 0f
                        if (isOpen) {
                            alphaAnimator?.start()
                        }
                    }

                    override fun onAnimationRepeat(animation: Animator?) {
                    }

                    override fun onAnimationCancel(animation: Animator?) {
                    }

                    override fun onAnimationStart(animation: Animator?) {
                        isAnimating = true
                        if (isOpen && labelAlpha > 0) {
                            alphaAnimator?.start()
                        }
                    }
                })
            }

            alphaAnimator = getAnimator(
                    labelAlpha.toFloat(),
                    if (isOpen) 0f else defaultMaxAlpha.toFloat(),
                    if (duration > 0) duration / 3 else 0,
                    LinearInterpolator(),
                    AnimatorUpdateListener { valueAnimator ->
                        setLabelAlpha((valueAnimator.animatedValue as Float).toInt())
                    }).also {
                it.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationEnd(animation: Animator) {
                        isAnimating = false
                        if (isClosing) {
                            translateAnimator?.start()
                        }
                    }

                    override fun onAnimationRepeat(animation: Animator?) {
                    }

                    override fun onAnimationCancel(animation: Animator?) {
                    }

                    override fun onAnimationStart(animation: Animator?) {
                        isAnimating = true
                    }
                })
            }

            if (isOpen) {
                isClosing = true
                alphaAnimator?.start()
            } else {
                isClosing = false
                translateAnimator?.start()
            }
        }
    }

    private fun getAnimator(
            current: Float, next: Float, duration: Long, timeInterpolator: TimeInterpolator, listener: AnimatorUpdateListener
    ): ValueAnimator {
        return ValueAnimator().apply {
            this.interpolator = timeInterpolator
            this.duration = duration
            this.setObjectValues(current, next)
            this.setEvaluator(object : FloatEvaluator() {
                fun evaluate(fraction: Float, startValue: Float, endValue: Float): Int {
                    return (startValue + (endValue - startValue) * fraction).roundToInt()
                }
            })
            this.addUpdateListener(listener)
        }
    }

    private fun translateRemoveButton(value: Float) {
        btnRemovePosition = value
        invalidate()
    }

    private fun setLabelAlpha(value: Int) {
        labelAlpha = value
        invalidate()
    }

    private fun updateButtonsRect() {
        removeButtonRect = RectF(btnRemovePosition, 0f, btnRemove.width.toFloat(), btnRemove.height.toFloat())
        addButtonRect = RectF((measuredWidth - btnAdd.width).toFloat(), 0f, measuredWidth.toFloat(), btnAdd.height.toFloat())
    }
    //endregion animation

    @Synchronized
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = 200
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            width = MeasureSpec.getSize(widthMeasureSpec)
        }
        var height: Int = max(btnRemove.height, btnAdd.height)
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec))
        }
        setMeasuredDimension(width, height)

        if (initializing) {
            initializing = false
            btnRemovePosition = if (isOpen) 0f else (width - btnRemove.width).toFloat()
            updateButtonsRect()
        }
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundLineRect.set(btnRemovePosition + btnRemove.width / 2, 0f, width.toFloat() - btnAdd.width / 2, height.toFloat())
        pickerPaint.color = pickerBackgroundColor
        canvas.drawRect(backgroundLineRect, pickerPaint)

        if (showLabel) {
            drawCenteredText(canvas, textLabelPaint.apply { alpha = labelAlpha }, String.format(textLabelFormatter, value))
        }
        canvas.drawBitmap(
                btnRemove,
                btnRemovePosition,
                0f,
                btnRemovePaint.apply {
                    alpha = if (btnRemovePosition >= width.toFloat() - btnAdd.width) 0 else defaultMaxAlpha
                    colorFilter = if (pressedButton != null && pressedButton!! == Button.REMOVE) darkenColorFilter else null
                })

        canvas.drawBitmap(
                btnAdd,
                width.toFloat() - btnAdd.width,
                height.toFloat() - btnAdd.height,
                btnAddPaint.apply {
                    colorFilter = if (pressedButton != null && pressedButton!! == Button.ADD) darkenColorFilter else null
                })
    }

    private fun drawCenteredText(canvas: Canvas, paint: Paint, text: String) {
        val r = Rect()
        canvas.getClipBounds(r)
        val cHeight: Int = r.height()
        val cWidth: Int = r.width()
        paint.textAlign = Align.LEFT
        paint.getTextBounds(text, 0, text.length, r)
        val x: Float = cWidth / 2f - r.width() / 2f - r.left
        val y: Float = cHeight / 2f + r.height() / 2f - r.bottom
        canvas.drawText(text, x, y, paint)
    }

    //region touch logic
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || isAnimating) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                buttonTouched(startX, startY)?.let {
                    pressedButton = it
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val deltaX = abs(startX - event.x)
                val deltaY = abs(startY - event.y)
                if (!(deltaX > clickActionThreshold || deltaY > clickActionThreshold)) {
                    if (pressedButton != null && pressedButton!! == Button.ADD) {
                        pressedButton = null
                        if (!isOpen) {
                            toggle()
                        }
                        if (value < max) {
                            value++
                            return updateAndReturn()
                        }
                    }
                    if (pressedButton != null && pressedButton!! == Button.REMOVE) {
                        pressedButton = null
                        if (value > min) {
                            value--
                            if (value == 0 && autoToggle && isOpen) {
                                toggle()
                            }
                            return updateAndReturn()
                        } else {
                            if (autoToggle && isOpen) {
                                toggle()
                            }
                        }
                    }
                }
                pressedButton = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_OUTSIDE -> {
                pressedButton = null
                invalidate()
                return true
            }
            else -> return true
        }
    }

    private fun buttonTouched(x: Float, y: Float): Button? {
        if (addButtonRect.contains(x, y)) {
            return Button.ADD
        }
        if (removeButtonRect.contains(x, y)) {
            return Button.REMOVE
        }
        return null
    }

    private fun updateAndReturn(): Boolean {
        invalidate()
        valueListener?.onChanged(this, value)
        return true
    }

    private fun dpToPx(dp: Int): Int {
        return ceil(dp * Resources.getSystem().displayMetrics.density).toInt()
    }
//endregion

    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        bundle.putParcelable("SUPER", super.onSaveInstanceState())
        bundle.putInt("VALUE", value)
        bundle.putInt("MIN", min)
        bundle.putInt("MAX", max)
        bundle.putBoolean("LABEL_SHOW", showLabel)
        bundle.putInt("LABEL_SIZE", textLabelSize)
        bundle.putInt("LABEL_ALPHA", labelAlpha)
        bundle.putString("LABEL_FORMATTER", textLabelFormatter)
        bundle.putBoolean("IS_OPEN", isOpen)
        bundle.putBoolean("AUTO_TOGGLE", autoToggle)
        return bundle
    }

    override fun onRestoreInstanceState(parcel: Parcelable) {
        val bundle = parcel as Bundle
        super.onRestoreInstanceState(bundle.getParcelable("SUPER"))
        value = bundle.getInt("VALUE")
        min = bundle.getInt("MIN")
        max = bundle.getInt("MAX")
        showLabel = bundle.getBoolean("LABEL_SHOW", true)
        textLabelSize = bundle.getInt("LABEL_SIZE")
        labelAlpha = bundle.getInt("LABEL_ALPHA")
        textLabelFormatter = bundle.getString("LABEL_FORMATTER", "%s")
        isOpen = bundle.getBoolean("IS_OPEN", false)
        autoToggle = bundle.getBoolean("AUTO_TOGGLE", true)
    }
}