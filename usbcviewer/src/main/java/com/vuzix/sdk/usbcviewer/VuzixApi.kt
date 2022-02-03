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
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.vuzix.sdk.usbcviewer.utils.LogUtil
import java.lang.ref.WeakReference

abstract class VuzixApi(context: Context) {
    // Note: We store a WeakReference to the Context since it is possible that the Context owns
    // this API class.  If we were to hold a normal strong reference to the Context, the garbage
    // collector may never free us or the Context. Holding a WeakReference solves that problem.
    private val context: WeakReference<Context> = WeakReference(context)
    private var connectionListener: ConnectionListener? = null
    protected val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    protected var usbDevice: UsbDevice? = null

    /** Exposed property that can be used to determine if there is an active [UsbDeviceConnection] */
    var connected: Boolean = false
    protected set

    protected abstract fun getUsbVendorId() : Int

    protected abstract fun getUsbProductId() : Int

    abstract fun connect()

    abstract fun disconnect()

    private fun printableDevice(device: UsbDevice?) : String {
        return "Device ${device?.productName} (${device?.vendorId} ${device?.productId})"
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
        if( isDeviceAvailable() ) { // isDeviceAvailable() sets usbDevice
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

    private fun checkPermission(usbDevice: UsbDevice?) : Boolean{
        // The context will exist until the calling app is garbage collected, so it's OK
        // to assert here if it is null. Nobody should be calling this function by then
        var context = this.context.get()!!
        if(!context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            throw Exception("App does not have FEATURE_USB_HOST")
        }
        usbManager.hasPermission(usbDevice).let { granted ->
            if (!granted) {
                val usbPermissionIntent = PendingIntent.getBroadcast(context, 0, Intent(M400cConstants.ACTION_USB_PERMISSION), 0)
                val filter = IntentFilter(M400cConstants.ACTION_USB_PERMISSION)
                context.registerReceiver(mPermissionReceiver, filter)
                LogUtil.rel("Requesting permission to ${printableDevice(usbDevice)}")
                usbManager.requestPermission(usbDevice, usbPermissionIntent)
            }
            return granted;
        }
    }

    private val mPermissionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (M400cConstants.ACTION_USB_PERMISSION == intent.action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                LogUtil.rel("permission granted=$granted for ${printableDevice(device)}")
                connectionListener?.onPermissionsChanged(granted)
            }
            context.unregisterReceiver(this)
        }
    }

    private val mConnectDisconnectReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            LogUtil.debug("Got ${intent.action} for $device")
            if( device?.productId == getUsbProductId() && device?.vendorId == getUsbVendorId() ) {
                val connected = (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action)
                LogUtil.debug(" ${device?.productId} ${device?.vendorId} connected = $connected")
                connectionListener?.onConnectionChanged(connected)
                if(connected) {
                    usbDevice = device;
                    checkPermission(device)
                }
            }
        }
    }


    protected fun getDevice(): UsbDevice? {
        val devices = usbManager.deviceList
        val vendorId = getUsbVendorId()
        val productId = getUsbProductId()
        val usbDevice = devices.values.firstOrNull { device -> device.productId == productId && device.vendorId == vendorId}
        if(usbDevice == null) {
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

    /**
     * Function used to let you know if the video [UsbDevice] is available
     *
     * @return True if available
     */
    open fun isDeviceAvailable(): Boolean {
        usbDevice = getDevice()
        val available = (usbDevice != null)
        LogUtil.debug("Available = $available")
        return available
    }

    /**
     * Function used to let you know if the video [UsbDevice] is available
     * with all permissions granted
     *
     * This is identical to calling registerDeviceMonitor() but does not
     * listen for the state to change.
     *
     * @see registerDeviceMonitor
     *
     * @return True if available and permissions are granted
     */
    open fun isDeviceAvailableAndAllowed(): Boolean {
        if( isDeviceAvailable() ) { // isDeviceAvailable() sets usbDevice
            val granted = usbManager.hasPermission(usbDevice)
            LogUtil.debug("granted = $granted")
            return granted
        }
        return false;
    }

}