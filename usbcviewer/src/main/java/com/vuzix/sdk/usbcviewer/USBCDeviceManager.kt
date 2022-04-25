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


import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.vuzix.sdk.usbcviewer.utils.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference


class USBCDeviceManager private constructor(context: Context) {

    companion object : SingletonHolder<USBCDeviceManager, Context>(::USBCDeviceManager)

    private var context: WeakReference<Context> = WeakReference(context)

    private var connectionListener: ConnectionListener? = null

    var usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var _mainControlUsbDevice: UsbDevice? = null
    val mainControlUsbDevice: UsbDevice?
        get() {
            if (_mainControlUsbDevice != null) {
                return _mainControlUsbDevice
            }
            if (checkPermissions()) {
                _mainControlUsbDevice = getDevice(M400cConstants.VIEWER_VID, M400cConstants.VIEWER_PID)
                return _mainControlUsbDevice
            }
            return null
        }

    private var _touchPadUsbDevice: UsbDevice? = null
    val touchPadUsbDevice: UsbDevice?
        get() {
            if (_touchPadUsbDevice != null) {
                return _touchPadUsbDevice
            }
            if (checkPermissions()) {
                _touchPadUsbDevice = getDevice(M400cConstants.HID_TOUCHPAD_VID, M400cConstants.HID_TOUCHPAD_PID)
                return _touchPadUsbDevice
            }
            return null
        }

    private var _cameraUsbDevice: UsbDevice? = null
    val cameraUsbDevice: UsbDevice?
        get() {
            if (_cameraUsbDevice != null) {
                return _cameraUsbDevice
            }
            if (checkPermissions()) {
                _cameraUsbDevice = getDevice(M400cConstants.CAMERA_VID, M400cConstants.CAMERA_PID)
                return _cameraUsbDevice
            }
            return null
        }

    /*
    The Camera Interface to interact with
     */
    private var _cameraInterface: CameraInterface? = null
    val cameraInterface: CameraInterface?
        get() {
            if (usbManager == null) {
                return null
            }
            if (_cameraInterface != null) {
                return _cameraInterface
            }
            cameraUsbDevice?.let { device ->
                val usbInterface = device.getInterface(M400cConstants.CAMERA_HID)
                _cameraInterface = CameraInterface(usbManager!!, device, usbInterface)
                return _cameraInterface
            }
            return null
        }

    /*
    The Main Device Control Interface to interact with
     */
    private var _deviceControlInterface: DeviceControlInterface? = null
    val deviceControlInterface: DeviceControlInterface?
        get() {
            if (usbManager == null) {
                return null
            }
            if (_deviceControlInterface != null) {
                return _deviceControlInterface
            }
            mainControlUsbDevice?.let { device ->
                val usbInterface = device.getInterface(M400cConstants.HID_VIEWER_CONTROL_INTERFACE)
                _deviceControlInterface = DeviceControlInterface(usbManager!!, device, usbInterface)
                return _deviceControlInterface
            }
            return null
        }

    /*
    The Sensor Interface to interact with
     */
    private var _sensorInterface: SensorInterface? = null
    val sensorInterface: SensorInterface?
        get() {
            if (usbManager == null) {
                return null
            }
            if (_sensorInterface != null) {
                return _sensorInterface
            }
            mainControlUsbDevice?.let { device ->
                val usbInterface = device.getInterface(M400cConstants.HID_SENSOR_INTERFACE)
                _sensorInterface = SensorInterface(usbManager!!, device, usbInterface)
                return _sensorInterface
            }
            return null
        }

    /*
    The Touchpad Interface to interact with
     */
    private var _touchPadInterface: TouchPadInterface? = null
    val touchPadInterface: TouchPadInterface?
        get() {
            if (usbManager == null) {
                return null
            }
            if (_touchPadInterface != null) {
                return _touchPadInterface
            }
            touchPadUsbDevice?.let { device ->
                val usbInterface = device.getInterface(M400cConstants.HID_TOUCHPAD_INTERFACE)
                _touchPadInterface = TouchPadInterface(usbManager!!, device, usbInterface)
                return _touchPadInterface
            }
            return null
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
     * @return True if USB device is available and permissions are granted
     */
    open fun registerDeviceMonitor( connectionListener: ConnectionListener ) : Boolean {
        this.connectionListener = connectionListener

        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        context.get()?.registerReceiver(mConnectDisconnectReceiver, filter)
        val usbDevice = mainControlUsbDevice
        if( usbDevice != null && isDeviceAvailable() ) {
            val granted = checkPermission(usbDevice)
            LogUtil.debug("Registered attach/detach listener while attached, granted=$granted")
            return granted
        }
        LogUtil.debug("Registered attach/detach listener while not attached")
        return false
    }

    /**
     * Stops monitoring for the device state to change
     *
     * @see registerDeviceMonitor
     */
    open fun unregisterDeviceMonitor() {
        if(connectionListener != null) {
            LogUtil.debug("Unregistering attach/detach listener")
            context.get()?.unregisterReceiver(mConnectDisconnectReceiver)
            connectionListener = null
        }
    }

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
            context.unregisterReceiver(this)
        }
    }

    private val mConnectDisconnectReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            LogUtil.debug("Got ${intent.action} for $device")
            if ((device?.productId == M400cConstants.VIEWER_PID && device?.vendorId == M400cConstants.VIEWER_VID) ||
                (device?.productId == M400cConstants.CAMERA_PID && device?.vendorId == M400cConstants.CAMERA_VID)) {
                val connected = (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action)
                LogUtil.debug(" ${device?.productId} ${device?.vendorId} connected = $connected")
                if(connected) {
                    checkPermission(device)
                }
                else {
                    reset()
                }
                GlobalScope.launch (Dispatchers.Main) {
                    connectionListener?.onConnectionChanged(connected)
                }

            }
        }
    }

    private fun reset() {
        _cameraInterface = null
        _sensorInterface = null
        _deviceControlInterface = null

        _cameraUsbDevice = null
        _mainControlUsbDevice = null
    }

    /**
     * Function used to let you know if the video [UsbDevice] is available
     *
     * @return True if available
     */
    open fun isDeviceAvailable(): Boolean {
        val available = mainControlUsbDevice?.let { isDeviceConnected(it) }
        LogUtil.debug("Available = $available")
        return available == true
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


    open fun hasPermissionsBeenGranted(): Boolean {
        if (mainControlUsbDevice == null) return false
        return usbManager.hasPermission(mainControlUsbDevice)
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

    open fun checkPermissions(): Boolean {
        val devices = usbManager.deviceList.values.toList()

        var result = true
        for (i in devices.indices) {
            result = result && checkPermission(devices[i])
        }
        return result
    }


    private fun checkPermission(usbDevice: UsbDevice?) : Boolean {
        // The context will exist until the calling app is garbage collected, so it's OK
        // to assert here if it is null. Nobody should be calling this function by then
        var context = this.context.get() ?: return false

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
                    LogUtil.rel("Requesting permission to ${printableDevice(it)}")
                    usbManager.requestPermission(it, usbPendingPerm)
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

    fun shared(arg: A): T {
        val checkInstance = instance
        if (checkInstance != null) {
            return checkInstance
        }

        return synchronized(this) {
            val checkInstanceAgain = instance
            if (checkInstanceAgain != null) {
                checkInstanceAgain
            } else {
                val created = creator!!(arg)
                instance = created
                creator = null
                created
            }
        }
    }

    fun shared(): T? {
        val checkInstance = instance
        if (checkInstance != null) {
            return checkInstance
        }
        return null
    }
}

