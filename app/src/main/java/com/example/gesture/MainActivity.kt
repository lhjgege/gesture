package com.example.gesture

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // 使用EmotionKeyboard.with()方法获取EmotionKeyboard的实例，并进行绑定操作
        EmotionKeyboard.with(this)
            .bindToEditText(findViewById(R.id.chat_box)) // 绑定编辑框
            .setButtonView(findViewById(R.id.bt_emoji), findViewById(R.id.bt_menu))//设置按钮
            .bindToEmojiButton() // 绑定表情按钮
            .bindKeyboardViewToTop(findViewById(R.id.bottom_bar))
            .setEmotionView(findViewById(R.id.emoji_paper)) // 设置表情布局
            .bindToMenuButton() // 绑定菜单按钮
            .setMenuView(findViewById(R.id.menu_paper)) // 设置菜单布局
            .bindToContent(findViewById(R.id.chat_frame)) // 绑定内容布局
            .build() // 构建EmotionKeyboard实例
    }
}