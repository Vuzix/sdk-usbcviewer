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

package com.vuzix.sdk.usbcviewer.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.vuzix.sdk.usbcviewer.M400cConstants
import com.vuzix.sdk.usbcviewer.VuzixApi
import com.vuzix.sdk.usbcviewer.utils.LogUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import java.util.*
import java.util.concurrent.TimeUnit

class Sensors(context: Context, private val listener: VuzixSensorListener) : VuzixApi(context) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var connection: UsbDeviceConnection
    private lateinit var sensorInterface: UsbInterface
    private lateinit var endpoint: UsbEndpoint
    @Volatile
    private var getData: Boolean = false
    private var smooth: Boolean = false
    private var MAX_RETRIES = 3

    // IDs for HID Sensors
    private val SENSOR_ACCELEROMETER_ID = 1
    private val SENSOR_GYRO_ID = 2
    private val SENSOR_MAGNETOMETER_ID = 3
    private val SENSOR_ORIENTATION_ID = 4

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
        usbDevice = getHidDevice(usbManager)
        usbDevice?.let {
            sensorInterface = it.getInterface(M400cConstants.HID_SENSOR)
            endpoint = sensorInterface.getEndpoint(M400cConstants.HID_SENSOR_INBOUND)
            connection = usbManager.openDevice(usbDevice)
            if (!connection.claimInterface(sensorInterface, true)) {
                throw Exception("Failed to claim Sensor Interface")
            }
            connected = connection.setInterface(sensorInterface)
        } ?: throw Exception("Hid Device is null")
    }

    /**
     * Function used to close down the [UsbDeviceConnection].
     */
    override fun disconnect() {
        LogUtil.debug("disconnect")
        stopSensorStream();
        try {
            connection.releaseInterface(sensorInterface)
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
            usbDevice = getHidDevice(usbManager)
            usbDevice != null
        }
    }

    /**
     * Function used to initialize all the sensors. Set up as a a cascading flow
     * which allows each sensor to have time to initialize before the next one
     * is attempted. If the condition for a successful initialization does not
     * occur, then [VuzixSensorListener.onError] is called. This is informative
     * in nature and will not stop the next initialization attempt.
     *
     * @throws Exception When a successful initialization does not occur for a
     * specific sensor.
     */
    @Throws(Exception::class)
    fun initializeSensors() {
        // Developer Note: This is set up this way because the controlTransfer()
        // function in UsbDeviceConnection is performed asynchronously and it has
        // been observed during initial development that the device doesn't like
        // it when you try to initialize more than one sensor at a time and will
        // typically end up not initializing all of the required sensors. By
        // structuring this to occur as part of a cascading Flow with flatMapConcat
        // it resolves the issue.
        coroutineScope.launch {
            if (isDeviceAvailable()) {
                initSensor(SENSOR_ACCELEROMETER_ID)
                    .flatMapConcat {
                        if (!it) {
                            listener.onError(Exception("Accelerometer failed to initialize."))
                        }
                        initSensor(SENSOR_GYRO_ID)
                    }
                    .flatMapConcat {
                        if (!it) {
                            listener.onError(Exception("Gyrometer failed to initialize."))
                        }
                        initSensor(SENSOR_MAGNETOMETER_ID)
                    }
                    .flatMapConcat {
                        if (!it) {
                            listener.onError(Exception("Magnetometer failed to initialize."))
                        }
                        initSensor(SENSOR_ORIENTATION_ID)
                    }
                    .collect {
                        if (!it) {
                            listener.onError(Exception("Orientation Sensor failed to initialize."))
                        }
                        listener.onSensorInitialized()
                        startSensorStream();
                    }
            } else {
                throw Exception("Hid Device is null")
            }
        }
    }

    /**
     * Common function to initialize a given sensor. Makes three attempts.
     * A successful initialization will allow "read" to come back as > -1,
     * which will then break the loop.
     */
    private fun initSensor(sensor: Int): Flow<Boolean> = flow {
        val bytes = getSensorControlPacket(sensor)
        val incomingBytes = ByteArray(endpoint.maxPacketSize)
        var count = 0
        while (count < MAX_RETRIES) {
            connection.controlTransfer(
                0x21,
                0x09,
                0x0300 or sensor,
                sensorInterface.id,
                bytes,
                bytes.size,
                1000
            )
            val read = connection.controlTransfer(
                0xA1,
                0x01,
                0x0300 or sensor,
                sensorInterface.id,
                incomingBytes,
                incomingBytes.size,
                1000
            )
            LogUtil.debug("Init Sensor: $sensor | Read Value: $read")
            if (read != -1) {
                break
            }
            count++
            delay(500)
        }
        emit(count != 3)
    }

    /**
     * Function used to begin consuming the sensor data stream.
     *
     * This is called immediately after notifying the listener of onSensorInitialized()
     */
    private fun startSensorStream() {
        getData = true
        coroutineScope.launch {
            while (getData) {
                val bytes = ByteArray(endpoint.maxPacketSize)
                val read = connection.bulkTransfer(
                    endpoint, bytes, bytes.size,
                    TimeUnit.SECONDS.toMillis(1).toInt()
                )
                if (read <= endpoint.maxPacketSize && read != -1) {
                    listener.onSensorChanged(createSensorEvent(bytes.take(read).toByteArray()))
                }
                // Interval is the device specified value needed inbetween each read.
                delay(endpoint.interval.toLong())
            }
            // TODO: De-initialize the sensor
        }
    }

    /**
     * Function used to stop consuming the sensor data stream separate from
     * the use of the [disconnect] function.
     *
     * TODO: This should be implemented publicly once we de-initialize the sensor to put the M400C
     *       back in an idle state. Until then, let clients disconnect.
     */
    private fun stopSensorStream() {
        getData = false
        // TODO: This should join the coroutine so we know data has stopped
    }

    /**
     * Function to take the raw sensor byte data and convert it into data appropriate
     * for each sensor.
     */
    private fun createSensorEvent(byteArray: ByteArray): VuzixSensorEvent {
        val mutableList: MutableList<Float> = mutableListOf()
        // Developer Note: This goes against the actual Y and Z axis as defined by the device because
        // it was originally designed for Windows and had different design parameters. We swap the
        // Y and Z values here to better align with Android standards.
        val xData: Short = bytesToShort(byteArray[4], byteArray[3])
        val yData: Short = bytesToShort(byteArray[8], byteArray[7])
        val zData: Short = bytesToShort(byteArray[6], byteArray[5])

        return when (byteArray[0].toInt()) {
            SENSOR_ACCELEROMETER_ID -> {
                val accel = floatArrayOf(
                    calculateAccelData(xData),
                    calculateAccelData(yData),
                    calculateAccelData(zData)
                )
                if (smooth) {
                    val accelAvg = floatArrayOf(0f, 0f, 0f)
                    smoothSensorData(accel, accelAvg, SENSOR_ACCELEROMETER_ID)
                    VuzixSensorEvent(Sensor.TYPE_ACCELEROMETER, accelAvg)
                } else {
                    VuzixSensorEvent(Sensor.TYPE_ACCELEROMETER, accel)
                }
            }
            SENSOR_GYRO_ID -> {
                mutableList.add(xData / 131f)
                mutableList.add(yData / 131f)
                mutableList.add(zData / 131f)
                if (smooth) {
                    val gyroAvg = floatArrayOf(0f, 0f, 0f)
                    smoothSensorData(mutableList.toFloatArray(), gyroAvg, SENSOR_GYRO_ID)
                    VuzixSensorEvent(Sensor.TYPE_GYROSCOPE, gyroAvg)
                } else {
                    VuzixSensorEvent(Sensor.TYPE_GYROSCOPE, mutableList.toFloatArray())
                }
            }
            SENSOR_MAGNETOMETER_ID -> {
                mutableList.add(xData / 1000f)
                mutableList.add(yData / 1000f)
                mutableList.add(zData / 1000f)
                if (smooth) {
                    val magAvg = floatArrayOf(0f, 0f, 0f)
                    smoothSensorData(
                        mutableList.toFloatArray(),
                        magAvg,
                        SENSOR_MAGNETOMETER_ID
                    )
                    VuzixSensorEvent(Sensor.TYPE_MAGNETIC_FIELD, magAvg)
                } else {
                    VuzixSensorEvent(Sensor.TYPE_MAGNETIC_FIELD, mutableList.toFloatArray())
                }
            }
            SENSOR_ORIENTATION_ID -> {
                val wData: Short = bytesToShort(byteArray[10], byteArray[9])
                mutableList.add(xData / 1000f)
                mutableList.add(yData / 1000f)
                mutableList.add(zData / 1000f)
                mutableList.add(wData / 1000f)
                VuzixSensorEvent(Sensor.TYPE_GAME_ROTATION_VECTOR, mutableList.toFloatArray())
            }
            else -> throw Exception("Unknown Sensor Type Detected: ${byteArray[0]}")
        }
    }

    /** Function to convert the raw accelerometer data into m/s2 */
    private fun calculateAccelData(value: Short): Float {
        // This includes a deconversion of the calculation performed on the data prior to putting it
        // in the byte array.
        val decon = value * 8192 / 100
        // Then we calculate the G value based on the sensitivity, which is set at +/-4, or 0.1221
        val gValue = decon * .1221 / 1000
        // Finally, we return the G Value multiplied by 9.8m/s2
        return (gValue * 9.8).toFloat()
    }

    /** Function used to generate the ByteArray needed to initialize a given sensor */
    private fun getSensorControlPacket(sensorId: Int, reportInterval: Long = 4): ByteArray {
        return byteArrayOf(
            sensorId.toByte(), // 0
            2, // 1
            2, // 2
            2, // 3
            2, // 4
            (reportInterval and 0xFF).toByte(), // 5
            ((reportInterval shr 8) and 0xFF).toByte(), // 6
            ((reportInterval shr 16) and 0xFF).toByte(), // 7
            ((reportInterval shr 24) and 0xFF).toByte(), // 8
            0, // 9
            0, // 10
            (0xFF).toByte(), // 11
            0x1F, // 12
            0, // 13
            0 // 14
        )
    }

    private val accBuffer = LinkedList<FloatArray>().also {
        for (i in 0 until 20) {
            it.add(floatArrayOf(0f, 0f, 0f))
        }
    }

    private val gyroBuffer = LinkedList<FloatArray>().also {
        for (i in 0 until 20) {
            it.add(floatArrayOf(0f, 0f, 0f))
        }
    }

    private val magBuffer = LinkedList<FloatArray>().also {
        for (i in 0 until 20) {
            it.add(floatArrayOf(0f, 0f, 0f))
        }
    }

    /** Function to reduce the volatility of the sensor data */
    private fun smoothSensorData(data: FloatArray, dataAvg: FloatArray, sensorType: Int) {
        val a = floatArrayOf(0f, 0f, 0f)
        val dataIter: Iterator<FloatArray>
        val size: Int = when (sensorType) {
            SENSOR_ACCELEROMETER_ID -> {
                accBuffer.pollFirst()
                accBuffer.addLast(data)
                dataIter = accBuffer.iterator()
                accBuffer.size
            }
            SENSOR_MAGNETOMETER_ID -> {
                magBuffer.pollFirst()
                magBuffer.addLast(data)
                dataIter = magBuffer.iterator()
                magBuffer.size
            }
            SENSOR_GYRO_ID -> {
                gyroBuffer.pollFirst()
                gyroBuffer.addLast(data)
                dataIter = gyroBuffer.iterator()
                gyroBuffer.size
            }
            else -> return
        }
        while (dataIter.hasNext()) {
            val tmp = dataIter.next()
            a[0] += tmp[0]
            a[1] += tmp[1]
            a[2] += tmp[2]
        }
        dataAvg[0] = a[0] / size
        dataAvg[1] = a[1] / size
        dataAvg[2] = a[2] / size
    }

    /** Function to combine the raw sensor data for an axis into the proper value */
    private fun bytesToShort(msb: Byte, lsb: Byte): Short {
        return ((msb.toInt() shl 8) or (lsb.toInt() and 0xFF)).toShort()
    }
}