package com.github.guilhe

import android.content.res.Resources
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.github.guilhe.databinding.ActivityMainBinding
import com.github.guilhe.views.QuantityPickerView
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        val listener = object : QuantityPickerView.QuantityPickerViewActionListener {
            override fun onValueChanged(view: QuantityPickerView, value: Int) {
                (view.parent as FrameLayout).elevation = if (value == view.min) dpToPx(0) else dpToPx(5)
            }

            override fun onToggleFinish(isOpen: Boolean) {}
            override fun beforeStartToggle(willOpen: Boolean) {}
        }

        binding.picker1QuantityPickerView.actionListener = listener
        binding.picker2QuantityPickerView.actionListener = listener
        binding.picker3QuantityPickerView.actionListener = listener
        binding.picker4QuantityPickerView.actionListener = listener
        binding.picker5QuantityPickerView.actionListener = listener
    }

    private fun dpToPx(dp: Int) = ceil(dp * Resources.getSystem().displayMetrics.density)
}