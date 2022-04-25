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

import android.view.KeyEvent

object HIDKeyCodes {
    
    val KEY_ERRORROLLOVER = 0x01U
    val KEY_POSTFAIL = 0x02U
    val KEY_ERRORUNDEFINED = 0x03U
    val KEY_A = 0x04U
    val KEY_B = 0x05U
    val KEY_C = 0x06U
    val KEY_D = 0x07U
    val KEY_E = 0x08U
    val KEY_F = 0x09U
    val KEY_G = 0x0AU
    val KEY_H = 0x0BU
    val KEY_I = 0x0CU
    val KEY_J = 0x0DU
    val KEY_K = 0x0EU
    val KEY_L = 0x0FU
    val KEY_M = 0x10U
    val KEY_N = 0x11U
    val KEY_O = 0x12U
    val KEY_P = 0x13U
    val KEY_Q = 0x14U
    val KEY_R = 0x15U
    val KEY_S = 0x16U
    val KEY_T = 0x17U
    val KEY_U = 0x18U
    val KEY_V = 0x19U
    val KEY_W = 0x1AU
    val KEY_X = 0x1BU
    val KEY_Y = 0x1CU
    val KEY_Z = 0x1DU
    val KEY_1_EXCLAMATION_MARK = 0x1EU
    val KEY_2_AT = 0x1FU
    val KEY_3_NUMBER_SIGN = 0x20U
    val KEY_4_DOLLAR = 0x21U
    val KEY_5_PERCENT = 0x22U
    val KEY_6_CARET = 0x23U
    val KEY_7_AMPERSAND = 0x24U
    val KEY_8_ASTERISK = 0x25U
    val KEY_9_OPARENTHESIS = 0x26U
    val KEY_0_CPARENTHESIS = 0x27U
    val KEY_ENTER = 0x28U
    val KEY_ESCAPE = 0x29U
    val KEY_BACKSPACE = 0x2AU
    val KEY_TAB = 0x2BU
    val KEY_SPACEBAR = 0x2CU
    val KEY_MINUS_UNDERSCORE = 0x2DU
    val KEY_EQUAL_PLUS = 0x2EU
    val KEY_OBRACKET_AND_OBRACE = 0x2FU
    val KEY_CBRACKET_AND_CBRACE = 0x30U
    val KEY_BACKSLASH_VERTICAL_BAR = 0x31U
    val KEY_NONUS_NUMBER_SIGN_TILDE = 0x32U
    val KEY_SEMICOLON_COLON = 0x33U
    val KEY_SINGLE_AND_DOUBLE_QUOTE = 0x34U
    val KEY_GRAVE_ACCENT_AND_TILDE = 0x35U
    val KEY_COMMA_AND_LESS = 0x36U
    val KEY_DOT_GREATER = 0x37U
    val KEY_SLASH_QUESTION = 0x38U
    val KEY_CAPS_LOCK = 0x39U
    val KEY_F1 = 0x3AU
    val KEY_F2 = 0x3BU
    val KEY_F3 = 0x3CU
    val KEY_F4 = 0x3DU
    val KEY_F5 = 0x3EU
    val KEY_F6 = 0x3FU
    val KEY_F7 = 0x40U
    val KEY_F8 = 0x41U
    val KEY_F9 = 0x42U
    val KEY_F10 = 0x43U
    val KEY_F11 = 0x44U
    val KEY_F12 = 0x45U
    val KEY_PRINTSCREEN = 0x46U
    val KEY_SCROLL_LOCK = 0x47U
    val KEY_PAUSE = 0x48U
    val KEY_INSERT = 0x49U
    val KEY_HOME = 0x4AU
    val KEY_PAGEUP = 0x4BU
    val KEY_DELETE = 0x4CU
    val KEY_END1 = 0x4DU
    val KEY_PAGEDOWN = 0x4EU
    val KEY_RIGHTARROW = 0x4FU
    val KEY_LEFTARROW = 0x50U
    val KEY_DOWNARROW = 0x51U
    val KEY_UPARROW = 0x52U
    val KEY_KEYPAD_NUM_LOCK_AND_CLEAR = 0x53U
    val KEY_KEYPAD_SLASH = 0x54U
    val KEY_KEYPAD_ASTERIKS = 0x55U
    val KEY_KEYPAD_MINUS = 0x56U
    val KEY_KEYPAD_PLUS = 0x57U
    val KEY_KEYPAD_ENTER = 0x58U
    val KEY_KEYPAD_1_END = 0x59U
    val KEY_KEYPAD_2_DOWN_ARROW = 0x5AU
    val KEY_KEYPAD_3_PAGEDN = 0x5BU
    val KEY_KEYPAD_4_LEFT_ARROW = 0x5CU
    val KEY_KEYPAD_5 = 0x5DU
    val KEY_KEYPAD_6_RIGHT_ARROW = 0x5EU
    val KEY_KEYPAD_7_HOME = 0x5FU
    val KEY_KEYPAD_8_UP_ARROW = 0x60U
    val KEY_KEYPAD_9_PAGEUP = 0x61U
    val KEY_KEYPAD_0_INSERT = 0x62U
    val KEY_KEYPAD_DECIMAL_SEPARATOR_DELETE = 0x63U
    val KEY_NONUS_BACK_SLASH_VERTICAL_BAR = 0x64U
    val KEY_APPLICATION = 0x65U
    val KEY_POWER = 0x66U
    val KEY_KEYPAD_EQUAL = 0x67U
    val KEY_F13 = 0x68U
    val KEY_F14 = 0x69U
    val KEY_F15 = 0x6AU
    val KEY_F16 = 0x6BU
    val KEY_F17 = 0x6CU
    val KEY_F18 = 0x6DU
    val KEY_F19 = 0x6EU
    val KEY_F20 = 0x6FU
    val KEY_F21 = 0x70U
    val KEY_F22 = 0x71U
    val KEY_F23 = 0x72U
    val KEY_F24 = 0x73U
    val KEY_EXECUTE = 0x74U
    val KEY_HELP = 0x75U
    val KEY_MENU = 0x76U
    val KEY_SELECT = 0x77U
    val KEY_STOP = 0x78U
    val KEY_AGAIN = 0x79U
    val KEY_UNDO = 0x7AU
    val KEY_CUT = 0x7BU
    val KEY_COPY = 0x7CU
    val KEY_PASTE = 0x7DU
    val KEY_FIND = 0x7EU
    val KEY_MUTE = 0x7FU
    val KEY_VOLUME_UP = 0x80U
    val KEY_VOLUME_DOWN = 0x81U
    val KEY_LOCKING_CAPS_LOCK = 0x82U
    val KEY_LOCKING_NUM_LOCK = 0x83U
    val KEY_LOCKING_SCROLL_LOCK = 0x84U
    val KEY_KEYPAD_COMMA = 0x85U
    val KEY_KEYPAD_EQUAL_SIGN = 0x86U
    val KEY_INTERNATIONAL1 = 0x87U
    val KEY_INTERNATIONAL2 = 0x88U
    val KEY_INTERNATIONAL3 = 0x89U
    val KEY_INTERNATIONAL4 = 0x8AU
    val KEY_INTERNATIONAL5 = 0x8BU
    val KEY_INTERNATIONAL6 = 0x8CU
    val KEY_INTERNATIONAL7 = 0x8DU
    val KEY_INTERNATIONAL8 = 0x8EU
    val KEY_INTERNATIONAL9 = 0x8FU
    val KEY_LANG1 = 0x90U
    val KEY_LANG2 = 0x91U
    val KEY_LANG3 = 0x92U
    val KEY_LANG4 = 0x93U
    val KEY_LANG5 = 0x94U
    val KEY_LANG6 = 0x95U
    val KEY_LANG7 = 0x96U
    val KEY_LANG8 = 0x97U
    val KEY_LANG9 = 0x98U
    val KEY_ALTERNATE_ERASE = 0x99U
    val KEY_SYSREQ = 0x9AU
    val KEY_CANCEL = 0x9BU
    val KEY_CLEAR = 0x9CU
    val KEY_PRIOR = 0x9DU
    val KEY_RETURN = 0x9EU
    val KEY_SEPARATOR = 0x9FU
    val KEY_OUT = 0xA0U
    val KEY_OPER = 0xA1U
    val KEY_CLEAR_AGAIN = 0xA2U
    val KEY_CRSEL = 0xA3U
    val KEY_EXSEL = 0xA4U
    val KEY_KEYPAD_00 = 0xB0U
    val KEY_KEYPAD_000 = 0xB1U
    val KEY_THOUSANDS_SEPARATOR = 0xB2U
    val KEY_DECIMAL_SEPARATOR = 0xB3U
    val KEY_CURRENCY_UNIT = 0xB4U
    val KEY_CURRENCY_SUB_UNIT = 0xB5U
    val KEY_KEYPAD_OPARENTHESIS = 0xB6U
    val KEY_KEYPAD_CPARENTHESIS = 0xB7U
    val KEY_KEYPAD_OBRACE = 0xB8U
    val KEY_KEYPAD_CBRACE = 0xB9U
    val KEY_KEYPAD_TAB = 0xBAU
    val KEY_KEYPAD_BACKSPACE = 0xBBU
    val KEY_KEYPAD_A = 0xBCU
    val KEY_KEYPAD_B = 0xBDU
    val KEY_KEYPAD_C = 0xBEU
    val KEY_KEYPAD_D = 0xBFU
    val KEY_KEYPAD_E = 0xC0U
    val KEY_KEYPAD_F = 0xC1U
    val KEY_KEYPAD_XOR = 0xC2U
    val KEY_KEYPAD_CARET = 0xC3U
    val KEY_KEYPAD_PERCENT = 0xC4U
    val KEY_KEYPAD_LESS = 0xC5U
    val KEY_KEYPAD_GREATER = 0xC6U
    val KEY_KEYPAD_AMPERSAND = 0xC7U
    val KEY_KEYPAD_LOGICAL_AND = 0xC8U
    val KEY_KEYPAD_VERTICAL_BAR = 0xC9U
    val KEY_KEYPAD_LOGIACL_OR = 0xCAU
    val KEY_KEYPAD_COLON = 0xCBU
    val KEY_KEYPAD_NUMBER_SIGN = 0xCCU
    val KEY_KEYPAD_SPACE = 0xCDU
    val KEY_KEYPAD_AT = 0xCEU
    val KEY_KEYPAD_EXCLAMATION_MARK = 0xCFU
    val KEY_KEYPAD_MEMORY_STORE = 0xD0U
    val KEY_KEYPAD_MEMORY_RECALL = 0xD1U
    val KEY_KEYPAD_MEMORY_CLEAR = 0xD2U
    val KEY_KEYPAD_MEMORY_ADD = 0xD3U
    val KEY_KEYPAD_MEMORY_SUBTRACT = 0xD4U
    val KEY_KEYPAD_MEMORY_MULTIPLY = 0xD5U
    val KEY_KEYPAD_MEMORY_DIVIDE = 0xD6U
    val KEY_KEYPAD_PLUSMINUS = 0xD7U
    val KEY_KEYPAD_CLEAR = 0xD8U
    val KEY_KEYPAD_CLEAR_ENTRY = 0xD9U
    val KEY_KEYPAD_BINARY = 0xDAU
    val KEY_KEYPAD_OCTAL = 0xDBU
    val KEY_KEYPAD_DECIMAL = 0xDCU
    val KEY_KEYPAD_HEXADECIMAL = 0xDDU
    val KEY_LEFTCONTROL = 0xE0U
    val KEY_LEFTSHIFT = 0xE1U
    val KEY_LEFTALT = 0xE2U
    val KEY_LEFT_GUI = 0xE3U
    val KEY_RIGHTCONTROL = 0xE4U
    val KEY_RIGHTSHIFT = 0xE5U
    val KEY_RIGHTALT = 0xE6U
    val KEY_RIGHT_GUI = 0xE7U

