package com.webpanel.app

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.webpanel.app.util.AppConfig

class SettingsActivity : AppCompatActivity() {

    private lateinit var config: AppConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        config = AppConfig(this)

        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 48)
        }

        val toolbar = MaterialToolbar(this).apply {
            title = "设置"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }
        container.addView(toolbar)

        container.addView(createSectionLabel("网页设置"))
        container.addView(createSettingRow("网页地址", config.url) {
            config.url = it
        })
        container.addView(createNumberSettingRow("刷新间隔（秒）", config.refreshInterval) {
            config.refreshInterval = it
        })

        container.addView(createSectionLabel("内容检测"))
        container.addView(createNumberSettingRow("检测间隔（秒）", config.contentCheckInterval) {
            config.contentCheckInterval = it
        })
        container.addView(createNumberSettingRow("无新内容隐藏（分钟）", config.hideDelayMinutes) {
            config.hideDelayMinutes = it
        })

        container.addView(createSectionLabel("其他"))
        container.addView(createSwitchRow("开机自启", config.autoStart) {
            config.autoStart = it
        })

        scrollView.addView(container)
        setContentView(scrollView)
    }

    private fun createSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 32, 0, 8)
            setTextColor(getColor(com.google.android.material.R.color.design_default_color_primary))
        }
    }

    private fun createSettingRow(label: String, currentValue: String, onSave: (String) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)

            addView(TextView(this@SettingsActivity).apply {
                text = label
                textSize = 14f
            })

            addView(TextView(this@SettingsActivity).apply {
                text = currentValue
                textSize = 13f
                setTextColor(0xFF888888.toInt())
                setPadding(0, 4, 0, 0)
                setOnClickListener {
                    showEditDialog(label, currentValue) { newValue ->
                        text = newValue
                        onSave(newValue)
                    }
                }
            })
        }
    }

    private fun showEditDialog(title: String, currentValue: String, onResult: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(currentValue)
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    onResult(value)
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createNumberSettingRow(label: String, currentValue: Int, onSave: (Int) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)

            addView(TextView(this@SettingsActivity).apply {
                text = label
                textSize = 14f
            })

            addView(TextView(this@SettingsActivity).apply {
                text = currentValue.toString()
                textSize = 13f
                setTextColor(0xFF888888.toInt())
                setPadding(0, 4, 0, 0)
                setOnClickListener {
                    showNumberDialog(label, currentValue) { newValue ->
                        text = newValue.toString()
                        onSave(newValue)
                    }
                }
            })
        }
    }

    private fun showNumberDialog(title: String, currentValue: Int, onResult: (Int) -> Unit) {
        val input = EditText(this).apply {
            setText(currentValue.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value != null && value >= 0) {
                    onResult(value)
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createSwitchRow(label: String, currentValue: Boolean, onSave: (Boolean) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL

            addView(TextView(this@SettingsActivity).apply {
                text = label
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(Switch(this@SettingsActivity).apply {
                isChecked = currentValue
                setOnCheckedChangeListener { _, isChecked -> onSave(isChecked) }
            })
        }
    }
}
