package com.autotest.safety

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 守卫冒烟专用 Activity（androidTest-only，不进 AAR）：
 * 程序化构造三个按钮——危险（命中词表）、普通（不命中）、确认语义（验证 DialogDismiss 收敛），
 * 点击结果写入 [statusView]，测试据此断言真实点击是否发生。
 */
class GuardTestActivity : Activity() {

    lateinit var statusView: TextView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusView = TextView(this).apply { text = "初始" }
        root.addView(statusView)

        root.addView(Button(this).apply {
            text = "确认转账"  // 命中内置危险词「转账」
            setOnClickListener { statusView.text = "危险按钮被点击" }
        })
        root.addView(Button(this).apply {
            text = "普通按钮"
            setOnClickListener { statusView.text = "普通按钮被点击" }
        })
        root.addView(Button(this).apply {
            text = "确定"  // 确认语义：DialogDismiss 收敛后不得自动点
            setOnClickListener { statusView.text = "确定被点击" }
        })

        setContentView(root)
    }
}
