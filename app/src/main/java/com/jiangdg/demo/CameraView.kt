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
package com.jiangdg.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.lifecycle.*
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.BaseCameraView
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.callback.IPlayCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.render.effect.EffectBlackWhite
import com.jiangdg.ausbc.render.effect.EffectSoul
import com.jiangdg.ausbc.render.effect.EffectZoom
import com.jiangdg.ausbc.render.effect.bean.CameraEffect
import com.jiangdg.ausbc.utils.ToastUtils
import com.jiangdg.ausbc.utils.bus.BusKey
import com.jiangdg.ausbc.utils.bus.EventBus
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.CaptureMediaView
import com.jiangdg.ausbc.widget.IAspectRatio
import com.jiangdg.demo.EffectListDialog.Companion.KEY_ANIMATION
import com.jiangdg.demo.EffectListDialog.Companion.KEY_FILTER
import com.jiangdg.demo.databinding.DialogMoreBinding
import com.jiangdg.demo.databinding.ViewCameraBinding
import com.jiangdg.utils.MMKVUtils
import java.util.*

/** CameraFragment Usage Demo
 *
 * @author Created by jiangdg on 2022/1/28
 */
class CameraView : BaseCameraView, View.OnClickListener,
    LifecycleOwner, LifecycleEventObserver {
    private var mMultiCameraDialog: MultiCameraDialog? = null
    private lateinit var mMoreBindingView: DialogMoreBinding
    private var mMoreMenu: PopupWindow? = null
    private var isCapturingVideoOrAudio: Boolean = false
    private var isPlayingMic: Boolean = false
    private var mRecTimer: Timer? = null
    private var mRecSeconds = 0
    private var mRecMinute = 0
    private var mRecHours = 0
    private var isAlive = false


    enum class CameraState {
        OPEN, CLOSE, ERROR
    }

    private var listener: ((CameraState, String) -> Unit)? = null
    private val mEffectDataList by lazy {
        arrayListOf(
            CameraEffect.NONE_FILTER,
            CameraEffect(
                EffectBlackWhite.ID,
                "BlackWhite",
                CameraEffect.CLASSIFY_ID_FILTER,
                effect = EffectBlackWhite(mContext),
                coverResId = R.mipmap.filter0
            ),
            CameraEffect.NONE_ANIMATION,
            CameraEffect(
                EffectZoom.ID,
                "Zoom",
                CameraEffect.CLASSIFY_ID_ANIMATION,
                effect = EffectZoom(mContext),
                coverResId = R.mipmap.filter2
            ),
            CameraEffect(
                EffectSoul.ID,
                "Soul",
                CameraEffect.CLASSIFY_ID_ANIMATION,
                effect = EffectSoul(mContext),
                coverResId = R.mipmap.filter1
            ),
        )
    }

    private val mMainHandler: Handler by lazy {
        Handler(Looper.getMainLooper()) {
            when (it.what) {
                WHAT_START_TIMER -> {
                    if (mRecSeconds % 2 != 0) {
                        mViewBinding.recStateIv.visibility = View.VISIBLE
                    } else {
                        mViewBinding.recStateIv.visibility = View.INVISIBLE
                    }
                    mViewBinding.recTimeTv.text = calculateTime(mRecSeconds, mRecMinute)
                }
                WHAT_STOP_TIMER -> {
                    mViewBinding.recTimerLayout.visibility = View.GONE
                    mViewBinding.recTimeTv.text = calculateTime(0, 0)
                }
            }
            true
        }
    }

    private var mCameraMode = CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC

    private lateinit var mViewBinding: ViewCameraBinding
    private var deviceFilterXml:Int?=null
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

    override fun initView() {
        super.initView()
    }

    override fun initData() {
        super.initData()
        deviceFilterXml= R.xml.default_device_filter1
        setDefaultUsbDeviceList(deviceFilterXml)
        EventBus.with<Int>(BusKey.KEY_FRAME_RATE).observe(this) {
        }

        EventBus.with<Boolean>(BusKey.KEY_RENDER_READY).observe(this) { ready ->
            if (!ready) return@observe
            getDefaultEffect()?.apply {
                when (getClassifyId()) {
                    CameraEffect.CLASSIFY_ID_FILTER -> {
                        // check if need to set anim
                        val animId = MMKVUtils.getInt(KEY_ANIMATION, -99)
                        if (animId != -99) {
                            mEffectDataList.find {
                                it.id == animId
                            }?.also {
                                if (it.effect != null) {
                                    addRenderEffect(it.effect!!)
                                }
                            }
                        }
                        // set effect
                        val filterId = MMKVUtils.getInt(KEY_FILTER, -99)
                        if (filterId != -99) {
                            removeRenderEffect(this)
                            mEffectDataList.find {
                                it.id == filterId
                            }?.also {
                                if (it.effect != null) {
                                    addRenderEffect(it.effect!!)
                                }
                            }
                            return@apply
                        }
                        MMKVUtils.set(KEY_FILTER, getId())
                    }
                    CameraEffect.CLASSIFY_ID_ANIMATION -> {
                        // check if need to set filter
                        val filterId = MMKVUtils.getInt(KEY_ANIMATION, -99)
                        if (filterId != -99) {
                            mEffectDataList.find {
                                it.id == filterId
                            }?.also {
                                if (it.effect != null) {
                                    addRenderEffect(it.effect!!)
                                }
                            }
                        }
                        // set anim
                        val animId = MMKVUtils.getInt(KEY_ANIMATION, -99)
                        if (animId != -99) {
                            removeRenderEffect(this)
                            mEffectDataList.find {
                                it.id == animId
                            }?.also {
                                if (it.effect != null) {
                                    addRenderEffect(it.effect!!)
                                }
                            }
                            return@apply
                        }
                        MMKVUtils.set(KEY_ANIMATION, getId())
                    }
                    else -> throw IllegalStateException("Unsupported classify")
                }
            }
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
        return mViewBinding.cameraViewContainer
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = ViewCameraBinding.inflate(inflater, container, true)
        return mViewBinding.root
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
                mViewBinding.recTimerLayout.visibility = View.VISIBLE
                startMediaTimer()
            }

            override fun onError(error: String?) {
                isCapturingVideoOrAudio = false
                stopMediaTimer()
                callback.invoke(error ?: "未知异常", null)
            }

            override fun onComplete(path: String?) {
                isCapturingVideoOrAudio = false
                mViewBinding.recTimerLayout.visibility = View.GONE
                stopMediaTimer()
                callback.invoke("", path)
            }

        },path)
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
                mViewBinding.recTimerLayout.visibility = View.VISIBLE
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
                mViewBinding.recTimerLayout.visibility = View.GONE
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
        mMultiCameraDialog?.hide()
        isAlive = false
    }

    fun showDeviceDialog() {

        getCurrentCamera()?.let { strategy ->
            if (strategy is CameraUVC) {
                showUsbDevicesDialog(getDeviceList(), strategy.getUsbDevice())
                return
            }
        }
    }

    override fun onClick(v: View?) {

    }

    @SuppressLint("CheckResult")
    private fun showUsbDevicesDialog(
        usbDeviceList: MutableList<UsbDevice>?,
        curDevice: UsbDevice?
    ) {
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
        MaterialDialog(mContext).show {
            listItemsSingleChoice(
                items = list,
                initialSelection = selectedIndex
            ) { dialog, index, text ->
                if (selectedIndex == index) {
                    return@listItemsSingleChoice
                }
                switchCamera(usbDeviceList[index])
            }
        }
    }

    fun showEffectDialog() {
        EffectListDialog(mContext as Activity).apply {
            setData(mEffectDataList, object : EffectListDialog.OnEffectClickListener {
                override fun onEffectClick(effect: CameraEffect) {
                    mEffectDataList.find { it.id == effect.id }.also {
                        if (it == null) {
                            ToastUtils.show("set effect failed!")
                            return@also
                        }
                        updateRenderEffect(it.classifyId, it.effect)
                        // save to sp
                        if (effect.classifyId == CameraEffect.CLASSIFY_ID_ANIMATION) {
                            KEY_ANIMATION
                        } else {
                            KEY_FILTER
                        }.also { key ->
                            MMKVUtils.set(key, effect.id)
                        }
                    }
                }
            })
            show()
        }
    }

    @SuppressLint("CheckResult")
    fun showResolutionDialog() {
        if (!isCameraOpened()) {
            ToastUtils.show("camera not worked!")
            return
        }
        mMoreMenu?.dismiss()
        getAllPreviewSizes().let { previewSizes ->
            if (previewSizes.isNullOrEmpty()) {
                ToastUtils.show("Get camera preview size failed")
                return
            }
            val list = arrayListOf<String>()
            var selectedIndex: Int = -1
            for (index in (0 until previewSizes.size)) {
                val w = previewSizes[index].width
                val h = previewSizes[index].height
                getCurrentPreviewSize()?.apply {
                    if (width == w && height == h) {
                        selectedIndex = index
                    }
                }
                list.add("$w x $h")
            }
            MaterialDialog(mContext).show {
                listItemsSingleChoice(
                    items = list,
                    initialSelection = selectedIndex
                ) { dialog, index, text ->
                    if (selectedIndex == index) {
                        return@listItemsSingleChoice
                    }
                    updateResolution(previewSizes[index].width, previewSizes[index].height)
                }
            }
        }
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
}
