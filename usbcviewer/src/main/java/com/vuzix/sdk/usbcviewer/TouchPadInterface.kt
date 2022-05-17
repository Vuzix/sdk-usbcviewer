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

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.vuzix.sdk.usbcviewer.utils.LogUtil
import com.vuzix.sdk.usbcviewer.utils.toBoolean
import com.vuzix.sdk.usbcviewer.utils.toInt
import java.util.*

class TouchPadInterface (usbManager: UsbManager, device: UsbDevice, usbInterface: UsbInterface) : USBCDeviceInterface(usbManager,
    device, usbInterface
) {

    /*
    Read the orientation of the touchpad sensor.
     */
    fun getTouchPadOrientation(): TouchPadSettings? {
        val bytes = this.sendPayload(0x39, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0))

        LogUtil.debug("getTouchPadOrientation: ", bytes, bytes.size)
        if (bytes.size >= 11) {
            val settings = bytes[9].toInt() and 0xFF
            val hDirection = (settings shr 0) and 1
            val vDirection = (settings shr 1) and 1
            val sDirection = (settings shr 2) and 1
            val pDirection = (settings shr 3) and 1
            val zDirection = (settings shr 4) and 1
            return TouchPadSettings(hDirection.toBoolean(),
                vDirection.toBoolean(), sDirection.toBoolean(),
                pDirection.toBoolean(), zDirection.toBoolean())
        }

        return null
    }

    /*
    Set the orientation of the touchpad sensor.
    */
    fun setTouchPadOrientation(settings: TouchPadSettings): Boolean {
        val magicByte = BitSet(8)
        if (settings.horizontalDirectionRight.toInt() == 1) magicByte.flip(0)
        if (settings.verticalDirectionUP.toInt() == 1) magicByte.flip(1)
        if (settings.scrollDirectionUp.toInt() == 1) magicByte.flip(2)
        if (settings.panDirectionRight.toInt() == 1) magicByte.flip(3)
        if (settings.zoomDirectionIn.toInt() == 1) magicByte.flip(4)
        val bytes = this.sendPayload(0x39, byteArrayOf(0, 0, 0, 0, 0, 0, 1, 0, if (magicByte.toByteArray().isEmpty()) 0 else magicByte.toByteArray()[0]))
        if (bytes.size >= 9 && (bytes[1].toInt() and 0xFF) == 0x39) {
            return true
        }
        return false
    }

    /*
    Is the touchpad enabled?
     */
    fun getTouchPadEnabled(): Boolean? {
        val bytes = get(0x73)
        LogUtil.debug("getTouchPadEnabled: ", bytes, bytes.size)
        if (bytes.size >= 5) {
            return bytes[4].toInt().toBoolean()
        }
        return null
    }

    /*
    Enable or disable the touchpad sensor.
     */
    fun setTouchPadEnabled(value: Boolean): Boolean {
        val bytes = sendPayload(0x73, byteArrayOf(0, 0, value.toInt().toByte()))
        if (bytes.size >= 5 && (bytes[1].toInt() and 0xFF) == 0x73) {
            return true
        }
        return false
    }
}