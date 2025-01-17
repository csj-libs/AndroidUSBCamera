package com.jiangdg.ausbc.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.media.Image
import android.os.Build
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.jiangdg.ausbc.R
import com.jiangdg.usb.DeviceFilter

/** Camera tools
 *
 * @author Created by jiangdg on 2022/7/19
 */
object CameraUtils {

    fun transferYUV420ToNV21(image: Image, width: Int, height: Int): ByteArray {
        val nv21 = ByteArray(width * height * 3 / 2)
        val planes = image.planes
        // Y通道
        val yBuffer = planes[0].buffer
        val yLen = width * height
        yBuffer.get(nv21, 0, yLen)
        // V通道
        val vBuffer = planes[2].buffer
        val vPixelStride = planes[2].pixelStride
        for ((index, i) in (0 until vBuffer.remaining() step vPixelStride).withIndex()) {
            val vIndex = yLen + 2 * index
            if (vIndex >= nv21.size) {
                break
            }
            nv21[vIndex] = vBuffer.get(i)
        }
        // U通道
        val uBuffer = planes[1].buffer
        val uPixelStride = planes[1].pixelStride
        for ((index, i) in (0 until uBuffer.remaining() step uPixelStride).withIndex()) {
            val uIndex = yLen + (2 * index + 1)
            if (uIndex >= nv21.size) {
                break
            }
            nv21[yLen + (2 * index + 1)] = uBuffer.get(i)
        }
        return nv21
    }

    /**
     * check is usb camera
     *
     * @param device see [UsbDevice]
     * @return true usb camera
     */
    fun isUsbCamera(device: UsbDevice?): Boolean {
        return when (device?.deviceClass) {
            UsbConstants.USB_CLASS_VIDEO -> {
                true
            }
            UsbConstants.USB_CLASS_MISC -> {
                var isVideo = false
                for (i in 0 until device.interfaceCount) {
                    val cls = device.getInterface(i).interfaceClass
                    if (cls == UsbConstants.USB_CLASS_VIDEO) {
                        isVideo = true
                        break
                    }
                }
                isVideo
            }
            else -> {
                false
            }
        }
    }

    /**
     * Is camera contains mic
     *
     * @param device usb device
     * @return true contains
     */
    fun isCameraContainsMic(device: UsbDevice?): Boolean {
        device ?: return false
        var hasMic = false
        for (i in 0 until device.interfaceCount) {
            val cls = device.getInterface(i).interfaceClass
            if (cls == UsbConstants.USB_CLASS_AUDIO) {
                hasMic = true
                break
            }
        }
        return hasMic
    }
    fun getDeviceFiltersFromXml(  context:Context,   deviceFilterXmlId:Int): List<DeviceFilter> {
        return DeviceFilter.getDeviceFilters(context, deviceFilterXmlId)
    }
    /**
     * Filter needed usb device by according to filter regular
     *
     * @param context context
     * @param usbDevice see [UsbDevice]
     * @return true find success
     */
    
    fun isFilterDevice(context: Context?, usbDevice: UsbDevice?): Boolean {
        return isFilterDevice(DeviceFilter.getDeviceFilters(context, R.xml.default_device_filter),usbDevice)
    }
    
    fun isFilterDevice(filterList: List<DeviceFilter>?, usbDevice: UsbDevice?): Boolean {
        return filterList
            ?.find { devFilter ->
                var flag=devFilter.mProductId>=0  && devFilter.mVendorId>0&&devFilter.mProductId == usbDevice?.productId && devFilter.mVendorId == usbDevice.vendorId
                flag=flag or (devFilter.mClass>=0 && devFilter.mClass==usbDevice?.deviceClass && devFilter.mSubclass==usbDevice.deviceSubclass)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    flag=flag or (!TextUtils.isEmpty(devFilter.mProductName) && (devFilter.mProductName?.equals(usbDevice?.productName?:"")==true))
                }
                flag
            }.let { dev ->
                dev != null
            }
    }
    fun hasAudioPermission(ctx: Context): Boolean{
        val locPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
        return locPermission == PackageManager.PERMISSION_GRANTED
    }

    fun hasStoragePermission(ctx: Context): Boolean{
        val locPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return locPermission == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(ctx: Context): Boolean{
        val locPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
        return locPermission == PackageManager.PERMISSION_GRANTED
    }
}