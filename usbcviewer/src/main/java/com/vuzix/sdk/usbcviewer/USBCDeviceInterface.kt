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

import android.hardware.usb.*
import com.vuzix.sdk.usbcviewer.utils.LogUtil
import java.util.concurrent.TimeUnit

open class USBCDeviceInterface(private val usbManager: UsbManager, val device: UsbDevice, private val usbInterface: UsbInterface) {

    private val USB_REQUEST_TYPE_RECIPIENT_INTERFACE = 1

    var connection: UsbDeviceConnection = usbManager.openDevice(device)
    var connected: Boolean = false

    private val outEndpoint: UsbEndpoint? = getEndpointForDirection(UsbConstants.USB_DIR_OUT)
    protected val inEndpoint: UsbEndpoint? = getEndpointForDirection(UsbConstants.USB_DIR_IN)

    init {
        if (!connection.claimInterface(usbInterface, true)) {
            throw Exception("Failed to claim Interface")
        }
        connected = connection.setInterface(usbInterface)
    }

    // Returns the fist interface endpoint in the requested direction
    fun getEndpointForDirection(direction: Int): UsbEndpoint? {
        for (index in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(index)
            val attributes = endpoint.attributes
            LogUtil.debug("attributes: $attributes")
            val type = endpoint.type
            LogUtil.debug("type: $type")
            if (endpoint.direction == direction) {
                return endpoint
            }
        }
        return null
    }

    // Releases the interfaces and closes the connection.
    fun disconnect() {
        LogUtil.debug("disconnect")
        try {
            connection.releaseInterface(usbInterface)
            connection.close()
        } catch (e: Exception) {
            // Eat it
        }
        connected = false
        USBCDeviceManager.shared()?.disconnect(this)
    }

    fun get(command: Int): ByteArray {
        return get(command, null)
    }

    // Simple get property command, it will determine how to send
    fun get(command: Int, value:ByteArray?): ByteArray {
        if (inEndpoint != null && outEndpoint != null) {
            return sendPayload(command)
        }
        else if (value != null) {
            return getHidProperty(command, value)
        }

        return ByteArray(0)
    }

    fun set(command: Int) {
        set(command, null)
    }

    // Simple set property command, it will determine how to send
    fun set(command: Int, value:ByteArray?): ByteArray {
        if (inEndpoint != null && outEndpoint != null) {
            return sendPayload(command, value)
        }
        else if (value != null) {
            setHidProperty(command, value)
        }

        return ByteArray(0)
    }

    // returns the HID GET_REPORT for the requested property
    fun getHidProperty(command: Int, value:ByteArray): ByteArray {
        setHidProperty(command, value)
        // read the values back
        val requestType =
            (UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_CLASS or USB_REQUEST_TYPE_RECIPIENT_INTERFACE).toInt()

        if (inEndpoint == null) {
            return ByteArray(0)
        }

        var buffer = ByteArray(64)

        connection.controlTransfer(
            requestType,
            UsbHIDRequestType.GET_REPORT.value,
             0x0200, // descriptor type as the high byte and the index of the interface as the low byte
            usbInterface.id,
            buffer,
            buffer.size,
            TimeUnit.SECONDS.toMillis(1).toInt()
        )

        return buffer
    }

    // Sets the HID
    fun setHidProperty(command:Int, value:ByteArray) {
        var payload = byteArrayOf((value.size + 2).toByte(), command.toByte())
        payload = payload.plus(value)
        setHidReport(0x0200, payload)
    }

    // Gets the HID GET_REPORT
    fun getHidReport(wValue: Int): ByteArray? {
        val requestType =
            (UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_CLASS or USB_REQUEST_TYPE_RECIPIENT_INTERFACE).toInt()


        var buffer = if (inEndpoint != null) { ByteArray(inEndpoint.maxPacketSize) } else {
            ByteArray(64)
        }

        val status = connection.controlTransfer(
            requestType,
            UsbHIDRequestType.GET_REPORT.value,
            wValue,
            usbInterface.id,
            buffer,
            buffer.size,
            TimeUnit.SECONDS.toMillis(1).toInt()
        )
        if (status > 0) {
            return buffer
        }
        return null
    }

    // Sets the HID SET_REPORT value
    fun setHidReport(wValue: Int, payload: ByteArray): Int {
        val requestType =
            (UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or USB_REQUEST_TYPE_RECIPIENT_INTERFACE).toInt()

        return connection.controlTransfer(
            requestType,
            UsbHIDRequestType.SET_REPORT.value,
            wValue,
            usbInterface.id,
            payload,
            payload.size,
            TimeUnit.SECONDS.toMillis(1).toInt()
        )
    }

