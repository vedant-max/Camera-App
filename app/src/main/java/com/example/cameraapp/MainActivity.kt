package com.example.cameraapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.drawToBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
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
    private lateinit var uri: Uri
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().apply {
            setKeepVisibleCondition{
                viewModel.isLoading.value
            }
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
        val emptyBitmap = Bitmap.createBitmap(300,200,Bitmap.Config.RGB_565)

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener {
                takePhoto()
                viewBinding.viewFinder.visibility = View.INVISIBLE
                viewBinding.imageView.visibility = View.VISIBLE
        }
        viewBinding.rejectButton.setOnClickListener {
            viewBinding.imageView.visibility = View.INVISIBLE
            viewBinding.viewFinder.visibility = View.VISIBLE
        }
        viewBinding.random.setOnClickListener {
            viewBinding.imageView.visibility = View.VISIBLE
            viewBinding.viewFinder.visibility = View.INVISIBLE
            GlobalScope.launch(Dispatchers.Main) {
                myBitmap = viewBinding.imageView.drawToBitmap()
                val paint = Paint().apply {
                    color = Color.BLUE
                    isAntiAlias = false
                    style = Paint.Style.FILL_AND_STROKE
                }
                val canvas = Canvas(myBitmap)
                canvas.drawCircle(rand(myBitmap.width), rand(myBitmap.height), (30).toFloat(), paint)
                canvas.drawCircle(rand(myBitmap.width), rand(myBitmap.height), (30).toFloat(), paint)
                viewBinding.imageView.setImageBitmap(myBitmap)
            }
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
                    viewBinding.imageView.setImageURI(output.savedUri!!)
                    //Add Canvas Here
//                    GlobalScope.launch(Dispatchers.Main) {
//                        myBitmap = getBitmap(output.savedUri!!)
//                        val paint = Paint().apply {
//                            color = Color.BLUE
//                            isAntiAlias = false
//                            style = Paint.Style.FILL_AND_STROKE
//                        }
//                        val canvas = Canvas(myBitmap)
//                        canvas.drawCircle(rand(myBitmap.width), rand(myBitmap.height), (30).toFloat(), paint)
//                        canvas.drawCircle(rand(myBitmap.width), rand(myBitmap.height), (30).toFloat(), paint)
//                        viewBinding.imageView.setImageBitmap(myBitmap)
//                    }
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
    private suspend fun getBitmap() : Bitmap {
        val loading = ImageLoader(this)
        val request = ImageRequest.Builder(this).data(viewBinding.imageView.id).build()
        val result =
            GlobalScope.async {
                (loading.execute(request) as SuccessResult).drawable
            }
        return (result.await() as BitmapDrawable).bitmap
    }
    private fun rand(e: Int) = Random().nextInt(e).toFloat()
}