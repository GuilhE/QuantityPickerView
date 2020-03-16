package com.github.guilhe.views

import android.animation.Animator
import android.animation.FloatEvaluator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.*
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.graphics.Paint.Align
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlin.math.*

@Suppress("unused", "MemberVisibilityCanBePrivate", "SameParameterValue")
class QuantityPickerView : View {

    enum class Button {
        ADD, REMOVE
    }

    private val defaultMaxWidth = 200
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
    private var btnRemoveXPosition: Float = 0f
    private var btnAddXPosition: Float = 0f
    private var btnRippleDrawable: RippleDrawable? = null
    private var maxWidth = defaultMaxWidth

    private lateinit var textLabelPaint: Paint
    private lateinit var pickerPaint: Paint
    private lateinit var btnAddPaint: Paint
    private lateinit var btnRemovePaint: Paint
    private lateinit var backgroundLineRect: Rect
    private lateinit var removeButtonRect: Rect
    private lateinit var addButtonRect: Rect
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
        @ColorInt val rippleColor: Int?
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
            isAutoToggleEnabled = a.getBoolean(R.styleable.QuantityPickerView_autoToggle, true)
            toggleFromStart = a.getInt(R.styleable.QuantityPickerView_toggleFrom, 0) == 0
            isOpen = a.getBoolean(R.styleable.QuantityPickerView_isOpen, false)
            rippleColor = a.getColor(R.styleable.QuantityPickerView_rippleColor, Color.GRAY)
            isRippleEnabled = a.getBoolean(R.styleable.QuantityPickerView_rippleEnable, false)
        } finally {
            a.recycle()
        }

        labelAlpha = if (isOpen) defaultMaxAlpha else 0
        isFocusable = true
        isFocusableInTouchMode = true

        backgroundLineRect = Rect()
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

        if (isRippleEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            rippleColor?.let { it ->
                btnRippleDrawable = RippleDrawable(ColorStateList.valueOf(it), null, null).also { drawable ->
                    drawable.callback = this
                }
                isRippleEnabled = true
            }
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

    /**
     * If you want to define something like "x unit." call this method with formatter = "%s unit."
     */
    var textLabelFormatter: String = "%s"
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Values in DP
     */
    var textLabelSize: Int = dpToPx(20)
        set(value) {
            field = value
            invalidate()
        }

    /**
     * When minimum value is reached the view closes automatically
     */
    var isAutoToggleEnabled: Boolean = true

    /**
     * Animate from Start to End, otherwise if false
     */
    var toggleFromStart: Boolean = true

    var isOpen: Boolean = false
        private set

    var isRippleEnabled: Boolean = false
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
            val buttonToTranslate = if (toggleFromStart) Button.ADD else Button.REMOVE
            translateAnimator = getAnimator(
                if (buttonToTranslate == Button.ADD) btnAddXPosition else btnRemoveXPosition,
                if (toggleFromStart) {
                    if (isOpen) 0f else maxWidth.toFloat() - btnAdd.width
                } else {
                    if (isOpen) maxWidth.toFloat() - btnAdd.width else 0f
                },
                duration,
                interpolator,
                AnimatorUpdateListener { valueAnimator ->
                    val value = valueAnimator.animatedValue as Float
                    translateButton(value, buttonToTranslate)
                    updateButtonsRect()
                }).also {
                it.addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator?) {
                        isAnimating = true
                        if (isOpen && labelAlpha > 0) {
                            alphaAnimator?.start()
                        }
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        isAnimating = false
                        isOpen = if (toggleFromStart) btnAddXPosition == maxWidth.toFloat() - btnAdd.width else btnRemoveXPosition == 0f
                        if (isOpen) {
                            alphaAnimator?.start()
                        }
                    }

                    override fun onAnimationRepeat(animation: Animator?) {
                    }

                    override fun onAnimationCancel(animation: Animator?) {
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
                    override fun onAnimationStart(animation: Animator?) {
                        isAnimating = true
                    }

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

    private fun translateButton(value: Float, button: Button) {
        if (button == Button.ADD) {
            btnAddXPosition = value
        } else {
            btnRemoveXPosition = value
        }
//        invalidate()
        requestLayout()
    }

    private fun setLabelAlpha(value: Int) {
        labelAlpha = value
        invalidate()
    }

    private fun updateButtonsRect() {
        removeButtonRect = Rect(btnRemoveXPosition.toInt(), measuredHeight / 2 - btnRemove.height / 2, btnRemove.width, btnRemove.height)
        addButtonRect = Rect((btnAddXPosition.toInt()), measuredHeight / 2 - btnAdd.height / 2, measuredWidth, btnAdd.height)
    }
    //endregion animation

    //region ripple
    override fun drawableHotspotChanged(x: Float, y: Float) {
        super.drawableHotspotChanged(x, y)
        btnRippleDrawable?.setHotspot(x, y)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        btnRippleDrawable?.state = drawableState
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return who == btnRippleDrawable || super.verifyDrawable(who)
    }
    //endregion

    @Synchronized
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = defaultMaxWidth
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            width = MeasureSpec.getSize(widthMeasureSpec)
        }
        var height: Int = max(btnRemove.height, btnAdd.height)
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            height = min(height, MeasureSpec.getSize(heightMeasureSpec))
        }
        setMeasuredDimension(width, height)
        maxWidth = width

        if (initializing) {
            initializing = false
            btnAddXPosition =
                if (toggleFromStart) {
                    if (isOpen) {
                        (maxWidth - btnAdd.width).toFloat()
                    } else 0f
                } else {
                    (maxWidth - btnAdd.width).toFloat()
                }
            btnRemoveXPosition = if (!toggleFromStart && !isOpen) btnAddXPosition else 0f
            updateButtonsRect()
            @RequiresApi(Build.VERSION_CODES.M)
            if (isRippleEnabled) {
                // assuming both buttons are equal in size, which is supposed...
                btnRippleDrawable?.radius = addButtonRect.height() / 2
            }
        }
