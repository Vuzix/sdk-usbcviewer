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

package com.vuzix.sdk.usbcviewer.utils

import android.content.Context
import android.hardware.input.InputManager
import android.view.KeyEvent
import com.vuzix.sdk.usbcviewer.ButtonID
import com.vuzix.sdk.usbcviewer.M400cButton
import com.vuzix.sdk.usbcviewer.USBCDeviceManager

fun Boolean.toInt() = if (this) 1 else 0

fun Int.toBoolean() = this == 1

fun KeyEvent.isFromM400c(context: Context): Boolean {
    val m400cKeyName = "Vuzix Corporation M400C VIEWER System Control"
    try {
        val im = context.getSystemService(InputManager::class.java) as InputManager
        val dev = im.getInputDevice(this.deviceId)
        if (dev.name == m400cKeyName) {
            return true
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return false
}

fun KeyEvent.isFromM400cButton(context: Context, buttonID: ButtonID): Boolean {
    val codes = USBCDeviceManager.shared(context).deviceControlInterface?.getButtonCodes(buttonID)
    val hidCode = HIDKeyCodes.toHID(this.keyCode)
    if (codes != null && hidCode != null) {
        if (codes.shortPressCode == hidCode.toInt() || codes.longPressCode == hidCode.toInt()) {
            return true
        }
    }
    return false
}

fun KeyEvent.getM400cButton(context: Context): M400cButton? {
    val allButtons = USBCDeviceManager.shared(context).deviceControlInterface?.getAllButtonCodes()
    val hidCode = HIDKeyCodes.toHID(this.keyCode)
    if (hidCode != null && allButtons != null) {
        for (button in allButtons) {
            if (button?.shortPressCode == hidCode.toInt() || button?.longPressCode == hidCode.toInt()) {
                return button
            }
        }
    }
    return null
}
