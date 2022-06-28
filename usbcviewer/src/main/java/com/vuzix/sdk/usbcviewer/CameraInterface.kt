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
import kotlin.Error

open class CameraInterface(usbManager: UsbManager, device: UsbDevice, usbInterface: UsbInterface) : USBCDeviceInterface(usbManager,
    device, usbInterface
) {

    fun getExposureCompensation(): Exposure? {
        val data = get(254, byteArrayOf(1.toByte()))
        if (data.size > 2) {
            return parseExposure(data[1].toInt())
        }
        return null
    }

    private fun parseExposure(value: Int): Exposure? {
        LogUtil.debug("parseExposure: $value")
        return try {
            Exposure.values()[value]
        } catch (e: Error) {
            null
        }
    }

    /*
    Sets the exposure compensation for the camera sensor.
     */
    fun setExposureCompensation(exposure: Exposure) {
        val payload = byteArrayOf(exposure.ordinal.toByte())
        set(1, payload)
    }

    fun getFlickerCancelPriority(): Boolean? {
        val data = get(254, byteArrayOf(2.toByte()))
        if (data.size > 2) {
            return data[1].toInt().toBoolean()
        }
        return null
    }

    /*
    Adjusts the priority of the flicker cancellation algorithm.
    Priority can be shifted to auto-exposure (AE) [False, default] or [True] to reduce flicker as much as possible.
     */
    fun setFlickerCancelPriority(on:Boolean) {
        val payload = byteArrayOf(on.toInt().toByte())
        set(2, payload)
    }

    fun getMaxFrameRate(): Int? {
        val data = get(254, byteArrayOf(3.toByte()))
        if (data.size > 2) {
            return data[1].toInt()
        }
        return null
    }

    /*
    Set the maximum frame rate returned by the camera over USB.
    NOTE: Allowed Values:
        0: Disable frame rate control
        3–60: Set maximum rate rate to this value
     */
    fun setMaxFrameRate(frameRate: Int) {
        require(frameRate in 3..60 || frameRate == 0) {"Frame rate should be in the range of 3-60 or 0 to disable frame rate control."}
        set(3, byteArrayOf(frameRate.toByte()))
    }

    fun getAutoFocusMode(): AutoFocusMode? {
        val data = get(254, byteArrayOf(4.toByte()))
        if (data.size > 2) {
            return parseAFMode(data[1].toInt())
        }
        return null
    }

    private fun parseAFMode(value: Int): AutoFocusMode? {
        val colorMode = when(value){
            0 -> AutoFocusMode.ONE_SHOT
            3 -> AutoFocusMode.CONTINUOUS
            8 -> AutoFocusMode.ONE_SHOT_AND_PDAF_HYBRID
            15 -> AutoFocusMode.CONTINUOUS_AND_PDAF_HYBRID
            else -> null
        }
        return colorMode
    }

    /*
    Set the autofocus (AF) method.
    Allowed values:
        One shot contrast AF
        Continuous contrast AF
        One shot contrast and PDAF hybrid AF
        Continuous contrast and PDAF hybrid AF
     */
    fun setAutoFocusMode(mode: AutoFocusMode) {
        set(4, byteArrayOf(mode.value.toByte()))
    }

    fun getNoiseReductionMode(): NoiseReductionMode? {
        val data = get(254, byteArrayOf(0x05))
        if (data.size > 2) {
            return parseNoiseReductionMode((data[1].toInt() shr 7) and 1)
        }
        return null
    }

    private fun parseNoiseReductionMode(value: Int): NoiseReductionMode? {
        return try {
            NoiseReductionMode.values()[value]
        } catch (e: Error) {
            null
        }
    }

    fun getNoiseReductionStrength(): Int? {
        val data = get(254, byteArrayOf(0x05))
        if (data.size > 2) {
            val mode = parseNoiseReductionMode((data[1].toInt() shr 7) and 1)
            if (mode == NoiseReductionMode.FIXED) {
                return data[1].toInt() - 0x80
            }
        }
        return null
    }

    /*
    Set the noise reduction to auto.
     */
    fun setNoiseReductionToAuto() {
        set(5, byteArrayOf(0.toByte()))
    }

    /*
    Set the noise reduction to fixed mode. In fixed mode, the noise reduction strength can be adjusted.
    Allowed Strength Value:
        0: Weakest
        ...
        10: Strongest
     */
    fun setNoiseReductionToFixed(strength: Int) {
        //high bit needs to be 1
        require(strength in 0..10) {"Noise reduction strength must be in the range of 0 to 10. 0 weakest, 10 strongest"}
        set(5, byteArrayOf((strength + 0x80).toByte()))
    }

    fun getScannerMode(): Boolean? {
        val data = get(254, byteArrayOf(0x06))
        if (data.size > 2) {
            if (data[1].toInt() == 0x20) {
                return true
            }
            return false
        }
        return null
    }

    /*
    Sets the camera capture settings to work better for black and white text documents.
    Default: is off (False)
     */
    fun setScannerMode(on:Boolean) {
        if (on) {
            set(6, byteArrayOf(0x20))
        }
        else {
            set(6, byteArrayOf(0x00))
        }
    }

    fun getColorMode(): Pair<ColorMode?,Int>? {
        val data = get(254, byteArrayOf(0x07))
        if (data.size > 3) {
            val colorMode = parseColorMode(data[1].toInt())
            val threshold = data[2].toInt() and 0xFF
            return Pair(colorMode, threshold)
        }
        return null
    }

    private fun parseColorMode(value: Int): ColorMode? {
        val colorMode = when(value){
            0 -> ColorMode.COLOR
            1 -> ColorMode.MONO
            3 -> ColorMode.NEGATIVE
            10 -> ColorMode.BLACK_AND_WHITE
            else -> null
        }
        return colorMode
    }

    /*
    Set the color capture mode of the camera.
    Allowed values:
        Color
        Mono
        Negative
        Black and white, default threshold is auto
     */
    fun setColorMode(colorMode: ColorMode) {
        val payload = byteArrayOf(colorMode.value.toByte(), 0.toByte())
        set(7, payload)
    }

    /*
    Set the color capture mode of the camera to Black and white with a threshold
    Allowed values:
        0: Auto
        1–255: Gray threshold value
     */
    fun setColorModeToBlackAndWhiteWithThreshold(threshold: Int){
        require(threshold in 1..255) {"Threshold must be in the range of 0 to 255. 0: auto, 1–255: Gray threshold value"}
        val payload = byteArrayOf(ColorMode.BLACK_AND_WHITE.value.toByte(), threshold.toByte())
        set(7, payload)
    }

    fun getJpegQuality(): Pair<Boolean, Int?>? {
        val data = get(254, byteArrayOf(0x08))
        if (data.size > 3) {
            val isAuto = data[2].toInt().toBoolean()
            var qFactor: Int? = null
            if (isAuto == false) {
                qFactor = data[3].toInt()
            }
            return Pair(isAuto, qFactor)
        }
        return null
    }

    /*
    Set the JPEG Q factor to Auto
     */
    fun setJpegQualityToAuto(){
        set(8, byteArrayOf(1.toByte()))
    }

    /*
    Set the JPEG Q factor, which affects how many bits are used per color pixel.
    A higher Q factor means a higher quality image.
    Allowed values:
        13–100: JPEG Q factor
     */
    fun setJpegQualityToManual(qFactor: Int): Boolean {
        require(qFactor in 13..100) {"JPG qFactor value must be between 13 and 100"}
        if (qFactor in 13..100) {
            set(8, byteArrayOf(0x00, qFactor.toByte()))
            return true
        }
        return false
    }

    /*
    Turns on/off the LED torch (flashlight) mode.
    Only to be used if the camera is off or in video mode.
     */
    fun setFlashLight(on: Boolean) {
        val command = if (on) 9 else 10
        set(command, byteArrayOf(0x01))
    }

    /*
    Turn on the LED as flash
    The LED will be continuously illuminated during camera preview and image capture.

    Force flash on -> Front LED will be illuminated during image capture.

    Only to be used if the camera is in still capture mode.
     */
    fun setFlashOn(force: Boolean?){
        if (force == true) {
            set(12, byteArrayOf(0x01))
        }
        else {
            set(11, byteArrayOf(0x01))
        }
    }

    /*
    Set flash to auto.
    Front LED will be illuminated during image capture when the scene is dimly lit.
    Only to be used if the camera is in still capture mode.
     */
    fun setFlashAuto() {
        set(13, byteArrayOf(0x01))
    }

    /*
    Set flash to off.
    Front LED will not be used during image capture.
    Only to be used if the camera is in still capture mode.
     */
    fun setFlashOff() {
        set(14, byteArrayOf(0x01))
    }

    /*
    returns the default values of the device.
     */
    fun getDefaultValues(): CameraValues? {
        val data = get(254, byteArrayOf(21.toByte()))
        return parseCameraValues(data)
    }

    /*
    Returns all the current camera values of the device
     */
    // NOTE: current values is empty if you haven't set it yet. (firmware limitation)
    // NOTE: only returns 11 bytes, instead of 13, missing autorotation and forceLeftEye (firmware limitation)
    fun getCurrentValues(): CameraValues? {
        val data = get(254, byteArrayOf(20.toByte()))
        return parseCameraValues(data)
    }

    private fun parseCameraValues(data: ByteArray): CameraValues? {
        if (data.size >= 12) {
            val exposure = parseExposure(data[1].toInt())
            val priority = data[2].toInt().toBoolean()
            val frameRate = data[3].toInt()
            val afMode = parseAFMode(data[4].toInt())
            val noiseReduction = parseNoiseReductionMode((data[5].toInt() shr 7) and 1)
            var noiseReductionStrength: Int? = null
            if (noiseReduction == NoiseReductionMode.FIXED) {
                noiseReductionStrength =  data[5].toInt() - 0x80
            }
            val scanMode = data[6].toInt() == 0x20
            val colorMode = parseColorMode(data[7].toInt())
            val colorModeThreshold = data[8].toInt()
            val jpegMode =  data[9].toInt().toBoolean()
            val jpegQFactor = data[10].toInt()


            return CameraValues(exposure, priority, frameRate, afMode,
                noiseReduction, noiseReductionStrength, scanMode, colorMode,
                colorModeThreshold, jpegMode, jpegQFactor)
        }
        return null
    }

    fun getFirmwareVersion(): FirmwareVersion? {
        val data = get(254, byteArrayOf(0x28))
        if (data.size >= 8) {
            val major = data[1].toInt()
            val minor = data[2].toInt()
            val year = data[3].toInt()
            val month = data[4].toInt()
            val day = data[5].toInt()
            val ispMajor = data[6].toInt()
            val ispMinor = data[7].toInt()
            return FirmwareVersion(major, minor, year, month, day, ispMajor, ispMinor)
        }
        return null
    }
}