//        setMeasuredDimension(
//            if (btnAddXPosition == btnRemoveXPosition)
//                max(addButtonRect.width(), removeButtonRect.width())
//            else
//                abs(removeButtonRect.width() / 2 + btnAddXPosition - btnRemoveXPosition + addButtonRect.width() / 2).toInt(),
//            height
//        ).also { updateButtonsRect() }
        maxWidth = width
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundLineRect.set((btnRemoveXPosition + btnRemove.width / 2).toInt(), 0, (btnAddXPosition + btnRemove.width / 2).toInt(), height)
        pickerPaint.color = pickerBackgroundColor
        canvas.drawRect(backgroundLineRect, pickerPaint.apply {
            alpha = if (btnRemoveXPosition == btnAddXPosition) 0 else defaultMaxAlpha
        })

        if (showLabel) {
            drawCenteredText(canvas, textLabelPaint.apply { alpha = labelAlpha }, String.format(textLabelFormatter, value))
        }

        val btnRemovePressed = pressedButton != null && pressedButton!! == Button.REMOVE
        val btnAddPressed = pressedButton != null && pressedButton!! == Button.ADD

        canvas.drawBitmap(
            btnRemove,
            btnRemoveXPosition,
            (height / 2 - btnRemove.height / 2).toFloat(),
            btnRemovePaint.apply {
                alpha = pickerPaint.alpha
                colorFilter = if (!isRippleEnabled && btnRemovePressed) darkenColorFilter else null
            })

        canvas.drawBitmap(
            btnAdd,
            btnAddXPosition,
            (height / 2 - btnAdd.height / 2).toFloat(),
            btnAddPaint.apply {
                colorFilter = if (!isRippleEnabled && btnAddPressed) darkenColorFilter else null
            })

        @RequiresApi(Build.VERSION_CODES.M)
        if (isRippleEnabled && (btnRemovePressed || btnAddPressed)) {
            btnRippleDrawable?.let {
                it.bounds = if (btnAddPressed) addButtonRect else removeButtonRect
                it.radius = (if (btnAddPressed) addButtonRect.height() else removeButtonRect.height()) / 2
                it.setHotspot(startX, startY)
                it.draw(canvas)
            }
        }
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
                isPressed = true
                startX = event.x
                startY = event.y
                buttonTouched(startX, startY)?.let {
                    pressedButton = it
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
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
                            if (value == 0 && isAutoToggleEnabled && isOpen) {
                                toggle()
                            }
                            return updateAndReturn()
                        } else {
                            if (isAutoToggleEnabled && isOpen) {
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
        if (addButtonRect.contains(x.toInt(), y.toInt())) {
            return Button.ADD
        }
        if (removeButtonRect.contains(x.toInt(), y.toInt())) {
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
        bundle.putBoolean("AUTO_TOGGLE", isAutoToggleEnabled)
        bundle.putBoolean("TOGGLE_FROM_START", toggleFromStart)
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
        isAutoToggleEnabled = bundle.getBoolean("AUTO_TOGGLE", true)
        toggleFromStart = bundle.getBoolean("TOGGLE_FROM_START", true)
    }
}