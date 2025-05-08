package com.example.an_front

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import org.json.JSONArray
import org.json.JSONObject

import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.InitListener
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechSynthesizer
import com.iflytek.cloud.SynthesizerListener

class ResponseActivity : AppCompatActivity() {

    // 默认发音人
    private var voicer = "x4_lingxiaoxuan_en"

    // 范围1-100
    private var speedValue = "50" // 语速
    private var pitchValue = "50" // 音调
    private var volumeValue = "50" // 音量

    // 引擎类型
    private var mEngineType = SpeechConstant.TYPE_CLOUD

    private var mTts: SpeechSynthesizer? = null

    private lateinit var locationClient: AMapLocationClient
    private lateinit var responseContainerLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var btnBack: Button
    private var steps: JSONArray? = null
    private var stepsContainer: LinearLayout? = null
    private var currentStepIndex = -2
    private var currentLatitude = 0.0
    private var currentLongitude = 0.0
    private var isFirstHighlight = true
    
    companion object {
        const val EXTRA_RESPONSE_TEXT = "extra_response_text"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        private const val TAG = "ResponseActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)
        setContentView(R.layout.activity_response)

        // 初始化合成对象
        mTts = SpeechSynthesizer.createSynthesizer(this, mTtsInitListener)
        // 设置参数
        setParam()

        // 初始化视图
        responseContainerLayout = findViewById(R.id.responseContainerLayout)
        scrollView = findViewById(R.id.scrollView)
        btnBack = findViewById(R.id.btnBack)
        
        // 获取传递的数据
        val responseText = intent.getStringExtra(EXTRA_RESPONSE_TEXT) ?: "无响应数据"
//        val addressText = intent.getStringExtra(EXTRA_ADDRESS) ?: "未知位置"
        
        // 获取位置信息
        if (savedInstanceState != null) {
            currentStepIndex = savedInstanceState.getInt("current_step_index", -1)
            currentLatitude = savedInstanceState.getDouble("current_latitude", 0.0)
            currentLongitude = savedInstanceState.getDouble("current_longitude", 0.0)
        } else {
            currentLatitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
            currentLongitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
        }
        
        // 添加位置信息卡片
//        addAddressCard(addressText)

        
        // 解析并显示服务器响应
        try {
            val jsonResponse = JSONObject(responseText)
            if (jsonResponse.has("results")) {
                val results = jsonResponse.getJSONArray("results")
                processResults(results)
            } else {
                addTextCard("服务器响应", responseText)
            }
        } catch (e: Exception) {
            addTextCard("服务器响应", responseText)
        }
        
        // 设置返回按钮点击事件
        btnBack.setOnClickListener {
            saveResponseResult()
            finish()
        }
        
        // 初始化位置服务 - 移到最后，确保UI已准备好
        initLocationService()
    }

    /**
     * 播放输入文本的语音
     * @param text 要播放的文本
     */
    private fun playText(text: String) {
        if (mTts == null) {
            showTip("创建对象失败，请确认 libmsc.so 放置正确，且有调用 createUtility 进行初始化")
            return
        }

        // 去除输入文本的首尾空格
        val etStr = text.trim()

        // 开始语音合成
        val code = if (etStr.isNotEmpty()) {
            mTts?.startSpeaking(etStr, mTtsListener)
        } else {
            mTts?.startSpeaking("输出文本为空", mTtsListener)
        }

        // 检查合成结果
        if (code != ErrorCode.SUCCESS) {
            showTip("语音合成失败,错误码: $code")
        }
    }

    /**
     * 初始化监听
     */
    private val mTtsInitListener = InitListener { code ->
        Log.i(TAG, "InitListener init() code = $code")
        if (code != ErrorCode.SUCCESS) {
            showTip("初始化失败,错误码：$code")
        } else {
            showTip("初始化成功")
        }
    }

