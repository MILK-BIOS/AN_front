package com.example.an_front

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import org.json.JSONArray
import org.json.JSONObject

class ResponseActivity : AppCompatActivity() {

    private lateinit var responseContainerLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var btnBack: Button
    
    companion object {
        const val EXTRA_RESPONSE_TEXT = "extra_response_text"
        const val EXTRA_ADDRESS = "extra_address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_response)
        
        // 初始化视图
        responseContainerLayout = findViewById(R.id.responseContainerLayout)
        scrollView = findViewById(R.id.scrollView)
        btnBack = findViewById(R.id.btnBack)
        
        // 获取传递的数据
        val responseText = intent.getStringExtra(EXTRA_RESPONSE_TEXT) ?: "无响应数据"
        val addressText = intent.getStringExtra(EXTRA_ADDRESS) ?: "未知位置"
        
        // 添加位置信息卡片
        addAddressCard(addressText)
        
        // 解析并显示服务器响应
        try {
            val jsonResponse = JSONObject(responseText)
            if (jsonResponse.has("results")) {
                val results = jsonResponse.getJSONArray("results")
                processResults(results)
            } else {
                // 不是预期格式的JSON，显示原始响应
                addTextCard("服务器响应", responseText)
            }
        } catch (e: Exception) {
            // JSON解析失败，显示原始文本
            addTextCard("服务器响应", responseText)
        }
        
        // 设置返回按钮点击事件
        btnBack.setOnClickListener {
            finish() // 结束当前Activity，返回上一个Activity
        }
    }
    
    private fun addAddressCard(addressText: String) {
        val addressCard = createCard()
        
        val titleView = TextView(this)
        titleView.text = "当前位置"
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.textSize = 18f
        titleView.setTextColor(Color.parseColor("#1F2937"))
        titleView.setPadding(0, 0, 0, dpToPx(8))
        
        val addressView = TextView(this)
        addressView.text = addressText
        addressView.textSize = 16f
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        layout.addView(titleView)
        layout.addView(addressView)
        
        addressCard.addView(layout)
        responseContainerLayout.addView(addressCard)
    }
    
    private fun addTextCard(title: String, content: String) {
        val card = createCard()
        
        val titleView = TextView(this)
        titleView.text = title
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.textSize = 18f
        titleView.setTextColor(Color.parseColor("#1F2937"))
        titleView.setPadding(0, 0, 0, dpToPx(8))
        
        val contentView = TextView(this)
        contentView.text = content
        contentView.textSize = 16f
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        layout.addView(titleView)
        layout.addView(contentView)
        
        card.addView(layout)
        responseContainerLayout.addView(card)
    }
    
    private fun processResults(results: JSONArray) {
        for (i in 0 until results.length()) {
            val agentObj = results.getJSONObject(i)
            val agentName = agentObj.keys().next()
            val agentData = agentObj.getJSONObject(agentName)
            
            if (agentName == "navigator" && agentData.has("messages")) {
                processNavigatorData(agentData.getJSONArray("messages"))
            } else {
                addAgentCard(agentName, agentData)
            }
        }
    }
    
    private fun processNavigatorData(messages: JSONArray) {
        val card = createCard()
        
        val titleView = TextView(this)
        titleView.text = "导航信息"
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.textSize = 20f
        titleView.setTextColor(Color.parseColor("#1F2937"))
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        layout.addView(titleView)
        
        for (i in 0 until messages.length()) {
            val message = messages.getJSONObject(i)
            
            if (message.has("content") && message.get("content") is JSONArray) {
                val content = message.getJSONArray("content")
                
                for (j in 0 until content.length()) {
                    try {
                        val item = content.getJSONObject(j)
                        
                        if (item.has("steps")) {
                            val steps = item.getJSONArray("steps")
                            val stepsLayout = createStepsLayout(steps)
                            layout.addView(stepsLayout)
                        }
                    } catch (e: Exception) {
                        // 跳过不是对象的内容
                        continue
                    }
                }
            } else if (message.has("content")) {
                // 处理其他类型的内容
                val contentText = message.get("content").toString()
                val contentView = TextView(this)
                contentView.text = contentText
                contentView.textSize = 16f
                contentView.setPadding(0, dpToPx(8), 0, dpToPx(8))
                layout.addView(contentView)
            }
        }
        
        card.addView(layout)
        responseContainerLayout.addView(card)
    }
    
    private fun createStepsLayout(steps: JSONArray): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        container.setBackgroundColor(Color.parseColor("#F9FAFB"))
        
        val titleView = TextView(this)
        titleView.text = "导航步骤"
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.textSize = 18f
        titleView.setTextColor(Color.parseColor("#4B5563"))
        titleView.setPadding(0, 0, 0, dpToPx(8))
        container.addView(titleView)
        
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val stepLayout = LinearLayout(this)
            stepLayout.orientation = LinearLayout.VERTICAL
            stepLayout.setPadding(dpToPx(16), dpToPx(8), dpToPx(8), dpToPx(8))
            stepLayout.setBackgroundResource(R.drawable.step_border)
            
            // 步骤指令
            val instruction = step.optString("instruction", "无指令")
            val instructionText = SpannableStringBuilder("步骤 ${i + 1}: $instruction")
            instructionText.setSpan(StyleSpan(Typeface.BOLD), 0, 4, 0)
            instructionText.setSpan(ForegroundColorSpan(Color.parseColor("#1F2937")), 0, 4, 0)
            
            val instructionView = TextView(this)
            instructionView.text = instructionText
            instructionView.textSize = 16f
            stepLayout.addView(instructionView)
            
            // 距离信息
            if (step.has("distance")) {
                val distance = step.getDouble("distance")
                val distanceView = TextView(this)
                distanceView.text = "距离: ${distance} 米"
                distanceView.textSize = 14f
                distanceView.setTextColor(Color.parseColor("#6B7280"))
                stepLayout.addView(distanceView)
            }
            
            // 时间信息
            if (step.has("duration")) {
                val duration = step.getDouble("duration")
                val durationView = TextView(this)
                durationView.text = "预计时间: ${duration} 秒"
                durationView.textSize = 14f
                durationView.setTextColor(Color.parseColor("#6B7280"))
                stepLayout.addView(durationView)
            }
            
            container.addView(stepLayout)
            
            // 添加间隔
            if (i < steps.length() - 1) {
                val spacer = View(this)
                spacer.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    dpToPx(8)
                )
                container.addView(spacer)
            }
        }
        
        return container
    }
    
    private fun addAgentCard(agentName: String, agentData: JSONObject) {
        val card = createCard()
        
        val titleView = TextView(this)
        titleView.text = "Agent: $agentName"
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.textSize = 18f
        titleView.setTextColor(Color.parseColor("#1F2937"))
        
        val contentView = TextView(this)
        contentView.text = agentData.toString(2)
        contentView.textSize = 14f
        contentView.setPadding(0, dpToPx(8), 0, 0)
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        layout.addView(titleView)
        layout.addView(contentView)
        
        card.addView(layout)
        responseContainerLayout.addView(card)
    }
    
    private fun createCard(): CardView {
        val card = CardView(this)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        card.layoutParams = params
        card.radius = dpToPx(8).toFloat()
        card.setCardBackgroundColor(Color.WHITE)
        card.cardElevation = dpToPx(4).toFloat()
        return card
    }
    
    private fun dpToPx(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }
}