    // returns the HID GET_DESCRIPTOR
    fun getStandardDescriptor(): ByteArray {
        val requestType =
            (UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_STANDARD or USB_REQUEST_TYPE_RECIPIENT_INTERFACE).toInt()

        var buffer = ByteArray(64)

        connection.controlTransfer(
            requestType,
            USBStandardDeviceRequestType.GET_DESCRIPTOR.value,
            0x0200, // descriptor type as the high byte and the index of the interface as the low byte
            usbInterface.id,
            buffer,
            buffer.size,
            TimeUnit.SECONDS.toMillis(1).toInt()
        )

        return buffer
    }


    private fun sendPayload(command: Int): ByteArray {
        return sendPayload(command, null)
    }

    // Sends data over the outgoing end point and reads back over the incoming end point.
    fun sendPayload(command: Int, payload: ByteArray?): ByteArray {
        if (!connected) {
            throw Exception("Device is not connected")
        }

        val inEndPoint = inEndpoint ?: throw Exception("IN Endpoint missing in sendPayload")
        val outEndPoint = outEndpoint?: throw Exception("OUT Endpoint missing in sendPayload")

        val incomingBytes = ByteArray(inEndPoint.maxPacketSize)
        var dataToWrite = byteArrayOf(2, command.toByte())
        payload?.let {
            dataToWrite = dataToWrite.plus(it)
        }

        val write = connection.bulkTransfer(outEndPoint, dataToWrite, dataToWrite.size, TimeUnit.SECONDS.toMillis(1).toInt() )

        if (write > 0) {
            val read = connection.bulkTransfer(
                inEndPoint,
                incomingBytes,
                incomingBytes.size,
                TimeUnit.SECONDS.toMillis(1).toInt()
            )
//            if (read <= 0) {
//                throw Exception("Failed to read payload results")
//            }
        }
        else {
            throw Exception("Failed to write payload")
        }
        return incomingBytes
    }

    // Returns the HID GET_DESCRIPTOR
    fun getHIDReportDescriptor(): Boolean {
        val incomingBytes = ByteArray(4 * 1024) // Plenty of space
        val requestType = UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_STANDARD or USB_REQUEST_TYPE_RECIPIENT_INTERFACE
        val request = USBStandardDeviceRequestType.GET_DESCRIPTOR.value
        val requestValue = (((0x22U) shl (8))).toInt()
        val requestIndex = usbInterface.id
        val timeoutMs = TimeUnit.SECONDS.toMillis(1).toInt()

        val bytesRead = connection.controlTransfer(
            requestType,
            request,
            requestValue,
            requestIndex,
            incomingBytes,
            incomingBytes.size,
            timeoutMs
        )
        if (bytesRead == incomingBytes.size) {
            LogUtil.rel("WARNING: HID report for sensor: truncated at $bytesRead")
        }
        if (bytesRead > 0) {
            // Decode the full output this with
            LogUtil.debug(
                "HID report for request: 0x${requestType.toString(16)} ${request.toString(16)}" +
                        " ${requestValue.toString(16)} ${requestIndex.toString(16)} returned size $bytesRead" +
                        " of ${incomingBytes.size} : ", incomingBytes, bytesRead
            )
            LogUtil.debug("paste the bytes here: https://eleccelerator.com/usbdescreqparser/")
            return true
        } else {
            LogUtil.rel(
                "HID report failed $bytesRead. Request: 0x${requestType.toString(16)} " +
                        request.toString(16) +
                        " ${requestValue.toString(16)} " +
                        requestIndex.toString(16)
            )
        }
        return false
    }
}

enum class UsbHIDRequestType(val value: Int) {
    GET_REPORT(0x01),
    GET_IDLE(0x02),
    GET_PROTOCOL(0x03),
    SET_REPORT (0x09),
    SET_IDLE(0x0A),
    SET_PROTOCOL(0x0B)
}

enum class USBStandardDeviceRequestType(val value: Int) {
    GET_STATUS (0x00),
    CLEAR_FEATURE (0x01),
    SET_FEATURE (0x03),
    SET_ADDRESS (0x05),
    GET_DESCRIPTOR (0x06),
    SET_DESCRIPTOR (0x07),
    GET_CONFIGURATION (0x08),
    SET_CONFIGURATION (0x09)
}