    /**
     * Toast 提示
     */
    private fun showTip(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * 参数设置
     */
    private fun setParam() {
        // 清空参数
        mTts?.setParameter(SpeechConstant.PARAMS, null)
        // 根据合成引擎设置相应参数
        if (mEngineType == SpeechConstant.TYPE_CLOUD) {
            mTts?.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD)
            // 支持实时音频返回，仅在 synthesizeToUri 条件下支持
            mTts?.setParameter(SpeechConstant.TTS_DATA_NOTIFY, "1")
            // 设置在线合成发音人
            mTts?.setParameter(SpeechConstant.VOICE_NAME, voicer)

            // 设置合成语速
            mTts?.setParameter(SpeechConstant.SPEED, speedValue)
            // 设置合成音调
            mTts?.setParameter(SpeechConstant.PITCH, pitchValue)
            // 设置合成音量
            mTts?.setParameter(SpeechConstant.VOLUME, volumeValue)
        } else {
            mTts?.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL)
            mTts?.setParameter(SpeechConstant.VOICE_NAME, "")
        }
        // 设置播放合成音频打断音乐播放，默认为 true
        mTts?.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "false")
    }

    /**
     * 合成回调监听
     */
    private val mTtsListener = object : SynthesizerListener {
        // 开始播放
        override fun onSpeakBegin() {
            Log.i(TAG, "开始播放")
        }

        // 暂停播放
        override fun onSpeakPaused() {
            Log.i(TAG, "暂停播放")
        }

        // 继续播放
        override fun onSpeakResumed() {
            Log.i(TAG, "继续播放")
        }

        // 合成进度
        override fun onBufferProgress(percent: Int, beginPos: Int, endPos: Int, info: String?) {
            Log.i(TAG, "合成进度：$percent%")
        }

        // 播放进度
        override fun onSpeakProgress(percent: Int, beginPos: Int, endPos: Int) {
            Log.i(TAG, "播放进度：$percent%")
        }

        // 播放完成
        override fun onCompleted(error: SpeechError?) {
            if (error == null) {
                Log.i(TAG, "播放完成")
            } else {
                // 异常信息
                showTip(error.getPlainDescription(true))
            }
        }

        // 事件
        override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {}
    }

    private fun initLocationService() {
        try {
            // 设置高德隐私协议(必须在初始化前调用)

            locationClient = AMapLocationClient(applicationContext)
            
            // 设置定位回调监听
            locationClient.setLocationListener { aMapLocation ->
                if (aMapLocation != null && aMapLocation.errorCode == 0) {
                    // 更新位置并检查高亮
                    currentLatitude = aMapLocation.latitude
                    currentLongitude = aMapLocation.longitude
                    
                    // 强制重新计算高亮
                    if (steps != null) {
                        // 使用-2表示强制刷新
                        currentStepIndex = -2
                        updateStepHighlight()
                    }
                }
            }
            
            // 配置定位参数
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                interval = 5000  // 降低到3秒更新一次位置
                isOnceLocation = false  // 持续定位
            }
            
            locationClient.setLocationOption(option)
            locationClient.startLocation()  // 启动定位
            
            // 立即触发一次高亮检查
            Handler(Looper.getMainLooper()).postDelayed({
                if (steps != null) {
                    updateStepHighlight()
                }
            }, 1000) // 1秒后检查
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // 更新步骤高亮方法
    private fun updateStepHighlight() {
        if (steps == null || stepsContainer == null) {
            return
        }
        val currentScrollX = scrollView.scrollX
        val currentScrollY = scrollView.scrollY
        val newStepIndex = findCurrentStepIndex()

        // 强制刷新或步骤变化时更新UI
        if (currentStepIndex == -2 || newStepIndex != currentStepIndex) {
            currentStepIndex = newStepIndex

            // 更新UI中的高亮
            runOnUiThread {
                for (i in 1 until stepsContainer!!.childCount) {
                    val child = stepsContainer!!.getChildAt(i)
                    if (child is LinearLayout) {  // 步骤容器
                        if (i == currentStepIndex) {
                            child.setBackgroundResource(R.drawable.current_step_border)

                            // 添加或更新当前位置标记
                            var hasLocationTag = false
                            for (j in 0 until child.childCount) {
                                val subView = child.getChildAt(j)
                                if (subView is TextView && subView.tag == "location_tag") {
                                    hasLocationTag = true
                                    break
                                }
                            }

                            if (!hasLocationTag) {
                                val locationTag = TextView(this)
                                locationTag.text = "⚑ 当前位置"
                                locationTag.setTextColor(Color.parseColor("#2563EB"))
                                locationTag.setTypeface(null, Typeface.BOLD)
                                locationTag.textSize = 14f
                                locationTag.tag = "location_tag"
                                locationTag.setPadding(0, dpToPx(4), 0, dpToPx(4))
                                child.addView(locationTag, 0)
                            }

                            val targetIndex = child.childCount - 4
                            if (targetIndex >= 0) {
                                val targetView = child.getChildAt(targetIndex)
                                if (targetView is TextView) {
                                    // 播放倒数第四个 TextView 的文本内容
                                    playText(targetView.text.toString().split(":").last())
                                } else {
                                    showTip("倒数第四个视图不是 TextView")
                                }
                            } else {
                                showTip("子视图数量不足，无法获取倒数第四个视图")
                            }

                        } else {
                            child.setBackgroundResource(R.drawable.step_border)

                            // 移除当前位置标记
                            for (j in child.childCount - 1 downTo 0) {
                                val subView = child.getChildAt(j)
                                if (subView is TextView && subView.tag == "location_tag") {
                                    child.removeViewAt(j)
                                    break
                                }
                            }
                        }
                    }
                }

                // 如果当前步骤可见，滚动到该步骤
                if (currentStepIndex >= 0) {
//                    val stepView = stepsContainer!!.getChildAt(currentStepIndex)
                    scrollView.post {
                        if (isFirstHighlight && currentStepIndex >= 0) {
                            val stepView = stepsContainer!!.getChildAt(currentStepIndex)
                            scrollView.smoothScrollTo(0, stepView.top)
                            isFirstHighlight = false // 标记为非首次
                        } else {
                            // 保持当前滚动位置
                            scrollView.scrollTo(currentScrollX, currentScrollY)
                        }
                    }
                }
            }
        }
    }
    
    // 查找当前步骤索引
    private fun findCurrentStepIndex(): Int {
        if (steps == null) return -1
        
        // 检查当前位置是否有效
        if (Math.abs(currentLatitude) < 0.000001 && Math.abs(currentLongitude) < 0.000001) {
            Log.d("ResponseActivity", "当前位置无效: $currentLatitude, $currentLongitude")
            return -1
        }
        
        var closestStepIndex = -1
        var minDistance = Double.MAX_VALUE
        
        // 找到最接近的步骤
        for (i in 0 until steps!!.length()) {
            try {
                val step = steps!!.getJSONObject(i)
                if (step.has("polyline")) {
                    val polyline = step.getString("polyline")
                    val distance = getMinDistanceToPolyline(currentLatitude, currentLongitude, polyline)
                    
                    if (distance < minDistance) {
                        minDistance = distance
                        closestStepIndex = i
                    }
                    
                    // 如果在路径上，直接返回
                    if (distance <= 50.0) {  // 50米阈值
                        Log.d("ResponseActivity", "位于步骤 $i 上，距离: $distance 米")
                        return i + 1
                    }
                }
            } catch (e: Exception) {
                Log.e("ResponseActivity", "处理步骤 $i 时出错", e)
            }
        }
        
        // 如果所有步骤都超过阈值，返回最接近的步骤
        if (closestStepIndex >= 0 && minDistance <= 200.0) {  // 200米较大阈值
            Log.d("ResponseActivity", "最接近步骤 $closestStepIndex，距离: $minDistance 米")
            return closestStepIndex + 1
        }
        
        Log.d("ResponseActivity", "未找到匹配步骤，最接近距离: $minDistance")
        return -1
    }

    // 计算点到折线的最小距离
    private fun getMinDistanceToPolyline(latitude: Double, longitude: Double, polyline: String): Double {
        val points = parsePolyline(polyline)
        if (points.size < 2) return Double.MAX_VALUE
        
        var minDistance = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val distance = distanceToSegment(
                latitude, longitude,
                points[i].first, points[i].second,
                points[i+1].first, points[i+1].second
            )
            minDistance = Math.min(minDistance, distance)
        }
        
        return minDistance
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
            } else if (agentName!="printer"){
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
        this.steps = steps 
        
        val container = LinearLayout(this)
        stepsContainer = container  // 保存引用

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
        
        // 找到当前所在步骤
        
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            if (step.has("polyline")) {
                val polyline = step.getString("polyline")
                if (isLocationOnPath(currentLatitude, currentLongitude, polyline)) {
                    currentStepIndex = i
                    break
                }
            }
        }
        
        // 创建并添加所有步骤视图
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val stepLayout = LinearLayout(this)
            stepLayout.orientation = LinearLayout.VERTICAL
            stepLayout.setPadding(dpToPx(16), dpToPx(8), dpToPx(8), dpToPx(8))
            
            // 添加标签存储索引
            stepLayout.tag = i
            
            // 如果是当前步骤，使用高亮样式
            if (i == currentStepIndex) {
                stepLayout.setBackgroundResource(R.drawable.current_step_border)
            } else {
                stepLayout.setBackgroundResource(R.drawable.step_border)
            }
            
            // 步骤指令
            val stepNumber = i + 1
            val stepPrefix = "步骤 $stepNumber: "
            val instruction = step.optString("instruction", "无指令")
            val instructionText = SpannableStringBuilder("$stepPrefix$instruction")

            // 计算步骤前缀的实际长度
            val prefixLength = stepPrefix.length

            // 对整个步骤前缀应用样式，而不是固定字符数
            instructionText.setSpan(StyleSpan(Typeface.BOLD), 0, prefixLength, 0)
            instructionText.setSpan(ForegroundColorSpan(Color.parseColor("#1F2937")), 0, prefixLength, 0)

            val instructionView = TextView(this)
            instructionView.text = instructionText
            instructionView.textSize = 16f
            
            // 如果是当前步骤，添加"当前位置"标记
            if (i == currentStepIndex) {
                instructionView.setTextColor(Color.parseColor("#2563EB")) // 蓝色文本
                val currentLocationTag = TextView(this)
                currentLocationTag.text = "⚑ 当前位置"
                currentLocationTag.setTextColor(Color.parseColor("#2563EB"))
                currentLocationTag.setTypeface(null, Typeface.BOLD)
                currentLocationTag.textSize = 14f
                currentLocationTag.setPadding(0, dpToPx(4), 0, dpToPx(4))
                stepLayout.addView(currentLocationTag)
            }
            
            stepLayout.addView(instructionView)
            
            // 添加距离信息
            if (step.has("distance")) {
                val distance = step.getDouble("distance")
                val distanceView = TextView(this)
                distanceView.text = "距离: ${distance} 米"
                distanceView.textSize = 14f
                distanceView.setTextColor(Color.parseColor("#6B7280"))
                stepLayout.addView(distanceView)
            }
            
            // 添加时间信息
            if (step.has("duration")) {
                val duration = step.getDouble("duration")
                val durationView = TextView(this)
                durationView.text = "预计时间: ${duration} 秒"
                durationView.textSize = 14f
                durationView.setTextColor(Color.parseColor("#6B7280"))
                stepLayout.addView(durationView)
            }
            
            // 添加polyline信息（可选，用于调试）
            if (step.has("polyline")) {
                val polylineView = TextView(this)
                val polylineTruncated = step.getString("polyline").take(30) + "..."
                polylineView.text = "路径: $polylineTruncated"
                polylineView.textSize = 12f
                polylineView.setTextColor(Color.parseColor("#9CA3AF"))
                stepLayout.addView(polylineView)
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
        
        // 创建标题视图
        val titleView = TextView(this)
        titleView.text = "Agent: $agentName"
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.textSize = 18f
        titleView.setTextColor(Color.parseColor("#1F2937"))
        
        // 创建内容视图
        val contentView = TextView(this)
        
        // 尝试提取 messages[0].content
        try {
            if (agentData.has("messages") && agentData.getJSONArray("messages").length() > 0) {
                val firstMessage = agentData.getJSONArray("messages").getJSONObject(0)
                
                if (firstMessage.has("content")) {
                    // 获取内容，根据类型处理
                    val content = firstMessage.get("content")
                    
                    // 如果是字符串，直接显示
                    if (content is String) {
                        contentView.text = content.replace("\n", "") // 去掉换行符
                    } else if (content is JSONArray && content.length() > 0) {
                        // 如果是JSON数组，取出首个元素
                        val firstElement = content.get(0).toString()
                        contentView.text = firstElement.replace("\n", "") // 去掉换行符
                    } else {
                        // 如果是JSON对象或数组，格式化显示
                        contentView.text = content.toString().replace("\n", "") // 去掉换行符
                    }
                } else {
                    // 没有content键，显示整个消息
                    contentView.text = firstMessage.toString(2)
                }
            } else {
                // 没有messages键或消息为空，显示整个数据
                contentView.text = agentData.toString(2)
            }
        } catch (e: Exception) {
            // 发生错误时，显示原始数据
            contentView.text = "解析错误: ${e.message}\n\n原始数据:\n${agentData}"
        }
        
        contentView.setBackgroundResource(R.drawable.content_background) // 自定义背景
        contentView.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        contentView.textSize = 15f
        contentView.setTextColor(Color.parseColor("#374151")) // 深灰色文本
        
        // 创建并设置布局
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        layout.addView(titleView)
        layout.addView(contentView)
        
        // 添加到卡片
        card.addView(layout)
        responseContainerLayout.addView(card)

        if (agentName == "descriptor"){
            playText(contentView.text.toString())
        }
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

    override fun onBackPressed() {
        saveResponseResult()
        super.onBackPressed()
    }

    /**
    * 重写标题栏返回按钮点击事件
    */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            saveResponseResult()
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
    * 保存响应结果的方法
    */
    private fun saveResponseResult() {
        // 获取需要保存的数据
        val responseText = intent.getStringExtra(EXTRA_RESPONSE_TEXT) ?: "无响应数据"
        val addressText = intent.getStringExtra(EXTRA_ADDRESS) ?: "未知位置"
        
        // 创建返回结果Intent
        val resultIntent = Intent().apply {
            putExtra("saved_response", responseText)
            putExtra("saved_address", addressText)
        }
        
        // 设置结果为成功并传递数据
        setResult(RESULT_OK, resultIntent)
        
        // 你也可以在这里添加其他保存逻辑，例如写入SharedPreferences
        val sharedPrefs = getSharedPreferences("response_data", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString("last_response", responseText)
            putString("last_address", addressText)
            putLong("timestamp", System.currentTimeMillis())
            apply()
        }
        mTts?.pauseSpeaking()
    }

    private fun isLocationOnPath(latitude: Double, longitude: Double, polyline: String): Boolean {
        if (polyline.isEmpty()) return false
        
        try {
            val points = parsePolyline(polyline)
            if (points.size < 2) return false
            
            // 计算点到路径的最短距离
            var minDistance = Double.MAX_VALUE
            for (i in 0 until points.size - 1) {
                val segmentStart = points[i]
                val segmentEnd = points[i + 1]
                
                val distance = distanceToSegment(
                    latitude, longitude,
                    segmentStart.first, segmentStart.second,
                    segmentEnd.first, segmentEnd.second
                )
                
                if (distance < minDistance) {
                    minDistance = distance
                }
            }
            
            // 如果最短距离小于阈值，认为点在路径上
            val thresholdMeters = 50.0 // 50米阈值，可以根据需要调整
            return minDistance <= thresholdMeters
            
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
    * 解析polyline字符串为坐标点列表
    */
    private fun parsePolyline(polyline: String): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        
        val coordPairs = polyline.split(";")
        for (coordPair in coordPairs) {
            val coords = coordPair.split(",")
            if (coords.size == 2) {
                try {
                    val lng = coords[0].toDouble()
                    val lat = coords[1].toDouble()
                    points.add(Pair(lat, lng))
                } catch (e: NumberFormatException) {
                    // 忽略无效的坐标
                }
            }
        }
        
        return points
    }

    /**
    * 计算点到线段的距离（米）
    */
    private fun distanceToSegment(
        lat: Double, lng: Double,
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        // 使用Haversine公式计算地球表面距离
        
        // 点到线段端点的距离
        val distToP1 = haversineDistance(lat, lng, lat1, lng1)
        val distToP2 = haversineDistance(lat, lng, lat2, lng2)
        
        // 线段长度
        val segmentLength = haversineDistance(lat1, lng1, lat2, lng2)
        
        // 如果线段非常短，返回到任一端点的距离
        if (segmentLength < 1.0) {
            return Math.min(distToP1, distToP2)
        }
        
        // 使用向量投影计算点到线段的最短距离
        // 计算向量投影需要在平面坐标系中，我们可以使用简化的方法
        val bearing1 = bearing(lat1, lng1, lat2, lng2)
        val bearing2 = bearing(lat1, lng1, lat, lng)
        val bearingDiff = Math.abs(bearing1 - bearing2)
        
        // 如果点在线段的延长线上
        if (bearingDiff > 90.0) {
            return Math.min(distToP1, distToP2)
        }
        
        // 点到线段的垂直距离
        val sinAngle = Math.sin(Math.toRadians(bearingDiff))
        return distToP1 * sinAngle
    }

    /**
    * 计算两点间的Haversine距离（米）
    */
    private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // 地球半径，单位米
        
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLng = Math.toRadians(lng2 - lng1)
        
        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(lat1Rad) * Math.cos(lat2Rad) *
            Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }

    /**
    * 计算两点间的方位角
    */
    private fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLng = Math.toRadians(lng2 - lng1)
        
        val y = Math.sin(deltaLng) * Math.cos(lat2Rad)
        val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLng)
        
        val bearing = Math.toDegrees(Math.atan2(y, x))
        return (bearing + 360) % 360
    }

    override fun onResume() {
        super.onResume()
        try {
            // 重启位置服务
            locationClient.startLocation()
            
            // 强制刷新高亮
            Handler(Looper.getMainLooper()).postDelayed({
                if (steps != null) {
                    currentStepIndex = -2  // 强制刷新
                    updateStepHighlight()
                }
            }, 500)
        } catch (e: Exception) {
            Log.e("ResponseActivity", "onResume错误", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
        locationClient.stopLocation()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        locationClient.onDestroy()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_step_index", currentStepIndex)
        outState.putDouble("current_latitude", currentLatitude)
        outState.putDouble("current_longitude", currentLongitude)
    }
}