enum class Exposure {
    N_6_over_3,
    N_5_over_3,
    N_4_over_3,
    N_3_over_3,
    N_2_over_3,
    N_1_over_3,
    ZERO,
    P_1_over_3,
    P_2_over_3,
    P_3_over_3,
    P_4_over_3,
    P_5_over_3,
    P_6_over_3;
}

enum class ColorMode(val value: Int) {
    COLOR(0),
    MONO(1),
    NEGATIVE(3),
    BLACK_AND_WHITE(10);
}

enum class AutoFocusMode(val value: Int) {
    ONE_SHOT(0),
    CONTINUOUS(3),
    ONE_SHOT_AND_PDAF_HYBRID(8),
    CONTINUOUS_AND_PDAF_HYBRID(15)
}

enum class NoiseReductionMode{
    AUTO,
    FIXED
}

data class CameraValues(val exposure: Exposure?,
                        val priority: Boolean,
                        val frameRate: Int,
                        val autoFocusMode: AutoFocusMode?,
                        val noiseReduction: NoiseReductionMode?,
                        val noiseReductionStrength: Int?,
                        val scanMode: Boolean,
                        val colorMode: ColorMode?,
                        val colorModeThreshold: Int,
                        val jpegMode: Boolean,
                        val jpegQFactor: Int) {
    override fun toString(): String {
        return "exposure: $exposure, priority: $priority, frameRate: $frameRate, autoFocusMode: ${autoFocusMode?.name}, " +
                "noiseReduction: ${noiseReduction.toString()}, scanMode: $scanMode, colorMode: ${colorMode?.name}, " +
                "colorModeThreshold: $colorModeThreshold, jpegMode: $jpegMode, jpegQFactor: $jpegQFactor"
    }
}

data class FirmwareVersion(val major: Int,
                           val minor: Int,
                           val year: Int,
                           val month: Int,
                           val day: Int,
                           val ispMajor: Int,
                           val ispMinor: Int){}