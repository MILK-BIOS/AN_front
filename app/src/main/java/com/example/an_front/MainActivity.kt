package com.example.an_front

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import android.os.Looper
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.core.content.ContextCompat
import okhttp3.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream

import java.io.IOException
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.text.Html
import android.util.TypedValue
import java.util.concurrent.TimeUnit

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
//import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.example.an_front.databinding.ActivityMainBinding
import com.iflytek.cloud.ErrorCode
import com.iflytek.cloud.InitListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechRecognizer
import com.iflytek.cloud.ui.RecognizerDialog
import com.iflytek.cloud.ui.RecognizerDialogListener
import org.json.JSONException
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import com.iflytek.cloud.SpeechUtility


class MainActivity : AppCompatActivity() {

    private val mainTAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private var mIat: SpeechRecognizer? = null // 语音听写对象
    private var mIatDialog: RecognizerDialog? = null // 语音听写UI

    // 用HashMap存储听写结果
    private val mIatResults = LinkedHashMap<String, String>()

    private lateinit var mSharedPreferences: SharedPreferences // 缓存

    private val mEngineType = SpeechConstant.TYPE_CLOUD // 引擎类型
    private var language = "zh_cn" // 识别语言
    private val resultType = "json" // 结果内容数据格式

    private val silentTimeout = "4000" // 静音超时时间
    private val backedTimeout = "2000" // 后端点静音检测时间ms

    private lateinit var cameraExecutor: ExecutorService
    private val modelPath = "yolov8n_float32.tflite"
    private val labelPath = "labels.txt"
    private var interpreter: Interpreter? = null
    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0
    private var labels = mutableListOf<String>()
    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build() // preprocess input
    private var latestBitmap: Bitmap? = null
    private var imageAnalysis: androidx.camera.core.ImageAnalysis? = null
    private val distanceCalculator = MonocularDistanceCalculator()
    private var lastResponseText: String? = null
    private var lastAddressText: String? = null
    private var hasResponse = false


    // 位置相关变量
    private lateinit var locationClient: AMapLocationClient
    private var currentLocation: com.amap.api.location.AMapLocation? = null
 

    companion object {
        // 保留现有常量
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.3F
        private const val IOU_THRESHOLD = 0.5F
        
        // 修改权限相关常量，添加位置权限
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,         // 添加麦克风权限
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            initializeAllFeatures()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.detectionImageView.setBackgroundColor("#EFEFEF".toColorInt())
        
        // 设置高德隐私协议(必须在初始化前调用)
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)
        
