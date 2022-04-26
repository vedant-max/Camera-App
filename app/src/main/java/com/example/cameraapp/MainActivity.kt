package com.example.cameraapp

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.drawToBitmap
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.MutableLiveData
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.example.cameraapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var myBitmap: Bitmap
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().apply {
            setVisible(viewModel.isLoading.value)
        }
        supportActionBar?.hide()
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener {
            takePhoto()
            viewBinding.viewFinder.visibility = View.INVISIBLE
            viewBinding.cropper.visibility = View.VISIBLE
        }
        viewBinding.changeColor.setOnClickListener {
            val points = viewModel.coordinates
            val cropPoints = viewBinding.cropper.cropPoints
            val x1 = cropPoints[0]
            val x2 = cropPoints[2]
            val y1 = cropPoints[1]
            val y2 = cropPoints[5]
            val canvas = Canvas(myBitmap)
            val paint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.FILL_AND_STROKE
            }
            for (point in points){
                if((point.first>x1 && point.first<x2) && (point.second>y1 && point.second<y2)){
                    canvas.drawCircle(point.first,point.second,(30).toFloat(),paint)
                }
            }
        }
        viewBinding.rejectButton.setOnClickListener {
            viewBinding.cropper.visibility = View.INVISIBLE
            viewBinding.viewFinder.visibility = View.VISIBLE
            viewModel.coordinates.clear()
            viewBinding.cropper.clearImage()
        }
        viewBinding.random.setOnClickListener {
            val canvas = Canvas(myBitmap)
            val paint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.FILL_AND_STROKE
            }
            val (x,y) = Pair(rand(),rand())
            canvas.drawCircle(x,y,(30).toFloat(),paint)
            viewModel.coordinates.add(Pair(x,y))
            viewBinding.cropper.setImageBitmap(myBitmap)
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto(){
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    GlobalScope.launch(Dispatchers.Main) {
                        myBitmap = getBitmap(output.savedUri!!)
                        viewBinding.cropper.setImageBitmap(getBitmap(output.savedUri!!))
                    }
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun startCamera() {
        //bind lifecycle of camera to the lifecycle owner
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    private suspend fun getBitmap(uri: Uri) : Bitmap {
        val loading = ImageLoader(this)
        val request = ImageRequest.Builder(this).data(uri).build()
        val result =
            GlobalScope.async {
                (loading.execute(request) as SuccessResult).drawable
            }
        return (result.await() as BitmapDrawable).bitmap
    }

    private fun rand() = (100..1600).random().toFloat()
}