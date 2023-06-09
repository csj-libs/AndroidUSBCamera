package com.jiangdg.demo

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jiangdg.ausbc.utils.ToastUtils
import com.jiangdg.ausbc.utils.Utils
import com.jiangdg.ausbc.widget.CaptureMediaView
import com.jiangdg.demo.databinding.ActivityCameraViewDemoBinding
import com.jiangdg.utils.imageloader.ILoader
import com.jiangdg.utils.imageloader.ImageLoaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraViewDemoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewBinding = ActivityCameraViewDemoBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        viewBinding.lensFacingBtn1.setOnClickListener {
            viewBinding.cameraView.showDeviceDialog()
        }
        viewBinding.takePictureModeTv.setOnClickListener {
            viewBinding.cameraView.setMode(CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC)
            viewBinding.cameraView.captureImage { err, path ->
                showPic(path ?: "", viewBinding.resultImageView)
            }
        }
        viewBinding.recordVideoModeTv.setOnClickListener {
            viewBinding.cameraView.setMode(CaptureMediaView.CaptureMode.MODE_CAPTURE_VIDEO)
            viewBinding.cameraView.captureVideo() { err, path ->
                ToastUtils.show(path ?: err)
            }
        }
        viewBinding.recordAudioModeTv.setOnClickListener {
            viewBinding.cameraView.setMode(CaptureMediaView.CaptureMode.MODE_CAPTURE_AUDIO)
            viewBinding.cameraView.captureAudio { err, path ->
                ToastUtils.show(path ?: err)
            }
        }
        viewBinding.resolutionBtn.setOnClickListener {
            viewBinding.cameraView.showResolutionDialog()
        }
    }

    private fun showPic(path: String, view: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            val size = Utils.dp2px(this@CameraViewDemoActivity, 38F)
            ImageLoaders.of(this@CameraViewDemoActivity)
                .loadAsBitmap(path, size, size, object : ILoader.OnLoadedResultListener {
                    override fun onLoadedSuccess(bitmap: Bitmap?) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            view.setImageBitmap(bitmap)
                        }
                    }

                    override fun onLoadedFailed(error: Exception?) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            ToastUtils.show("Capture image error.${error?.localizedMessage}")
                        }
                    }
                })
        }
    }
}