        try {
            // 初始化定位
            locationClient = AMapLocationClient(applicationContext)
            
            // 设置定位回调监听
            locationClient.setLocationListener { aMapLocation ->
                if (aMapLocation != null) {
                    if (aMapLocation.errorCode == 0) {
                        // 定位成功
                        currentLocation = aMapLocation
                        binding.textView.text = "已获取位置信息"
                    } else {
                        // 定位失败
                        binding.textView.text = "定位失败: ${aMapLocation.errorInfo}"
                    }
                }
            }
            
            // 配置定位参数
            val option = AMapLocationClientOption().apply {
                // 高精度定位模式
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                // 设置定位间隔，单位毫秒
                interval = 2000
            }
            
            locationClient.setLocationOption(option)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        

        mSharedPreferences = getSharedPreferences("ASR", Activity.MODE_PRIVATE)
        // 保留其他初始化代码
        val model = FileUtil.loadMappedFile(this, modelPath)
        val options = Interpreter.Options()
        options.numThreads = 4
        interpreter = Interpreter(model, options)
        val inputShape = interpreter!!.getInputTensor(0).shape()
        val outputShape = interpreter!!.getOutputTensor(0).shape()

        tensorWidth = inputShape[1]
        tensorHeight = inputShape[2]
        numChannel = outputShape[1]
        numElements = outputShape[2]

        try {
            val inputStream: InputStream = this.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = reader.readLine()
            while (line != null && line != "") {
                labels.add(line)
                line = reader.readLine()
            }
            reader.close()
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val bitmap = loadSampleBitmap() // 替换为实际的图像加载逻辑
        val resizedBitmap = bitmap.scale(tensorWidth, tensorHeight, false)
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(tensorImage)
        val imageBuffer = processedImage.buffer
        val output = TensorBuffer.createFixedSize(intArrayOf(1 , numChannel, numElements), OUTPUT_IMAGE_TYPE)
        interpreter!!.run(imageBuffer, output.buffer)
        val bestBoxes = bestBox(output.floatArray)
        if (bestBoxes != null) {
            drawBoundingBoxes(bitmap, bestBoxes)
        }
        val viewLastResponseButton = findViewById<Button>(R.id.viewLastResponseButton)
        viewLastResponseButton.setOnClickListener {
            if (hasResponse) {
                val intent = Intent(this, ResponseActivity::class.java).apply {
                    putExtra(ResponseActivity.EXTRA_RESPONSE_TEXT, lastResponseText)
                    putExtra(ResponseActivity.EXTRA_ADDRESS, lastAddressText)
                    putExtra(ResponseActivity.EXTRA_LATITUDE, currentLocation?.latitude ?: 0.0)
                    putExtra(ResponseActivity.EXTRA_LONGITUDE, currentLocation?.longitude ?: 0.0)
                }
                startActivity(intent)
            } else {
                binding.textView.text = "尚无对话记录"
            }
        }
    }

    private fun initializeAllFeatures() {
        // 初始化相机z
        startCamera()
        
        // 初始化位置服务
        getCurrentLocation()
        
        // 初始化语音识别
        initSpeech()
    }

    private fun setParam() {
        mIat?.setParameter(SpeechConstant.PARAMS, null)
        mIat?.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType)
        mIat?.setParameter(SpeechConstant.RESULT_TYPE, resultType)

        if (language == "zh_cn") {
            val lag = mSharedPreferences.getString("iat_language_preference", "mandarin")
            Log.e(mainTAG, "language:$language")
            mIat?.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
            mIat?.setParameter(SpeechConstant.ACCENT, lag)
        } else {
            mIat?.setParameter(SpeechConstant.LANGUAGE, language)
        }
        Log.e(mainTAG, "last language:" + mIat?.getParameter(SpeechConstant.LANGUAGE))

        mIat?.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", silentTimeout))
        mIat?.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", backedTimeout))
        mIat?.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "1"))
        mIat?.setParameter("dwa", "wpgs")
    }

    private fun printResult(results: RecognizerResult) {
        val text = JsonParser.parseIatResult(results.resultString)

        var sn: String? = null
        var pgs: String? = null
        var rg: String? = null
        try {
            val resultJson = JSONObject(results.resultString)
            sn = resultJson.optString("sn")
            pgs = resultJson.optString("pgs")
            rg = resultJson.optString("rg")
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        if (pgs == "rpl") {
            val strings = rg?.replace("[", "")?.replace("]", "")?.split(",") ?: emptyList()
            val begin = strings[0].toInt()
            val end = strings[1].toInt()
            for (i in begin..end) {
                mIatResults.remove(i.toString())
            }
        }

        if (!sn.isNullOrEmpty()) {
            mIatResults[sn] = text
        }

        val resultBuffer = StringBuilder()
        for (key in mIatResults.keys) {
            resultBuffer.append(mIatResults[key])
        }

        binding.tvResult.text = resultBuffer.toString()
    }

    private val mInitListener = InitListener { code ->
        Log.d(mainTAG, "SpeechRecognizer init() code = $code")
        if (code != ErrorCode.SUCCESS) {
            showMsg("初始化失败，错误码：$code,请点击网址https://www.xfyun.cn/document/error-code查询解决方案")
        }
    }

    private val mRecognizerDialogListener = object : RecognizerDialogListener {
        override fun onResult(results: RecognizerResult, isLast: Boolean) {
            printResult(results)
            if(isLast){
                val message = binding.tvResult.text.toString()
                if (message.isNotEmpty()) {
                    sendMessageWithLocation(message)
                } else {
                    binding.textView.text = "请输入消息内容"
                }
            }
        }

        override fun onError(error: SpeechError) {
            showMsg(error.getPlainDescription(true))
        }

    }

    private fun showMsg(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun initSpeech() {
        try {
            mIat = SpeechRecognizer.createRecognizer(this, mInitListener)
            mIatDialog = RecognizerDialog(this, mInitListener)
            mIatDialog!!.setListener(mRecognizerDialogListener)
            setParam()
        } catch (e: Exception) {
            showMsg("语音识别初始化失败: ${e.message}")
        }
    }

    private fun determineScreenPosition(centerX: Float, screenWidth: Int): String {
        val leftThreshold = screenWidth / 3.0f
        val rightThreshold = 2 * screenWidth / 3.0f
        
        return when {
            centerX < leftThreshold -> "左侧"
            centerX > rightThreshold -> "右侧"
            else -> "中间"
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        try {
            // 启动定位
            locationClient.startLocation()
        } catch (e: Exception) {
            binding.textView.text = "位置获取失败: ${e.message}"
        }
    }

    private fun compressBitmapToBase64(bitmap: Bitmap, quality: Int): String {
        val outputStream = ByteArrayOutputStream()
        
        // 先调整图像尺寸以减少数据大小
        val maxSize = 800 // 最大尺寸为800像素
        val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * ratio).toInt()
            val newHeight = (bitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        
        // 压缩为JPEG
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        
        // 转换为Base64
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
    }

    private fun sendMessageWithLocation(messages: String) {
        // 获取当前位置信息
        if (currentLocation == null) {
            // 如果没有位置信息，尝试再次获取 
            getCurrentLocation()
            binding.textView.text = "正在获取位置信息，请稍后再试..."
            return
        }

        // 检查是否有最新的相机图像
        if (latestBitmap == null) {
            binding.textView.text = "正在获取相机图像，请稍后再试..."
            return
        }
        
        // 使用高德位置对象
        val latitude = currentLocation?.latitude ?: 0.0
        val longitude = currentLocation?.longitude ?: 0.0
        
        // 高德SDK已经提供了地址信息，可以直接使用
        val address = currentLocation?.address ?: "未知地址"

        // 压缩并转换图像为Base64
        val imageBase64 = compressBitmapToBase64(latestBitmap!!, 80)

        // 构建JSON数据并发送
        val json = JSONObject().apply {
            put("messages", messages)
            put("latitude", latitude)
            put("longitude", longitude)
            put("current_address", address)
            put("image", imageBase64)
            put("config", JSONObject().apply {
                put("configurable", JSONObject().apply {
                    put("thread_id", "25315")
                })
            })
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // 如果响应非常长，可以设置得更高
            .build()
        // 替换为你的后端API地址
        val request = Request.Builder()
            .url("https://4f2w793170.goho.co/chat")
            .post(requestBody)
            .build()

        binding.textView.text = "正在发送消息和位置信息..."

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    binding.textView.text = "消息发送失败: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string() ?: "服务器无响应"
                
                runOnUiThread {
                    // 存储响应
                    lastResponseText = responseText
                    lastAddressText = address
                    hasResponse = true
                    
                    // 启动新的Activity显示响应
                    val intent = Intent(this@MainActivity, ResponseActivity::class.java).apply {
                        putExtra(ResponseActivity.EXTRA_RESPONSE_TEXT, responseText)
                        putExtra(ResponseActivity.EXTRA_ADDRESS, address)
                        putExtra(ResponseActivity.EXTRA_LATITUDE, currentLocation?.latitude ?: 0.0)
                        putExtra(ResponseActivity.EXTRA_LONGITUDE, currentLocation?.longitude ?: 0.0)
                    }
                    startActivity(intent)

                }
            }
        })
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double, callback: (String?) -> Unit) {
        val client = OkHttpClient()
        
        // 注意：需要替换YOUR_AMAP_KEY为你申请的高德Web服务API的Key
        val url = "https://restapi.amap.com/v3/geocode/regeo" +
                "?key=68c551c86aa0a3b983bd8e383e900e14" +
                "&location=$longitude,$latitude" +  // 注意高德API中经度在前，纬度在后
                "&poitype=&radius=1000&extensions=all&batch=false&roadlevel=0"
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // 请求失败时回调null
                callback(null)
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    // 解析JSON响应获取地址
                    val jsonObject = JSONObject(responseBody)
                    val regeocode = jsonObject.getJSONObject("regeocode")
                    val formattedAddress = regeocode.getString("formatted_address")
                    callback(formattedAddress)
                } catch (e: Exception) {
                    e.printStackTrace()
                    callback(null)
                }
            }
        })
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // 创建预览用例
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            // 创建图像分析用例
            imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    // 将相机图像转换为Bitmap
                    val bitmap = imageProxyToBitmap(imageProxy)
                    latestBitmap = bitmap
                    
                    // 创建适合模型输入的resized bitmap
                    val resizedBitmap = Bitmap.createScaledBitmap(bitmap, tensorWidth, tensorHeight, false)
                    
                    // 创建TensorImage并处理
                    val tensorImage = TensorImage(DataType.FLOAT32)
                    tensorImage.load(resizedBitmap)
                    val processedImage = imageProcessor.process(tensorImage)
                    
                    // 运行推理
                    val imageBuffer = processedImage.buffer
                    val output = TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)
                    interpreter!!.run(imageBuffer, output.buffer)
                    
                    // 查找最佳边界框
                    val bestBoxes = bestBox(output.floatArray)
                    
                    // 收集近距离物体信息
                    val nearbyObjects = mutableListOf<String>()
                    
                    // 如果找到边界框，则绘制并显示
                    if (bestBoxes != null) {
                        val resultBitmap = drawBoundingBoxes(bitmap, bestBoxes)
                        
                        // 收集近距离物体信息
                        for (box in bestBoxes) {
                            val rect = RectF(
                                box.x1 * bitmap.width,
                                box.y1 * bitmap.height,
                                box.x2 * bitmap.width,
                                box.y2 * bitmap.height
                            )
                            
                            // 计算距离
                            val distance = distanceCalculator.calculateDistance(
                                rect, 
                                box.clsName, 
                                bitmap.width, 
                                bitmap.height
                            )
                            
                            // 如果距离小于3米，添加到近距离物体列表
                            if (distance != null && distance < 3.0) {
                                val centerX = (rect.left + rect.right) / 2
                                val position = determineScreenPosition(centerX, bitmap.width)
                                nearbyObjects.add("${box.clsName}: ${distance}米，位于${position}")
                            }
                        }
                        
                        // 在UI线程更新ImageView和文本视图
                        runOnUiThread {
                            binding.detectionImageView.setImageBitmap(resultBitmap)
                            
                            if (nearbyObjects.isEmpty()) {
                                binding.textView.text = "没有检测到3米内的物体"
                                binding.textView.setTextColor(Color.WHITE)
                                binding.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                            } else {
                                // 使用更明确的格式显示每个物体的信息
                                val warningHeader = "<font color='#FF0000'><b>⚠️ 警告：3米内检测到物体 ⚠️</b></font>"
                                val objectsList = nearbyObjects.mapIndexed { index, info -> 
                                    "<font color='#FFA500'><b>${index + 1}. $info</b></font>"
                                }.joinToString("<br>")
                                
                                val warningText = "$warningHeader<br>$objectsList"
                                binding.textView.text = Html.fromHtml(warningText, Html.FROM_HTML_MODE_COMPACT)
                                binding.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                            }
                        }
                    } else {
                        // 如果没有找到边界框，仍然显示原始图像
                        runOnUiThread {
                            binding.detectionImageView.setImageBitmap(bitmap)
                            binding.textView.text = "未检测到物体"
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // 解绑所有用例
                cameraProvider.unbindAll()
                
                // 将用例绑定到相机
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadSampleBitmap(): Bitmap {
        // 返回从相机捕获的最新位图，如果没有则返回默认位图
        return latestBitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    data class BoundingBox(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val cx: Float,
        val cy: Float,
        val w: Float,
        val h: Float,
        val cnf: Float,
        val cls: Int,
        val clsName: String
    )

    private fun bestBox(array: FloatArray) : List<BoundingBox>? {

        val boundingBoxes = mutableListOf<BoundingBox>()

        for (c in 0 until numElements) {
            var maxConf = -1.0f
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + numElements * j
            while (j < numChannel){
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                val clsName = labels[maxIdx]
                val cx = array[c] // 0
                val cy = array[c + numElements] // 1
                val w = array[c + numElements * 2]
                val h = array[c + numElements * 3]
                val x1 = cx - (w/2F)
                val y1 = cy - (h/2F)
                val x2 = cx + (w/2F)
                val y2 = cy + (h/2F)
                if (x1 < 0F || x1 > 1F) continue
                if (y1 < 0F || y1 > 1F) continue
                if (x2 < 0F || x2 > 1F) continue
                if (y2 < 0F || y2 > 1F) continue

                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        cx = cx, cy = cy, w = w, h = h,
                        cnf = maxConf, cls = maxIdx, clsName = clsName
                    )
                )
            }
        }

        if (boundingBoxes.isEmpty()) return null

        return applyNMS(boundingBoxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>) : MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while(sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                val iou = calculateIoU(first, nextBox)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    private fun drawBoundingBoxes(bitmap: Bitmap, boxes: List<BoundingBox>): Bitmap {
        // 创建透明Bitmap
        val mutableBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mutableBitmap)
        
        // 绘制原始图像
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
        }

        for (box in boxes) {
            val rect = RectF(
                box.x1 * mutableBitmap.width,
                box.y1 * mutableBitmap.height,
                box.x2 * mutableBitmap.width,
                box.y2 * mutableBitmap.height
            )
            
            // 计算距离
            val distance = distanceCalculator.calculateDistance(
                rect, 
                box.clsName, 
                mutableBitmap.width, 
                mutableBitmap.height
            )
            
            // 根据距离调整颜色
            if (distance != null) {
                paint.color = if (distance < 20.0) Color.GREEN else Color.RED
            }
            
            canvas.drawRect(rect, paint)
            
            // 绘制标签和距离信息
            val label = if (distance != null) {
                "${box.clsName} ${distance}m"
            } else {
                box.clsName
            }
            
            // 绘制半透明背景
            val textBgPaint = Paint().apply {
                color = Color.BLACK
                alpha = 128 // 50% 透明度
            }
            canvas.drawRect(
                rect.left, 
                rect.bottom - 50f, 
                rect.left + textPaint.measureText(label) + 20f, 
                rect.bottom, 
                textBgPaint
            )
            
            canvas.drawText(label, rect.left + 10f, rect.bottom - 10f, textPaint)
        }

        return mutableBitmap
    }


    @SuppressLint("UnsafeOptInUsageError")
    private fun imageProxyToBitmap(imageProxy: androidx.camera.core.ImageProxy): Bitmap {
        val image = imageProxy.image ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        // 从Image获取YUV数据
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        // U和V是交错的
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )
        
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            .let { bitmap ->
                // 根据旋转角度旋转位图
                val matrix = Matrix()
                matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
            }
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) {
            getCurrentLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        // 停止定位
        locationClient.stopLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 销毁定位客户端
        locationClient.onDestroy()
        cameraExecutor.shutdown()
        mIat?.cancel()
        mIat?.destroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // 所有权限都已获取，初始化所有功能
                initializeAllFeatures()
                binding.btnStart.setOnClickListener {
                    if (mIat == null) {
                        showMsg("创建对象失败，请确认 libmsc.so 放置正确，且有调用 createUtility 进行初始化")
                        return@setOnClickListener
                    }

                    mIatResults.clear() // 清除数据
                    setParam() // 设置参数
                    mIatDialog?.setListener(mRecognizerDialogListener) // 设置监听
                    mIatDialog?.show() // 显示对话框
                    // 提示语为空，不显示提示语
                    val txt = mIatDialog?.window?.decorView?.findViewWithTag<TextView>("textlink")
                    txt?.text = ""
                }
            } else {
                // 分别检查各权限并显示相应提示
                when {
                    !hasCameraPermission() -> {
                        binding.textView.text = "请授予相机权限以使用检测功能"
                    }
                    !hasLocationPermission() -> {
                        binding.textView.text = "请授予位置权限以发送位置信息"
                    }
                    !isMicrophonePermissionGranted() -> {
                        binding.textView.text = "请授予麦克风权限以使用语音识别功能"
                    }
                    !isNetworkPermissionGranted() -> {
                        binding.textView.text = "请授予网络权限以与服务器通信"
                    }
                    else -> {
                        binding.textView.text = "请授予必要权限以使用所有功能"
                    }
                }
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || 
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 麦克风权限检查
    private fun isMicrophonePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 网络权限检查
    private fun isNetworkPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_NETWORK_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }
}