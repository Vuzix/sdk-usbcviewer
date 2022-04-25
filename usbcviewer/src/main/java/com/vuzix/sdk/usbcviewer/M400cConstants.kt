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

object M400cConstants {
    const val ACTION_USB_PERMISSION = "com.android.vuzix.USB_PERMISSION"

    // Main Viewer Interface controls (auto-rotate, brightness, buttons key codes, etc)
    const val VIEWER_PID = 509   // 0x01fd
    const val VIEWER_VID = 7086   // 0x1bea

    // TOUCHPAD Control
    const val HID_TOUCHPAD_VID = 7086   // 0x1bea
    const val HID_TOUCHPAD_PID = 1296  // 0x0510

    // CAMERA, UVC
    const val CAMERA_PID = 195  // 0x00c3
    const val CAMERA_VID = 1204 // 0x04b4

    // AUDIO
    const val AUDIO_PID = 22529 // 0x5801
    const val AUDIO_VID = 1156  // 0x0484

    // IDs for the HID interfaces
    const val HID_VIEWER_CONTROL_INTERFACE = 0
    const val HID_SENSOR_INTERFACE = 1
    const val HID_TOUCHPAD_INTERFACE = 1

    // IDs for HID endpoints
    const val HID_VIEWER_CONTROL_INBOUND = 1
    const val HID_VIEWER_CONTROL_OUTBOUND = 0
    const val HID_SENSOR_INBOUND = 0

    // IDs for the CAMERA Interfaces
    const val CAMERA_CONTROL = 0
    const val CAMERA_STREAM = 1
    const val CAMERA_HID = 2

    // IDs for Video Endpoints
    const val VIDEO_CONTROL_ENDPOINT_ONE = 0
    const val VIDEO_STREAM_ENDPOINT_ONE = 0
    const val VIDEO_HID_ENDPOINT_ONE = 0

    // IDs for the Outgoing Audio Interfaces
    const val MIC_CONTROL = 0
    const val MIC_STREAM = 1

    // IDs for the Outgoing Audio Interfaces
    const val MIC_STREAM_ENDPOINT_ONE = 0
    const val MIC_STREAM_ENDPOINT_TWO = 1

    // IDs for the Incoming Audio Interfaces
    const val AUDIO_CONTROL = 3
    const val AUDIO_STREAM_ONE = 4
    const val AUDIO_STREAM_TWO = 5

    // IDs for Outgoing Audio Endpoints

    // IDs for Incoming Audio Endpoints
    const val AUDIO_STREAM_ONE_ENDPOINT_ONE = 0
    const val AUDIO_STREAM_ONE_ENDPOINT_TWO = 1

    // Keyboard IDs / Command
    const val KEY_BACK = 28 // Enter
    const val KEY_BACK_LONG = 1 // Escape
    const val KEY_FRONT = 106 // Move Right
    const val KEY_FRONT_LONG = 103 // Move Up
    const val KEY_MIDDLE = 105 // Move Left
    const val KEY_MIDDLE_LONG = 108 // Move Down
    const val KEY_SIDE = 57 // Unknown
}



