package com.example.aiassistant.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aiassistant.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        // 显示返回箭头
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    // 处理返回箭头的点击事件
    override fun onSupportNavigateUp(): Boolean {
        // 当点击标题栏的返回箭头时，执行与按物理返回键相同的操作
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
