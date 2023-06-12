package com.jiangdg.demo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.utils.ToastUtils
import com.jiangdg.ausbc.utils.Utils
import com.jiangdg.ausbc.widget.CaptureMediaView
import com.jiangdg.demo.databinding.ActivityCameraViewDemoBinding
import com.jiangdg.utils.imageloader.ILoader
import com.jiangdg.utils.imageloader.ImageLoaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraViewDemoActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityCameraViewDemoBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityCameraViewDemoBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        viewBinding.lensFacingBtn1.setOnClickListener {
            showUsbDevicesDialog()
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
            showResolutionDialog()
        }
    }

    @SuppressLint("CheckResult")
    private fun showUsbDevicesDialog(
    ) {

        val usbDeviceList: MutableList<UsbDevice> = viewBinding.cameraView.getDeviceList()?:ArrayList()
        val curDevice = viewBinding.cameraView.getCurrentDevice()
        if (usbDeviceList.isNullOrEmpty()) {
            ToastUtils.show("Get usb device failed")
            return
        }
        val list = arrayListOf<String>()
        var selectedIndex: Int = -1
        for (index in (0 until usbDeviceList.size)) {
            val dev = usbDeviceList[index]
            val devName =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !dev.productName.isNullOrEmpty()) {
                    "${dev.productName}(${curDevice?.deviceId})"
                } else {
                    dev.deviceName
                }
            val curDevName =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !curDevice?.productName.isNullOrEmpty()) {
                    "${curDevice!!.productName}(${curDevice.deviceId})"
                } else {
                    curDevice?.deviceName
                }
            if (devName == curDevName) {
                selectedIndex = index
            }
            list.add(devName)
        }
        MaterialDialog(this).show {
            listItemsSingleChoice(
                items = list,
                initialSelection = selectedIndex
            ) { dialog, index, text ->
                if (selectedIndex == index) {
                    return@listItemsSingleChoice
                }
                this@CameraViewDemoActivity.viewBinding.cameraView.switchCamera(usbDeviceList[index])
            }
        }
    }

    @SuppressLint("CheckResult")
    fun showResolutionDialog() {
        if (!viewBinding.cameraView.isCameraOpened()) {
            ToastUtils.show("camera not worked!")
            return
        }
        viewBinding.cameraView.getAllPreviewSizes().let { previewSizes ->
            if (previewSizes.isNullOrEmpty()) {
                ToastUtils.show("Get camera preview size failed")
                return
            }
            val list = arrayListOf<String>()
            var selectedIndex: Int = -1
            for (index in (0 until previewSizes.size)) {
                val w = previewSizes[index].width
                val h = previewSizes[index].height
                viewBinding.cameraView.getCurrentPreviewSize()?.apply {
                    if (width == w && height == h) {
                        selectedIndex = index
                    }
                }
                list.add("$w x $h")
            }
            MaterialDialog(this).show {
                listItemsSingleChoice(
                    items = list,
                    initialSelection = selectedIndex
                ) { dialog, index, text ->
                    if (selectedIndex == index) {
                        return@listItemsSingleChoice
                    }
                    viewBinding.cameraView.updateResolution(previewSizes[index].width, previewSizes[index].height)
                }
            }
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