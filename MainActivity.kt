package com.vivo.faceemotionanalyzer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.ImageFormat
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    // 控件
    private var previewView: PreviewView? = null
    private var imageView: ImageView? = null
    private var faceFrame: View? = null
    private var previewHint: TextView? = null
    private var cameraBtn: Button? = null
    private var uploadBtn: Button? = null
    private var detectBtn: Button? = null
    private var statusText: TextView? = null
    private var mainEmotionText: TextView? = null
    private var happyText: TextView? = null
    private var sadText: TextView? = null
    private var angryText: TextView? = null
    private var surprisedText: TextView? = null
    private var neutralText: TextView? = null
    private var fearText: TextView? = null
    private var contemptText: TextView? = null // 新增：第7类

    // 相机 & 状态
    private var isCameraOn = false
    private var isRealTimeAnalyzing = false
    private var selectedImageUri: Uri? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // TFLite
    private var tflite: Interpreter? = null

    // 权限请求码
    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_STORAGE_PERMISSION = 101

    // 帧分析间隔（每 N 帧分析一次）
    private val ANALYZE_EVERY_N_FRAMES = 10
    private val frameCounter = AtomicInteger(0)

    // 相册选择
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            imageView?.setImageURI(it)
            imageView?.visibility = View.VISIBLE
            previewView?.visibility = View.GONE
            if (isCameraOn) {
                cameraProvider?.unbindAll()
                isCameraOn = false
                cameraBtn?.text = "打开相机"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupButtonListeners()
        loadTFLiteModel()
    }

    // 加载 TFLite 模型
    private fun loadTFLiteModel() {
        try {
            val assetFileDescriptor = assets.openFd("emotion_model.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val mappedByteBuffer: MappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )

            val options = Interpreter.Options()
            options.setNumThreads(4)
            tflite = Interpreter(mappedByteBuffer, options)

            runOnUiThread {
                statusText?.text = "✅ 模型加载成功"
                statusText?.setTextColor(Color.GREEN)
            }

        } catch (e: Exception) {
            runOnUiThread {
                statusText?.text = "❌ 模型加载失败：${e.message}"
                statusText?.setTextColor(Color.RED)
            }
            e.printStackTrace()
        }
    }

    // 情绪识别 - 适配 7 分类
    private fun analyzeEmotion(bitmap: Bitmap) {
        try {
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            // 🔴 关键修改：模型输出是 [1,7]，所以输入数组要变成 7
            val inputBuffer = Array(1) { Array(48) { Array(48) { FloatArray(1) } } }

            for (y in 0 until 48) {
                for (x in 0 until 48) {
                    val pixel = resizedBitmap.getPixel(x, y)
                    val gray = Color.red(pixel) * 0.299f +
                            Color.green(pixel) * 0.587f +
                            Color.blue(pixel) * 0.114f
                    inputBuffer[0][y][x][0] = gray / 255.0f
                }
            }

            // 🔴 关键修改：输出数组要对应 7 个分类
            val outputBuffer = Array(1) { FloatArray(7) }
            tflite?.run(inputBuffer, outputBuffer)
            val results = outputBuffer[0]

            // 🔴 关键修改：映射到 7 个标签
            val emotionMap = mapOf(
                "angry"    to results[0],
                "fear"     to results[1],
                "happy"    to results[2],
                "sad"      to results[3],
                "surprise" to results[4],
                "neutral"  to results[5],
                "contempt" to results[6] // 第7类：轻蔑
            )

            updateUI(emotionMap)

        } catch (e: Exception) {
            runOnUiThread {
                statusText?.text = "识别错误：${e.message}"
                statusText?.setTextColor(Color.RED)
            }
            e.printStackTrace()
        }
    }

    // 更新UI - 适配 7 分类
    private fun updateUI(emotionMap: Map<String, Float>) {
        val angry = (emotionMap["angry"]!! * 100).toInt()
        val fear = (emotionMap["fear"]!! * 100).toInt()
        val happy = (emotionMap["happy"]!! * 100).toInt()
        val sad = (emotionMap["sad"]!! * 100).toInt()
        val surprise = (emotionMap["surprise"]!! * 100).toInt()
        val neutral = (emotionMap["neutral"]!! * 100).toInt()
        val contempt = (emotionMap["contempt"]!! * 100).toInt() // 新增

        val mainEmotion = mapOf(
            "愤怒" to angry, "恐惧" to fear, "开心" to happy,
            "悲伤" to sad, "惊讶" to surprise, "中性" to neutral, "轻蔑" to contempt
        ).maxByOrNull { it.value }?.key ?: "未知"

        runOnUiThread {
            angryText?.text = "愤怒：$angry%"
            fearText?.text = "恐惧：$fear%"
            happyText?.text = "开心：$happy%"
            sadText?.text = "悲伤：$sad%"
            surprisedText?.text = "惊讶：$surprise%"
            neutralText?.text = "中性：$neutral%"
            contemptText?.text = "轻蔑：$contempt%" // 新增
            mainEmotionText?.text = mainEmotion
            // 实时分析模式下不显示"识别完成"，保持"实时识别中"状态
            if (!isRealTimeAnalyzing) {
                statusText?.text = "✅ 识别完成"
                statusText?.setTextColor(Color.GREEN)
            }
        }
    }

    // 按钮逻辑
    private fun setupButtonListeners() {
        cameraBtn?.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                toggleCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION
                )
            }
        }

        uploadBtn?.setOnClickListener {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE

            if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED
            ) {
                openGallery()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    REQUEST_STORAGE_PERMISSION
                )
            }
        }

        detectBtn?.setOnClickListener {
            if (tflite == null) {
                Toast.makeText(this, "模型尚未加载", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isCameraOn) {
                Toast.makeText(this, "请先打开相机", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 切换实时分析状态
            isRealTimeAnalyzing = !isRealTimeAnalyzing
            updateDetectBtnState()

            // 停止识别时更新状态文字
            if (!isRealTimeAnalyzing) {
                statusText?.text = "⏸️ 已停止识别"
                statusText?.setTextColor(Color.GRAY)
            }
        }
    }

    // 开关相机
    private fun toggleCamera() {
        if (isCameraOn) {
            cameraProvider?.unbindAll()
            previewView?.visibility = View.GONE
            imageView?.visibility = View.VISIBLE
            cameraBtn?.text = "打开相机"
            isCameraOn = false
            // 关闭实时分析
            isRealTimeAnalyzing = false
            updateDetectBtnState()
        } else {
            selectedImageUri = null
            startCamera()
        }
    }

    // 启动相机
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cp = cameraProviderFuture.get()
            this.cameraProvider = cp

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView?.surfaceProvider)
            }

            // 图像分析器配置
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }

            // 尝试获取可用的摄像头选择器
            val selector = getAvailableCameraSelector(cp)

            try {
                cp.unbindAll()
                cp.bindToLifecycle(this, selector, preview, imageAnalysis)

                previewView?.visibility = View.VISIBLE
                imageView?.visibility = View.GONE
                previewHint?.visibility = View.GONE
                cameraBtn?.text = "关闭相机"
                isCameraOn = true
                updateDetectBtnState()
            } catch (e: Exception) {
                Toast.makeText(this, "相机启动失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // 处理每一帧
    private fun processFrame(imageProxy: ImageProxy) {
        if (!isRealTimeAnalyzing || tflite == null) {
            imageProxy.close()
            return
        }

        // 每 N 帧分析一次
        val currentCount = frameCounter.incrementAndGet()
        if (currentCount % ANALYZE_EVERY_N_FRAMES != 0) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxy.toBitmap()
        imageProxy.close()

        if (bitmap != null) {
            cameraExecutor.execute {
                try {
                    analyzeEmotion(bitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 将 ImageProxy 转换为 Bitmap
    private fun ImageProxy.toBitmap(): Bitmap? {
        val yBuffer = planes[0].buffer
        val vuBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)
        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    // 更新识别按钮状态
    private fun updateDetectBtnState() {
        detectBtn?.text = if (isRealTimeAnalyzing) "停止识别" else "开始识别"
        detectBtn?.setBackgroundColor(
            if (isRealTimeAnalyzing) Color.parseColor("#FF5722")
            else Color.parseColor("#4CAF50")
        )
        if (isRealTimeAnalyzing) {
            statusText?.text = "🔄 实时识别中..."
            statusText?.setTextColor(Color.BLUE)
        }
    }

    // 获取可用的摄像头选择器
    private fun getAvailableCameraSelector(cameraProvider: ProcessCameraProvider): CameraSelector {
        // 尝试后置摄像头
        val backSelector = CameraSelector.DEFAULT_BACK_CAMERA
        if (cameraProvider.availableCameraInfos.any { backSelector.filter(listOf(it)).isNotEmpty() }) {
            return backSelector
        }
        // 尝试前置摄像头
        val frontSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        if (cameraProvider.availableCameraInfos.any { frontSelector.filter(listOf(it)).isNotEmpty() }) {
            return frontSelector
        }
        // 如果都没有，返回默认后置摄像头让系统自己处理
        return backSelector
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    toggleCamera()
                } else {
                    Toast.makeText(this, "需要相机权限", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bindViews() {
        previewView = findViewById(R.id.previewView)
        imageView = findViewById(R.id.imageView)
        faceFrame = findViewById(R.id.faceFrame)
        previewHint = findViewById(R.id.previewHint)
        cameraBtn = findViewById(R.id.cameraBtn)
        uploadBtn = findViewById(R.id.uploadBtn)
        detectBtn = findViewById(R.id.detectBtn)
        statusText = findViewById(R.id.statusText)
        mainEmotionText = findViewById(R.id.mainEmotionText)
        happyText = findViewById(R.id.happyText)
        sadText = findViewById(R.id.sadText)
        angryText = findViewById(R.id.angryText)
        surprisedText = findViewById(R.id.surprisedText)
        neutralText = findViewById(R.id.neutralText)
        fearText = findViewById(R.id.fearText)
        contemptText = findViewById(R.id.contemptText) // 新增
    }

    override fun onDestroy() {
        super.onDestroy()
        tflite?.close()
        cameraExecutor.shutdown()
    }
}