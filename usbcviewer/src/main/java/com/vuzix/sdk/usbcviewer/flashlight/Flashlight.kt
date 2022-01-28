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

package com.vuzix.sdk.usbcviewer.flashlight

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.vuzix.sdk.usbcviewer.M400cConstants
import com.vuzix.sdk.usbcviewer.VuzixApi
import com.vuzix.sdk.usbcviewer.utils.LogUtil
import java.util.concurrent.TimeUnit

/**
 * This class allows you to access the Flashlight/Torch in order to turn
 * it on or off.
 *
 * @param context App context, necessary to initialize an instance of
 * [UsbManager].
 */
class Flashlight(context: Context): VuzixApi(context) {
    private lateinit var connection: UsbDeviceConnection
    private lateinit var flashlightInterface: UsbInterface
    // IDs for Flashlight Commands
    private val FLASHLIGHT_ON = 9
    private val FLASHLIGHT_OFF = 10

    /**
     * Function used to create the [UsbDeviceConnection]
     * needed in order to send the commands for turning the Flashlight/Torch
     * on or off.
     *
     * @throws Exception if, when attempting to get the video [UsbDevice]
     * the device returns as null, or if when attempting to [claimInterface()] the
     * result is false.
     */
    @Throws(Exception::class)
    override fun connect() {
        LogUtil.debug("connect")
        usbDevice = getVideoDevice(usbManager)
        usbDevice?.let {
            flashlightInterface = it.getInterface(M400cConstants.VIDEO_HID)
            connection = usbManager.openDevice(it)
            if (!connection.claimInterface(flashlightInterface, true)) {
                throw Exception("Failed to claim Flashlight Interface")
            }
            connected = connection.setInterface(flashlightInterface)
        } ?: throw Exception("Video Device is null")
    }

    /**
     * Function used to close down the [UsbDeviceConnection].
     */
    override fun disconnect() {
        LogUtil.debug("disconnect")
        try {
            connection.releaseInterface(flashlightInterface)
            connection.close()
        } catch (e: Exception) {
            // Eat it
        }
        connected = false
        usbDevice = null
    }

    /**
     * Function used to let you know if the video [UsbDevice] is null or not.
     *
     * @return True if not null.
     */
    override fun isDeviceAvailable(): Boolean {
        return usbDevice?.let {
            true
        } ?: run {
            usbDevice = getVideoDevice(usbManager)
            usbDevice != null
        }
    }

    /**
     * Function used to turn the flashlight/torch on.
     */
    @Throws(Exception::class)
    fun turnFlashlightOn() {
        LogUtil.debug("turnFlashlightOn")
        changeFlashlightState(getFlashlightPacket(true))
    }

    /**
     * Function used to turn the flashlight/torch off.
     */
    @Throws(Exception::class)
    fun turnFlashlightOff() {
        LogUtil.debug("turnFlashlightOn")
        changeFlashlightState(getFlashlightPacket(false))
    }

    private fun changeFlashlightState(byteArray: ByteArray) {
        if (!connected) {
            throw Exception("Device is not connected")
        }
        connection.controlTransfer(
            0x21,
            0x09,
            0x0200,
            flashlightInterface.id,
            byteArray,
            byteArray.size,
            TimeUnit.SECONDS.toMillis(1).toInt()
        )
    }

    // This function simply generates the On/Off ByteArray payload.
    private fun getFlashlightPacket(turnOn: Boolean): ByteArray {
        return if (turnOn) {
            byteArrayOf(2, FLASHLIGHT_ON.toByte(), 0x01)
        } else {
            byteArrayOf(2, FLASHLIGHT_OFF.toByte(), 0x01)
        }
    }
}