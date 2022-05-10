/*
 * Copyright 2022, Vuzix Corporation
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *   Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *   Neither the name of Vuzix Corporation nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.vuzix.sdk.usbcviewer


import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vuzix.sdk.usbcviewer.utils.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Timer
import kotlin.concurrent.schedule


class USBCDeviceManager private constructor(_context: Context) {

    companion object : SingletonHolder<USBCDeviceManager, Context>(::USBCDeviceManager) {
        /**
         * Obtain or create the shared singleton instance
         *
         * @param arg Context of the caller
         */
        @JvmStatic fun shared(arg: Context): USBCDeviceManager {
            return _shared(arg)
        }
        /**
         * Obtain the previously created singleton instance
         *
         * Note: the Singleton must have been previously created with a valid Context
         */
        @JvmStatic fun shared(): USBCDeviceManager? {
            return _shared()
        }
    }

    private var context: WeakReference<Context> = WeakReference(_context)
    private var connectionListener: ConnectionListener? = null
    var usbManager: UsbManager = _context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var _mainControlUsbDevice: UsbDevice? = null

    val mainControlUsbDevice: UsbDevice?
        get() {
            if (!isDeviceAvailable()) {
                LogUtil.debug("Main controller device not currently available")
                return null
            }
            if (_mainControlUsbDevice != null) {
                return _mainControlUsbDevice
            }
            val device = getDevice(M400cConstants.VIEWER_VID, M400cConstants.VIEWER_PID)
            if (checkPermission(device)) {
                _mainControlUsbDevice = device
                return _mainControlUsbDevice
            }
            LogUtil.debug("get() mainControlUsbDevice no main device permission. return null")
            return null
        }

    private var _touchPadUsbDevice: UsbDevice? = null
    val touchPadUsbDevice: UsbDevice?
        get() {
            if (!isDeviceAvailable()) {
                return null
            }
            if (_touchPadUsbDevice != null) {
                return _touchPadUsbDevice
            }
            val device = getDevice(M400cConstants.HID_TOUCHPAD_VID, M400cConstants.HID_TOUCHPAD_PID)
            if (checkPermission(device)) {
                _touchPadUsbDevice = device
                return _touchPadUsbDevice
            }
            LogUtil.debug("get() touchPadUsbDevice no touchpad device permission. return null")
            return null
        }

    private var _cameraUsbDevice: UsbDevice? = null
    val cameraUsbDevice: UsbDevice?
        get() {
            if (!isDeviceAvailable()) {
                return null
            }
            if (_cameraUsbDevice != null) {
                return _cameraUsbDevice
            }
            val device = getDevice(M400cConstants.CAMERA_VID, M400cConstants.CAMERA_PID)
            if (checkPermission(device)) {
                _cameraUsbDevice = device
                return _cameraUsbDevice
            }
            LogUtil.debug("get() cameraUsbDevice no camera device permission. return null")
            return null
        }

    /*
    The Camera Interface to interact with
     */
    private var _cameraInterface: CameraInterface? = null
    val cameraInterface: CameraInterface?
        get() {
            if (!isDeviceAvailable()) {
                return null
            }
            if (_cameraInterface != null) {
                return _cameraInterface
            }
            cameraUsbDevice?.let { device ->
                if (checkPermission(device)) {
                    val usbInterface = device.getInterface(M400cConstants.CAMERA_HID)
                    _cameraInterface = CameraInterface(usbManager, device, usbInterface)
                    return _cameraInterface
                }
            }
            LogUtil.debug("get() cameraInterface no camera device permission. return null")
            return null
        }

    /*
    The Main Device Control Interface to interact with
     */
    private var _deviceControlInterface: DeviceControlInterface? = null
    val deviceControlInterface: DeviceControlInterface?
        get() {
            if (!isDeviceAvailable()) {
                return null
            }
            if (_deviceControlInterface != null) {
                return _deviceControlInterface
            }
            mainControlUsbDevice?.let { device ->
                if (checkPermission(device)) {
                    val usbInterface = device.getInterface(M400cConstants.HID_VIEWER_CONTROL_INTERFACE)
                    _deviceControlInterface = DeviceControlInterface(usbManager, device, usbInterface)
                    return _deviceControlInterface
                }
            }
            LogUtil.debug("get() deviceControlInterface device null. return null")
            return null
        }

    /*
    The Sensor Interface to interact with
     */
    private var _sensorInterface: SensorInterface? = null
    val sensorInterface: SensorInterface?
        get() {
            if (!isDeviceAvailable()) {
                return null
            }
            if (_sensorInterface != null) {
                return _sensorInterface
            }
            mainControlUsbDevice?.let { device ->
                if (checkPermission(device)) {
                    val usbInterface = device.getInterface(M400cConstants.HID_SENSOR_INTERFACE)
                    _sensorInterface = SensorInterface(usbManager, device, usbInterface)
                    return _sensorInterface
                }
            }
            LogUtil.debug("get() sensorInterface device null. return null")
            return null
        }

    /*
    The Touchpad Interface to interact with
     */
    private var _touchPadInterface: TouchPadInterface? = null
    val touchPadInterface: TouchPadInterface?
        get() {
            if (!isDeviceAvailable()) {
                return null
            }
            if (_touchPadInterface != null) {
                return _touchPadInterface
            }
            touchPadUsbDevice?.let { device ->
                if (checkPermission(device)) {
                    val usbInterface = device.getInterface(M400cConstants.HID_TOUCHPAD_INTERFACE)
                    _touchPadInterface = TouchPadInterface(usbManager, device, usbInterface)
                    return _touchPadInterface
                }
            }
            LogUtil.debug("get() touchPadInterface device null. return null")
            return null
        }

    private var lastNotifiedState = false

    private fun notifyListener(device: UsbDevice?, newAttachedState: Boolean) {
        if(lastNotifiedState != newAttachedState) {
            lastNotifiedState = newAttachedState
            if (newAttachedState) {
                checkPermission(device)
            } else {
                reset()
            }
            checkPermission(device)
            GlobalScope.launch(Dispatchers.Main) {
                connectionListener?.onConnectionChanged(newAttachedState)
            }
        }
    }

    private fun startRecheckThread() {
        // Prevent a race condition where it has just become available and we miss the broadcast
        Timer("DelayedRecheck", false).schedule(10000) {
            LogUtil.debug("Timed re-check")
            if (mainControlUsbDevice != null) {
                LogUtil.debug("Noticed already attached")
                notifyListener(_mainControlUsbDevice, true)
            } else {
                LogUtil.debug("Not currently attached")
            }
        }
    }

    /**
     * Starts monitoring for the device state to change
     *
     * Note: After calling this method you must later call unregisterDeviceMonitor
     *
     * @see unregisterDeviceMonitor
     *
     * @param ConnectionListener - callback for alerting of changes
     *
     */
    @Synchronized fun registerDeviceMonitor( connectionListener: ConnectionListener ) {
        if (connectionListener == null) {
            throw Exception("Listener cannot be null")
        }
        val context = this.context.get() ?: throw Exception("Must provide context first")
        this.connectionListener = connectionListener

        if(!connectDisconnectReceiverRegistered) {
            val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            context.registerReceiver(mConnectDisconnectReceiver, filter)
            connectDisconnectReceiverRegistered = true
            LogUtil.debug("Registered attach/detach listener $mConnectDisconnectReceiver by $context")
        }
        startRecheckThread()
    }

    /**
     * Stops monitoring for the device state to change
     *
     * @see registerDeviceMonitor
     */
    @Synchronized fun unregisterDeviceMonitor() {
        val context = this.context.get() ?: throw Exception("Must provide context first")
        LogUtil.debug("unregisterDeviceMonitor")
        if(connectDisconnectReceiverRegistered) {
            connectDisconnectReceiverRegistered = false
            LogUtil.debug("Unregistering attach/detach listener $mConnectDisconnectReceiver by $context")
            try {
                context.unregisterReceiver(mConnectDisconnectReceiver)
            } catch (e : Exception) {
                LogUtil.debug("Failed to unregister")
            }
        }
        if(mPermissionListenerRegistered) {
            mPermissionListenerRegistered = false
            LogUtil.debug("Unregistering permissions listener")
            try {
                context.unregisterReceiver(mPermissionReceiver)
            } catch (e: Exception) {
                LogUtil.debug("Failed to unregister")
            }
        }
        connectionListener = null
    }

    @Volatile private var mPermissionListenerRegistered = false
    private val mPermissionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (M400cConstants.ACTION_USB_PERMISSION == intent.action) {
                var granted = false
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                if( (device != null) && intent.hasExtra(UsbManager.EXTRA_PERMISSION_GRANTED)) {
                    granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                } else {
                    LogUtil.debug("Intent came back without device and granted extras!")
                    granted = usbManager?.hasPermission(mainControlUsbDevice) == true
                }
                LogUtil.rel("permission granted=$granted for ${printableDevice(device)}")
                GlobalScope.launch(Dispatchers.Main) {
                    connectionListener?.onPermissionsChanged(granted)
                }
            }
        }
    }

    @Volatile private var connectDisconnectReceiverRegistered = false
    private val mConnectDisconnectReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            LogUtil.debug("Got ${intent.action} for $device")
            if ((device?.productId == M400cConstants.VIEWER_PID && device?.vendorId == M400cConstants.VIEWER_VID) ||
                (device?.productId == M400cConstants.CAMERA_PID && device?.vendorId == M400cConstants.CAMERA_VID) ||
                (device?.productId == M400cConstants.HID_TOUCHPAD_PID && device?.vendorId == M400cConstants.HID_TOUCHPAD_VID)) {
                val connected = (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action)
                LogUtil.debug(" ${device?.productId} ${device?.vendorId} connected = $connected")
                notifyListener(_mainControlUsbDevice, connected)
            }
        }
    }

    private fun reset() {
        _cameraInterface = null
        _sensorInterface = null
        _deviceControlInterface = null
        _touchPadInterface = null

        _cameraUsbDevice = null
        _mainControlUsbDevice = null
        _touchPadUsbDevice = null
    }

    /**
     * Function used to let you know if the video [UsbDevice] is available
     *
     * @return True if available
     */
    open fun isDeviceAvailable(): Boolean {
        val connectedDevices = usbManager.deviceList
        for (d in connectedDevices.values) {
            if (d.productId == M400cConstants.VIEWER_PID && d.vendorId == M400cConstants.VIEWER_VID) {
                return true
            }
        }
        reset()
        return false
    }

    private fun isDeviceConnected(device: UsbDevice): Boolean {
        val connectedDevices = usbManager.deviceList
        if (connectedDevices.isEmpty()) {
            return false
        }
        for (d in connectedDevices.values) {
            if (d == device) {
                return true
            }
        }
        return false
    }


    private fun getDevice(vendorId: Int, productId: Int): UsbDevice? {
        if (usbManager == null) return null
        val devices = usbManager.deviceList
        val usbDevice =
            devices.values.firstOrNull { device -> device.productId == productId && device.vendorId == vendorId }
        if (usbDevice == null) {
            // help the developer and tech support by printing connection info
            if (devices.size == 0) {
                LogUtil.rel("No USB devices attached")
            } else {
                LogUtil.rel("Did not find $vendorId $productId")
                for ((key, value) in devices) {
                    LogUtil.rel("Non-matching USB: $key ${printableDevice(value)}")
                }
            }
        }
        return usbDevice
    }


    private fun checkPermission(usbDevice: UsbDevice?) : Boolean {
        LogUtil.debug("Checking permissions ${printableDevice(usbDevice)}")
        val context = this.context.get() ?: return false

        // if camera, they need user permissions first
        if (usbDevice?.vendorId == M400cConstants.CAMERA_VID && usbDevice?.productId == M400cConstants.CAMERA_PID) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (context is Activity) {
                ActivityCompat.requestPermissions(context,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                    0
                )}
                LogUtil.debug("No camera permission ${printableDevice(usbDevice)}")
                return false
            }
        }

        if(!context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            throw Exception("App does not have FEATURE_USB_HOST")
        }

        usbDevice?.let {
            usbManager.hasPermission(it).let { granted ->
                if (!granted) {
                    val permIntent = Intent(M400cConstants.ACTION_USB_PERMISSION)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    val usbPendingPerm = PendingIntent.getBroadcast(context, 0, permIntent, flags)
                    val filter = IntentFilter(permIntent.action)
                    context.registerReceiver(mPermissionReceiver, filter)
                    mPermissionListenerRegistered = true
                    LogUtil.rel("Requesting permission to ${printableDevice(it)}")
                    usbManager.requestPermission(it, usbPendingPerm)
                } else {
                    LogUtil.debug("already granted to ${printableDevice(it)}")
                }
                return granted;
            }
        }
        return false
    }

    internal fun disconnect(usbInterface: USBCDeviceInterface){
        when (usbInterface) {
            _deviceControlInterface -> _deviceControlInterface = null
            _cameraInterface -> _cameraInterface = null
            _sensorInterface -> _sensorInterface = null
        }
    }

    private fun printableDevice(device: UsbDevice?): String {
        return "Device ${device?.productName} (${device?.vendorId} ${device?.productId})"
    }

    /*
    Device-wide set Auto Rotation for the entire device, both Display and Camera
     */
    open fun setAutoRotationForDevice(rotation: Rotation) {
        if (rotation == Rotation.AUTO_ROTATE) {
            deviceControlInterface?.setAutoRotation(true)
            cameraInterface?.setAutoRotation(true)
        }
        else {
            deviceControlInterface?.setAutoRotation(false)
            cameraInterface?.setAutoRotation(false)
            deviceControlInterface?.setForceLeftEye(rotation == Rotation.LEFT_EYE)
            cameraInterface?.setForceLeftEye(rotation == Rotation.LEFT_EYE)
        }
    }
}


open class SingletonHolder<out T: Any, in A>(creator: (A) -> T) {
    private var creator: ((A) -> T)? = creator
    @Volatile private var instance: T? = null

    internal fun _shared(arg: A): T {
        val checkInstance = instance
        if (checkInstance != null) {
            return checkInstance
        }

        return synchronized(this) {
            val checkInstanceAgain = instance
            if (checkInstanceAgain != null) {
                checkInstanceAgain
            } else {
                LogUtil.debug("Creating singleton")
                val created = creator!!(arg)
                instance = created
                creator = null
                created
            }
        }
    }

    internal fun _shared(): T? {
        val checkInstance = instance
        if (checkInstance != null) {
            return checkInstance
        }
        return null
    }
}
