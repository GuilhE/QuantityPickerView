package com.github.guilhe

import android.content.res.Resources
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.github.guilhe.databinding.ActivityMainBinding
import com.github.guilhe.views.QuantityPickerView
import com.github.guilhe.views.QuantityPickerView.QuantityPickerViewChangeListener
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        val listener = object : QuantityPickerViewChangeListener {
            override fun onChanged(view: QuantityPickerView, value: Int) {
                (view.parent as FrameLayout).elevation = if (value == view.min) dpToPx(0) else dpToPx(5)
            }
        }

        binding.picker1QuantityPickerView.valueListener = listener
        binding.picker2QuantityPickerView.valueListener = listener
        binding.picker3QuantityPickerView.valueListener = listener
        binding.picker4QuantityPickerView.valueListener = listener
        binding.picker5QuantityPickerView.valueListener = listener

    }

    private fun dpToPx(dp: Int): Float {
        return ceil(dp * Resources.getSystem().displayMetrics.density)
    }
}