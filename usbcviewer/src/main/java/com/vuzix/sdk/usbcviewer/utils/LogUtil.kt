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

import android.util.Log
import com.vuzix.sdk.usbcviewer.BuildConfig
import java.util.regex.Pattern

/**
 * This object class is used to log messages within the SDK.
 */
object LogUtil {

    private const val MAX_TAG_LENGTH = 23
    private val ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$")
    private val tag: String?
        get() = Throwable().stackTrace
            .first { it.className != LogUtil::class.java.name }
            .let(LogUtil::createStackElementTag)

    private fun createStackElementTag(element: StackTraceElement): String? {
        var tag = element.className.substringAfterLast('.')
        val m = ANONYMOUS_CLASS.matcher(tag)
        if (m.find()) {
            tag = m.replaceAll("")
        }
        // Tag length limit was removed in API 26.
        return if (tag.length <= MAX_TAG_LENGTH) {
            tag
        } else {
            tag.substring(0, MAX_TAG_LENGTH)
        }
    }

    /**
     * Function used for debug logging when you don't want these messages to show up
     * in the release product. Explicitly writes with Debug Level Priority.
     *
     * @param message The message to be displayed.
     */
    fun debug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    /**
     * Function used for release logging when you want something to show up in the
     * release product. Explicitly writes with Info Level Priority.
     */
    fun rel(message: String) {
        Log.i(tag, message)
    }
}

// Extension functions that can be used for the purpose of logging ByteArray and FloatArray data
fun ByteArray.strPrint(): String {
    val hexChars = CharArray(this.size * 2)
    val hexArray = "0123456789ABCDEF".toCharArray()
    for (i in this.indices) {
        val v = this[i].toInt() and 0xFF
        hexChars[i * 2] = hexArray[v ushr 4]
        hexChars[i * 2 + 1] = hexArray[v and 0x0F]
    }
    return hexChars.print()
}

fun CharArray.print(): String {
    val sb = StringBuilder()
    this.forEachIndexed { index, _ ->
        sb.append(this[index])
    }
    return sb.toString()
}

fun FloatArray.strPrint(): String {
    val sb = StringBuilder()
    this.forEachIndexed { index, _ ->
        sb.append(this[index])
        if (this[index] != this[lastIndex])
            sb.append(" | ")
    }
    return sb.toString()
}