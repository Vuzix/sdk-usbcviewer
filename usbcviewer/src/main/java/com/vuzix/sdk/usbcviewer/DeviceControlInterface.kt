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
import com.vuzix.sdk.usbcviewer.utils.HIDKeyCodes
import com.vuzix.sdk.usbcviewer.utils.LogUtil
import com.vuzix.sdk.usbcviewer.utils.toBoolean

class DeviceControlInterface(usbManager: UsbManager, device: UsbDevice, usbInterface: UsbInterface) : USBCDeviceInterface(usbManager,
    device, usbInterface
) {

    /*
    Read the version of the device and its subsystems.
    Returns:
        USB_vmajor -- SupportedUSB revision(major version)
        USB_vminor -- SupportedUSB revision(minor version)
        ProductID -- Returns 9, for m400c
        Subsys_HWversion -- Scaler FPGA hardware version(versions are returned as whole numbers)
        Subsys_vmajor -- (Currently unused)
        Subsys_vminor -- (Currently unused)
        Tracker_HWversion -- Microcontroller hardware version (versions are returned as whole numbers)
        Tracker_SWversion -- Microcontroller software version (versions are returned as whole numbers)
     */
    fun getVersion(): Version? {
        val bytes = this.get(0x03)
        if (bytes.size >= 10) {
            LogUtil.debug("getVersion: ", bytes, bytes.size)
            val majorUSB = bytes[2].toInt() and 0xFF
            val minorUSB = bytes[3].toInt() and 0xFF
            val productId = bytes[4].toInt() and 0xFF  // always is 9 for m400c
            val subsysHWversion = bytes[5].toInt() and 0xFF
            //            val subsys_vmajor = it[6].toInt() and 0xff   // not implemented
            //            val subsys_vminor = it[7].toInt() and 0xff   // not implemented
            val trackerHWversion = bytes[8].toInt() and 0xFF
            val trackerSWversion = bytes[9].toInt() and 0xFF

            return Version(
                majorUSB,
                minorUSB,
                productId,
                subsysHWversion,
                trackerHWversion,
                trackerSWversion
            )
        }
        return null
    }

    /*
    Return the display panel brightness level.
    1–255: Brightness level
     */
    fun getBrightness(): Int? {
        return getProperty(0x24)
    }

    /*
    Set display panel brightness level. Saved to non-volatile memory.
    Allowed values:
        0: Turn off panel
        1–255: Brightness level 1–100%
    returns: successful boolean
     */
    fun setBrightness(value: Int): Boolean {
        if (value < 0 || value > 255) return false
        return setProperty(0x23, value)
    }

    /*
    Return the state of the force left eye override.
     */
    fun getForceLeftEye(): Boolean? {
        return getProperty(0x31)?.toBoolean() != true
    }

    /*
    Force the device to operate in left eye mode. Rotates the display 180 degrees.
    Saved to non-volatile memory.
     */
    fun setForceLeftEye(value: Boolean): Boolean {
        return setProperty(0x30, if (value) 0 else 1)
    }

    /*
    Return the status of the autorotation feature.
     */
    fun getAutoRotation(): Boolean? {
        return getProperty(0x37)?.toBoolean()
    }

    /*
    Control the status of the autorotation feature. Saved to non-volatile memory.
    NOTE: if Autorotation disabled, device uses the Force Left Eye command to set the device orientation
     */
    fun setAutoRotation(value: Boolean): Boolean {
        return setProperty(0x36, if (value) 1 else 0)
    }

    /*
    Return the key codes assigned to the physical buttons on the device.
     */
    fun getButtonCodes(button: ButtonID): M400cButton? {
        val bytes = this.sendPayload(0xB1, byteArrayOf(button.ordinal.toByte()))
        LogUtil.debug("getButtonCodes: ", bytes, bytes.size)
        if (bytes.size >= 5 && bytes[1].toInt() and 0xff == 0xB1) {
            val buttonId = ButtonID.values()[bytes[2].toInt() and 0xff]
            return M400cButton(
                buttonId,
                bytes[3].toInt() and 0xff,
                bytes[4].toInt() and 0xff
            )
        }
        return null
    }

    /*
    Return the key codes assigned to the all the buttons on the device.
    Returns: Array of M400cButton objects
     */
    fun getAllButtonCodes(): Array<M400cButton?> {
        val allButtons = ButtonID.values()
        val rtn = ArrayList<M400cButton?>()
        for (button in allButtons) {
            rtn.add(getButtonCodes(button))
        }
        return rtn.toTypedArray()
    }

    /*
    Set the key codes returned by the physical buttons on the device.
    Saved to non-volatile memory.
     */
    fun setButtonCodesWithAndroidKeyCodes(
        button: ButtonID,
        shortPressAndroidKeyCode: Int,
        longPressAndroidKeyCode: Int
    ): Boolean {
        val shortCode = HIDKeyCodes.toHID(shortPressAndroidKeyCode)
        if (shortCode == null){
            LogUtil.rel("Cannot map short code $shortPressAndroidKeyCode")
            return false
        }
        val longCode = HIDKeyCodes.toHID(longPressAndroidKeyCode)
        if (longCode == null){
            LogUtil.rel("Cannot map long code $longPressAndroidKeyCode")
            return false
        }

        return setButtonCodesWithHIDKeyCodes(button, shortCode!!.toInt(), longCode!!.toInt())
    }

    /*
    Set the key codes returned by the physical buttons on the device.
    Saved to non-volatile memory.
     */
    fun setButtonCodesWithHIDKeyCodes(
        button: ButtonID,
        shortPressHIDKeyCode: Int,
        longPressHIDKeyCode: Int
    ): Boolean {
        val bytes = this.sendPayload(
            0xB0,
            byteArrayOf(
                button.ordinal.toByte(),
                shortPressHIDKeyCode.toByte(),
                longPressHIDKeyCode.toByte()
            )
        )

        if (bytes.size >= 2 && bytes[1].toInt() and 0xff == 0xB0) {
            return true
        }

        return false
    }

    /*
    Reset NVRAM contents to factory settings.
    Will erase any customization to auto-rotation, orientation, brightness, and button key codes.
     */
    fun restoreDefaults(): Boolean {
        val bytes = this.sendPayload(0x10, null)
        if (bytes.isNotEmpty() && bytes[0].toInt() and 0xff == 1) {
            if (bytes[1].toInt() and 0xff == 0x10) {
                return true
            }
        }
        return false
    }

    /*
    Restores all Buttons to default Keycodes
     */
    fun restoreAllDefaultButtonCodes() {
        restoreDefaultButtonCodes(ButtonID.FRONT)
        restoreDefaultButtonCodes(ButtonID.MIDDLE)
        restoreDefaultButtonCodes(ButtonID.REAR)
        restoreDefaultButtonCodes(ButtonID.SIDE)
    }

    /*
        Restores all Button to default its default keycodes
    */
    fun restoreDefaultButtonCodes(buttonId: ButtonID) {
        val defaults = mapOf(
            0 to Pair(HIDKeyCodes.KEY_RIGHTARROW, HIDKeyCodes.KEY_UPARROW),
            1 to Pair(HIDKeyCodes.KEY_LEFTARROW, HIDKeyCodes.KEY_DOWNARROW),
            2 to Pair(HIDKeyCodes.KEY_ENTER, HIDKeyCodes.KEY_ESCAPE),
            3 to Pair(HIDKeyCodes.KEY_F16, HIDKeyCodes.KEY_SYSTEM_SLEEP)
        )

        val buttonDefaults = defaults[buttonId.ordinal]
        if (buttonDefaults != null) {
            setButtonCodesWithHIDKeyCodes(
                buttonId,
                buttonDefaults.first.toInt(),
                buttonDefaults.second.toInt()
            )
        }
    }

    /*
    Return any active error codes from the device.
    Error codes are currently undefined and will be implemented in a future release
     */
    fun getErrorReport() {
        val bytes = this.sendPayload(0xF6, null)
        LogUtil.debug("Error Report: ", bytes, 6)
    }


    private fun setProperty(command: Int, value: Int): Boolean {
        val bytes = this.set(command, byteArrayOf(value.toByte()))
        if (bytes[0].toInt() and 0xff == 1 && bytes[1].toInt() and 0xff == command) {
            return true
        }
        return false
    }

    private fun getProperty(command: Int): Int? {
        val bytes = this.get(command)
        LogUtil.debug("getProperty: ", bytes, bytes.size)
        if (bytes.size >= 3) {
            return bytes[2].toInt() and 0xff
        }
        return null
    }
}

enum class ButtonID {
    FRONT,
    MIDDLE,
    REAR,
    SIDE
}

enum class Rotation {
    AUTO_ROTATE,
    RIGHT_EYE,
    LEFT_EYE
}

data class Version(val majorUSB: Int,
                           val minorUSB: Int,
                           val productID: Int,
                           val subsys_HWversion: Int,
                           val tracker_HWversion: Int,
                           val tracker_SWversion: Int){
    override fun toString(): String {
        return "VERSION: $subsys_HWversion, USB: $majorUSB.$minorUSB, MCU_HW: $tracker_HWversion, MCU_SW:$tracker_SWversion"
    }
}

data class TouchPadSettings(val horizontalDirectionRight: Boolean,
                            val verticalDirectionUP: Boolean,
                            val scrollDirectionUp: Boolean,
                            val panDirectionRight: Boolean,
                            val zoomDirectionIn: Boolean) {}

data class M400cButton(val buttonID: ButtonID, val shortPressCode: Int, val longPressCode: Int) {}