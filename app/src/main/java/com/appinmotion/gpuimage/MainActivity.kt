package com.appinmotion.gpuimage

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.wefika.flowlayout.FlowLayout
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 10
    }

    private lateinit var gpuImageView: GPUImageView
    private var cameraProvider: ProcessCameraProvider? = null
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var converter: YuvToRgbConverter
    private var bitmap: Bitmap? = null
    private lateinit var buttonContainer: FlowLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // YuvToRgb converter.
        converter = YuvToRgbConverter(this)

        // Init views.
        addButtons()
        buttonContainer = findViewById(R.id.button_container)
        gpuImageView = findViewById(R.id.gpu_image_view)

        // The activity is locked to portrait mode. We only need to correct for sensor rotation.
        gpuImageView.rotation = 90F
        gpuImageView.setScaleType(GPUImage.ScaleType.CENTER_CROP)

        // Camera permission needed for CameraX.
        requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)

        // Init CameraX.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()
            startCameraIfReady()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun addButtons() {
        addButton("no filter", GPUImageFilter())
        addButton("sketch", GPUImageSketchFilter())
        addButton("color invert", GPUImageColorInvertFilter())
        addButton("solarize", GPUImageSolarizeFilter())
        addButton("grayscale", GPUImageGrayscaleFilter())
        addButton("brightness", GPUImageBrightnessFilter(.8f))
        addButton("contrast", GPUImageContrastFilter(2f))
        addButton("pixelation", GPUImagePixelationFilter().apply { setPixel(20F) })
        addButton("glass sphere", GPUImageGlassSphereFilter())
        addButton("crosshatch", GPUImageCrosshatchFilter())
        addButton("gamma", GPUImageGammaFilter(2f))
    }

    private fun addButton(text: String, filter: GPUImageFilter?) {
        val button = Button(this).apply {
            setText(text)
            setOnClickListener {
                gpuImageView.filter = filter
            }
        }
        buttonContainer.addView(button, FlowLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun startCameraIfReady() {
        if (!isPermissionsGranted() || cameraProvider == null) {
            return;
        }
        val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        imageAnalysis.setAnalyzer(executor, ImageAnalysis.Analyzer {
            var bitmap = allocateBitmapIfNecessary(it.width, it.height)
            converter.yuvToRgb(it.image!!, bitmap)
            it.close()
            gpuImageView.post {
                gpuImageView.setImage(bitmap)
            }
        })
        cameraProvider!!.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis)
    }

    private fun allocateBitmapIfNecessary(width: Int, height: Int): Bitmap {
        if (bitmap == null || bitmap!!.width != width || bitmap!!.height != height) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        return bitmap!!
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            startCameraIfReady()
        }
    }

    private fun isPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}
