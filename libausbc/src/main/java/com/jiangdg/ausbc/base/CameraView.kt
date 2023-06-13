/*
 * Copyright 2017-2022 Jiangdg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jiangdg.ausbc.base

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.R
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.callback.IPlayCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.utils.ToastUtils
import com.jiangdg.ausbc.utils.bus.BusKey
import com.jiangdg.ausbc.utils.bus.EventBus
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.CaptureMediaView
import com.jiangdg.ausbc.widget.IAspectRatio
import java.util.*

/** CameraFragment Usage Demo
 *
 * @author Created by jiangdg on 2022/1/28
 */
class CameraView : BaseCameraView, View.OnClickListener,
    LifecycleOwner, LifecycleEventObserver {
    private var captureRawImage: Boolean = false
    private var rawPreviewData: Boolean = false
    private var aspectRatioShow: Boolean = true
    private var isCapturingVideoOrAudio: Boolean = false
    private var isPlayingMic: Boolean = false
    private var mRecTimer: Timer? = null
    private var mRecSeconds = 0
    private var mRecMinute = 0
    private var mRecHours = 0
    private var isAlive = false
    private var view: View? = null


    enum class CameraState {
        OPEN, CLOSE, ERROR, READY
    }

    private var listener: ((CameraState, String) -> Unit)? = null

    private val mMainHandler: Handler by lazy {
        Handler(Looper.getMainLooper()) {
            when (it.what) {
                WHAT_START_TIMER -> {
                    if (mRecSeconds % 2 != 0) {
                        view?.findViewById<View>(R.id.recStateIv)?.visibility = View.VISIBLE
                    } else {
                        view?.findViewById<View>(R.id.recStateIv)?.visibility = View.INVISIBLE
                    }
                    view?.findViewById<TextView>(R.id.recTimeTv)?.text =
                        calculateTime(mRecSeconds, mRecMinute)
                }
                WHAT_STOP_TIMER -> {
                    view?.findViewById<View>(R.id.recTimerLayout)?.visibility = View.GONE
                    view?.findViewById<TextView>(R.id.recTimeTv)?.text = calculateTime(0, 0)
                }
            }
            true
        }
    }

    private var mCameraMode = CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC

    private var deviceFilterXml: Int? = null
    private var previewWidth: Int = 1024
    private var previewHeight: Int = 768
    private var defaultRotateType: RotateType = RotateType.ANGLE_0
    private var renderMode: CameraRequest.RenderMode = CameraRequest.RenderMode.OPENGL
    private var audioSource: CameraRequest.AudioSource = CameraRequest.AudioSource.SOURCE_SYS_MIC
    private var format: CameraRequest.PreviewFormat = CameraRequest.PreviewFormat.FORMAT_MJPEG

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAlive = true
    }

    @SuppressLint("Recycle")
    override fun initView(context: Context, attrs: AttributeSet?) {
        super.initView(context, attrs)
        val typedArray: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.CameraView)
        deviceFilterXml = typedArray.getResourceId(
            R.styleable.CameraView_defaultUsbXml,
            -1
        )
        if (deviceFilterXml == null || deviceFilterXml!! < 0) deviceFilterXml = null
        previewWidth = typedArray.getInt(
            R.styleable.CameraView_previewWidth,
            1024
        )
        previewHeight = typedArray.getInt(
            R.styleable.CameraView_previewHeight,
            768
        )
        val tmpRenderMode = typedArray.getInt(
            R.styleable.CameraView_renderMode,
            1
        )
        when (tmpRenderMode) {
            1 -> {
                renderMode = CameraRequest.RenderMode.OPENGL
            }
            2 -> {
                renderMode = CameraRequest.RenderMode.NORMAL
            }
        }
        val tmpRotateType = typedArray.getInt(
            R.styleable.CameraView_defaultRotateType,
            1
        )
        when (tmpRotateType) {
            1 -> {
                defaultRotateType = RotateType.ANGLE_0
            }
            2 -> {
                defaultRotateType = RotateType.ANGLE_90
            }
            3 -> {
                defaultRotateType = RotateType.ANGLE_180
            }
            4 -> {
                defaultRotateType = RotateType.ANGLE_270
            }
            5 -> {
                defaultRotateType = RotateType.FLIP_UP_DOWN
            }
            6 -> {
                defaultRotateType = RotateType.FLIP_LEFT_RIGHT
            }
        }
        val tmpAudioSource = typedArray.getInt(
            R.styleable.CameraView_audioSource,
            1
        )
        when (tmpAudioSource) {
            1 -> {
                audioSource = CameraRequest.AudioSource.SOURCE_SYS_MIC
            }
            2 -> {
                audioSource = CameraRequest.AudioSource.SOURCE_DEV_MIC
            }
            3 -> {
                audioSource = CameraRequest.AudioSource.SOURCE_AUTO
            }
        }
        val tmpFormat = typedArray.getInt(
            R.styleable.CameraView_format,
            1
        )
        when (tmpFormat) {
            1 -> {
                format = CameraRequest.PreviewFormat.FORMAT_MJPEG
            }
            2 -> {
                format = CameraRequest.PreviewFormat.FORMAT_MJPEG
            }
            3 -> {
                format = CameraRequest.PreviewFormat.FORMAT_YUYV
            }
        }
        aspectRatioShow = typedArray.getBoolean(
            R.styleable.CameraView_aspectRatioShow,
            true
        )
        captureRawImage = typedArray.getBoolean(
            R.styleable.CameraView_captureRawImage,
            false
        )
        rawPreviewData = typedArray.getBoolean(
            R.styleable.CameraView_rawPreviewData,
            false
        )
        typedArray.recycle()
    }

    override fun getCameraRequest(): CameraRequest {
        return CameraRequest.Builder()
            .setPreviewWidth(previewWidth)
            .setPreviewHeight(previewHeight)
            .setRenderMode(renderMode)
            .setDefaultRotateType(defaultRotateType)
            .setAudioSource(audioSource)
            .setPreviewFormat(format)
            .setAspectRatioShow(aspectRatioShow)
            .setCaptureRawImage(captureRawImage)
            .setRawPreviewData(rawPreviewData)
            .create();
    }

    override fun initData() {
        super.initData()
//        deviceFilterXml = R.xml.default_device_filter2
        if (deviceFilterXml != null) {
            setDefaultUsbDeviceList(deviceFilterXml)
        }
        EventBus.with<Int>(BusKey.KEY_FRAME_RATE).observe(this) {
        }

        EventBus.with<Boolean>(BusKey.KEY_RENDER_READY).observe(this) { ready ->
            if (!ready) return@observe
            listener?.invoke(CameraState.READY, "")
        }
    }

    override fun getDefaultCamera(): UsbDevice? {
        return super.getDefaultCamera()
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        when (code) {
            ICameraStateCallBack.State.OPENED -> handleCameraOpened()
            ICameraStateCallBack.State.CLOSED -> handleCameraClosed()
            ICameraStateCallBack.State.ERROR -> handleCameraError(msg)
        }
    }

    fun setCameraListener(listener: ((CameraState, String) -> Unit)) {
        this.listener = listener
    }

    private fun handleCameraError(msg: String?) {
        listener?.invoke(CameraState.ERROR, msg ?: "")
    }

    private fun handleCameraClosed() {
        listener?.invoke(CameraState.CLOSE, "")
    }

    private fun handleCameraOpened() {
        listener?.invoke(CameraState.OPEN, "")
    }

    fun setMode(mode: CaptureMediaView.CaptureMode) {
        if (mCameraMode == mode) {
            return
        }
        mCameraMode = mode
    }

    override fun getCameraView(): IAspectRatio {
        return AspectRatioTextureView(mContext)
    }

    override fun getCameraViewContainer(): ViewGroup {
        return view?.findViewById(R.id.cameraViewContainer)!!
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        view = inflater.inflate(R.layout.view_camera, container, true)
        return view!!
    }

    override fun getGravity(): Int = Gravity.CENTER


    fun captureAudio(path: String? = null, callback: (err: String, path: String?) -> Unit) {
        if (!isCameraOpened()) {
            callback.invoke("camera not worked!", null)
            return
        }
        if (isCapturingVideoOrAudio) {
            captureAudioStop()
            return
        }
        captureAudioStart(object : ICaptureCallBack {
            override fun onBegin() {
                isCapturingVideoOrAudio = true
                view?.findViewById<View>(R.id.recTimerLayout)?.visibility = View.VISIBLE
                startMediaTimer()
            }

            override fun onError(error: String?) {
                isCapturingVideoOrAudio = false
                stopMediaTimer()
                callback.invoke(error ?: "未知异常", null)
            }

            override fun onComplete(path: String?) {
                isCapturingVideoOrAudio = false
                view?.findViewById<View>(R.id.recTimerLayout)?.visibility = View.GONE
                stopMediaTimer()
                callback.invoke("", path)
            }

        }, path)
    }

    fun captureVideo(path: String? = null, callback: (err: String, path: String?) -> Unit) {
        if (!isCameraOpened()) {
            callback.invoke("camera not worked!", null)
            return
        }
        if (isCapturingVideoOrAudio) {
            captureVideoStop()
            return
        }
        captureVideoStart(object : ICaptureCallBack {
            override fun onBegin() {
                isCapturingVideoOrAudio = true
                view?.findViewById<View>(R.id.recTimerLayout)?.visibility = View.VISIBLE
                startMediaTimer()
            }

            override fun onError(error: String?) {
                isCapturingVideoOrAudio = false
                stopMediaTimer()
                callback.invoke(error ?: "未知异常", null)
            }

            override fun onComplete(path: String?) {
                ToastUtils.show(path ?: "")
                isCapturingVideoOrAudio = false
                view?.findViewById<View>(R.id.recTimerLayout)?.visibility = View.GONE
                stopMediaTimer()
                callback.invoke("", path)
            }

        }, path)
    }

    fun captureImage(path: String? = null, callback: (err: String, path: String?) -> Unit) {
        if (!isCameraOpened()) {
            callback.invoke("camera not worked!", null)
            return
        }
        captureImage(object : ICaptureCallBack {
            override fun onBegin() {
            }

            override fun onError(error: String?) {
                callback.invoke(error ?: "未知异常", null)
            }

            override fun onComplete(path: String?) {
                callback.invoke("", path)
            }
        }, path)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAlive = false
    }

    override fun onClick(v: View?) {

    }


    fun playMic(enable: Boolean) {
        if (enable) {
            if (isPlayingMic) return
            startPlayMic(object : IPlayCallBack {
                override fun onBegin() {
//                mViewBinding.voiceBtn.setImageResource(R.mipmap.camera_voice_on)
                    isPlayingMic = true
                }

                override fun onError(error: String) {
//                     mViewBinding.voiceBtn.setImageResource(R.mipmap.camera_voice_off)
                    isPlayingMic = false
                }

                override fun onComplete() {
//                     mViewBinding.voiceBtn.setImageResource(R.mipmap.camera_voice_off)
                    isPlayingMic = false
                }
            })
        } else {
            if (!isPlayingMic) return
            stopPlayMic()
        }
    }


    private fun startMediaTimer() {
        val pushTask: TimerTask = object : TimerTask() {
            override fun run() {
                //秒
                mRecSeconds++
                //分
                if (mRecSeconds >= 60) {
                    mRecSeconds = 0
                    mRecMinute++
                }
                //时
                if (mRecMinute >= 60) {
                    mRecMinute = 0
                    mRecHours++
                    if (mRecHours >= 24) {
                        mRecHours = 0
                        mRecMinute = 0
                        mRecSeconds = 0
                    }
                }
                mMainHandler.sendEmptyMessage(WHAT_START_TIMER)
            }
        }
        if (mRecTimer != null) {
            stopMediaTimer()
        }
        mRecTimer = Timer()
        //执行schedule后1s后运行run，之后每隔1s运行run
        mRecTimer?.schedule(pushTask, 1000, 1000)
    }

    private fun stopMediaTimer() {
        if (mRecTimer != null) {
            mRecTimer?.cancel()
            mRecTimer = null
        }
        mRecHours = 0
        mRecMinute = 0
        mRecSeconds = 0
        mMainHandler.sendEmptyMessage(WHAT_STOP_TIMER)
    }

    private fun calculateTime(seconds: Int, minute: Int, hour: Int? = null): String {
        val mBuilder = java.lang.StringBuilder()
        //时
        if (hour != null) {
            if (hour < 10) {
                mBuilder.append("0")
                mBuilder.append(hour)
            } else {
                mBuilder.append(hour)
            }
            mBuilder.append(":")
        }
        // 分
        if (minute < 10) {
            mBuilder.append("0")
            mBuilder.append(minute)
        } else {
            mBuilder.append(minute)
        }
        //秒
        mBuilder.append(":")
        if (seconds < 10) {
            mBuilder.append("0")
            mBuilder.append(seconds)
        } else {
            mBuilder.append(seconds)
        }
        return mBuilder.toString()
    }

    companion object {
        private const val TAG = "DemoFragment"
        private const val WHAT_START_TIMER = 0x00
        private const val WHAT_STOP_TIMER = 0x01
    }

    override fun getLifecycle(): Lifecycle {
        if (mLifecycle == null) {
            mLifecycle = object : Lifecycle() {
                override fun addObserver(observer: LifecycleObserver) {
                }

                override fun removeObserver(observer: LifecycleObserver) {
                }

                override fun getCurrentState(): State {
                    return if (isAlive) {
                        State.CREATED
                    } else {
                        State.DESTROYED
                    }
                }

            }
        }
        return mLifecycle!!
    }

    private var mLifecycle: Lifecycle? = null
    fun setLifecycleOwner(owner: LifecycleOwner) {
        if (mLifecycle != null) mLifecycle?.removeObserver(this)
        mLifecycle = owner.lifecycle
        mLifecycle?.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_STOP) {
            if (rootView != null) {
                rootView.cancelPendingInputEvents()
            }
        }
    }

    fun getCurrentDevice(): UsbDevice? {
        getCurrentCamera()?.let { strategy ->
            if (strategy is CameraUVC) {
                return strategy.getUsbDevice()
            }
        }
        return null
    }


}