    val KEY_SYSTEM_POWERDOWN = 0xF1U
    val KEY_SYSTEM_SLEEP	= 0xF2U
    val KEY_SYSTEM_WAKEUP	= 0xF3U

    val MODIFERKEYS_LEFT_CTRL = 0x01U
    val MODIFERKEYS_LEFT_SHIFT = 0x02U
    val MODIFERKEYS_LEFT_ALT = 0x04U
    val MODIFERKEYS_LEFT_GUI = 0x08U
    val MODIFERKEYS_RIGHT_CTRL = 0x10U
    val MODIFERKEYS_RIGHT_SHIFT = 0x20U
    val MODIFERKEYS_RIGHT_ALT = 0x40U
    val MODIFERKEYS_RIGHT_GUI = 0x80U


    fun toHID(androidKeyCode: Int): UInt? {

        when(androidKeyCode) {
            KeyEvent.KEYCODE_1 -> return KEY_1_EXCLAMATION_MARK
            KeyEvent.KEYCODE_2 -> return KEY_2_AT
            KeyEvent.KEYCODE_3 -> return KEY_3_NUMBER_SIGN
            KeyEvent.KEYCODE_4 -> return KEY_4_DOLLAR
            KeyEvent.KEYCODE_5 -> return KEY_5_PERCENT
            KeyEvent.KEYCODE_6 -> return KEY_6_CARET
            KeyEvent.KEYCODE_7 -> return KEY_7_AMPERSAND
            KeyEvent.KEYCODE_8 -> return KEY_8_ASTERISK
            KeyEvent.KEYCODE_9 -> return KEY_9_OPARENTHESIS
            KeyEvent.KEYCODE_0 -> return KEY_0_CPARENTHESIS

            KeyEvent.KEYCODE_A -> return KEY_A
            KeyEvent.KEYCODE_B -> return KEY_B
            KeyEvent.KEYCODE_C -> return KEY_C
            KeyEvent.KEYCODE_D -> return KEY_D
            KeyEvent.KEYCODE_E -> return KEY_E
            KeyEvent.KEYCODE_F -> return KEY_F
            KeyEvent.KEYCODE_G -> return KEY_G
            KeyEvent.KEYCODE_H -> return KEY_H
            KeyEvent.KEYCODE_I -> return KEY_I
            KeyEvent.KEYCODE_J -> return KEY_J
            KeyEvent.KEYCODE_K -> return KEY_K
            KeyEvent.KEYCODE_L -> return KEY_L
            KeyEvent.KEYCODE_M -> return KEY_M
            KeyEvent.KEYCODE_N -> return KEY_N
            KeyEvent.KEYCODE_O -> return KEY_O
            KeyEvent.KEYCODE_P -> return KEY_P
            KeyEvent.KEYCODE_Q -> return KEY_Q
            KeyEvent.KEYCODE_R -> return KEY_R
            KeyEvent.KEYCODE_S -> return KEY_S
            KeyEvent.KEYCODE_T -> return KEY_T
            KeyEvent.KEYCODE_U -> return KEY_U
            KeyEvent.KEYCODE_V -> return KEY_V
            KeyEvent.KEYCODE_W -> return KEY_W
            KeyEvent.KEYCODE_X -> return KEY_X
            KeyEvent.KEYCODE_Y -> return KEY_Y
            KeyEvent.KEYCODE_Z -> return KEY_Z

            KeyEvent.KEYCODE_F1 -> return KEY_F1
            KeyEvent.KEYCODE_F2 -> return KEY_F2
            KeyEvent.KEYCODE_F3 -> return KEY_F3
            KeyEvent.KEYCODE_F4 -> return KEY_F4
            KeyEvent.KEYCODE_F5 -> return KEY_F5
            KeyEvent.KEYCODE_F6 -> return KEY_F6
            KeyEvent.KEYCODE_F7 -> return KEY_F7
            KeyEvent.KEYCODE_F8 -> return KEY_F8
            KeyEvent.KEYCODE_F9 -> return KEY_F9
            KeyEvent.KEYCODE_F10 -> return KEY_F10
            KeyEvent.KEYCODE_F11 -> return KEY_F11
            KeyEvent.KEYCODE_F12 -> return KEY_F12

            KeyEvent.KEYCODE_ENTER -> return KEY_ENTER
            KeyEvent.KEYCODE_ESCAPE -> return KEY_ESCAPE
            KeyEvent.KEYCODE_DEL -> return KEY_DELETE
            KeyEvent.KEYCODE_TAB -> return KEY_TAB
            KeyEvent.KEYCODE_SPACE -> return KEY_SPACEBAR
            KeyEvent.KEYCODE_MINUS -> return KEY_MINUS_UNDERSCORE
            KeyEvent.KEYCODE_EQUALS -> return KEY_EQUAL_PLUS
            KeyEvent.KEYCODE_LEFT_BRACKET -> return KEY_OBRACKET_AND_OBRACE
            KeyEvent.KEYCODE_RIGHT_BRACKET -> return KEY_CBRACKET_AND_CBRACE
            KeyEvent.KEYCODE_BACKSLASH -> return KEY_BACKSLASH_VERTICAL_BAR
            KeyEvent.KEYCODE_SEMICOLON -> return KEY_SEMICOLON_COLON
            KeyEvent.KEYCODE_GRAVE -> return KEY_GRAVE_ACCENT_AND_TILDE
            KeyEvent.KEYCODE_COMMA -> return KEY_COMMA_AND_LESS
            KeyEvent.KEYCODE_PERIOD -> return KEY_DOT_GREATER
            KeyEvent.KEYCODE_SLASH -> return KEY_SLASH_QUESTION
            KeyEvent.KEYCODE_CAPS_LOCK -> return KEY_CAPS_LOCK
            KeyEvent.KEYCODE_APOSTROPHE -> return KEY_SINGLE_AND_DOUBLE_QUOTE

            KeyEvent.KEYCODE_SCROLL_LOCK -> return KEY_SCROLL_LOCK
            KeyEvent.KEYCODE_INSERT -> return KEY_INSERT
            KeyEvent.KEYCODE_HOME -> return KEY_HOME
            KeyEvent.KEYCODE_PAGE_UP -> return KEY_PAGEUP
            KeyEvent.KEYCODE_PAGE_DOWN -> return KEY_PAGEDOWN
            KeyEvent.KEYCODE_DPAD_DOWN -> return KEY_DOWNARROW
            KeyEvent.KEYCODE_DPAD_UP -> return KEY_UPARROW
            KeyEvent.KEYCODE_DPAD_LEFT -> return KEY_LEFTARROW
            KeyEvent.KEYCODE_DPAD_RIGHT -> return KEY_RIGHTARROW
            KeyEvent.KEYCODE_DPAD_CENTER -> return KEY_RETURN

            KeyEvent.KEYCODE_SHIFT_LEFT -> return KEY_LEFTSHIFT
            KeyEvent.KEYCODE_SHIFT_RIGHT -> return KEY_RIGHTSHIFT
            KeyEvent.KEYCODE_ALT_LEFT -> return KEY_LEFTALT
            KeyEvent.KEYCODE_ALT_RIGHT -> return KEY_RIGHTALT
            KeyEvent.KEYCODE_CTRL_LEFT -> return KEY_LEFTCONTROL
            KeyEvent.KEYCODE_CTRL_RIGHT -> return KEY_RIGHTCONTROL

            KeyEvent.KEYCODE_HELP -> return KEY_HELP
            KeyEvent.KEYCODE_MENU -> return KEY_MENU
            KeyEvent.KEYCODE_BUTTON_SELECT -> return KEY_SELECT
            KeyEvent.KEYCODE_MEDIA_STOP -> return KEY_STOP
            KeyEvent.KEYCODE_CUT -> return KEY_CUT
            KeyEvent.KEYCODE_COPY -> return KEY_COPY
            KeyEvent.KEYCODE_PASTE -> return KEY_PASTE
            KeyEvent.KEYCODE_VOLUME_MUTE -> return KEY_MUTE
            KeyEvent.KEYCODE_VOLUME_UP -> return KEY_VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> return KEY_VOLUME_DOWN

            //I believe these are reserved for the system.
//            KeyEvent.KEYCODE_POWER -> return KEY_SYSTEM_POWERDOWN
//            KeyEvent.KEYCODE_SLEEP -> return KEY_SYSTEM_SLEEP
//            KeyEvent.KEYCODE_WAKEUP -> return KEY_SYSTEM_WAKEUP

            KeyEvent.KEYCODE_NUMPAD_9 -> return KEY_F16

        }
        return null
    }
}