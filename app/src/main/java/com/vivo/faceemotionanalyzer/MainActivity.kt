package com.vivo.faceemotionanalyzer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
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
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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
    private var selectedImageUri: Uri? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // TFLite
    private var tflite: Interpreter? = null

    // 权限请求码
    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_STORAGE_PERMISSION = 101

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
            statusText?.text = "✅ 识别完成"
            statusText?.setTextColor(Color.GREEN)
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

            val safeCameraBitmap = if (isCameraOn) {
                previewView?.bitmap
            } else {
                null
            }

            runOnUiThread {
                statusText?.text = "分析中..."
                statusText?.setTextColor(Color.BLUE)
                detectBtn?.isEnabled = false
            }

            cameraExecutor.execute {
                try {
                    val bitmap: Bitmap = when {
                        selectedImageUri != null -> {
                            val input = contentResolver.openInputStream(selectedImageUri!!)
                                ?: throw Exception("无法读取图片")
                            BitmapFactory.decodeStream(input)
                                ?: throw Exception("图片解析失败")
                        }
                        isCameraOn -> {
                            safeCameraBitmap ?: throw Exception("无法获取相机画面")
                        }
                        else -> throw Exception("请先打开相机或选择图片")
                    }

                    analyzeEmotion(bitmap)

                } catch (e: Exception) {
                    runOnUiThread {
                        statusText?.text = "失败：${e.message}"
                        statusText?.setTextColor(Color.RED)
                    }
                } finally {
                    runOnUiThread {
                        detectBtn?.isEnabled = true
                    }
                }
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

            val selector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cp.unbindAll()
                cp.bindToLifecycle(this, selector, preview)

                previewView?.visibility = View.VISIBLE
                imageView?.visibility = View.GONE
                previewHint?.visibility = View.GONE
                cameraBtn?.text = "关闭相机"
                isCameraOn = true
            } catch (e: Exception) {
                Toast.makeText(this, "相机启动失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
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