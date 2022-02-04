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
import java.util.*
import java.util.concurrent.TimeUnit

open class Sensors(context: Context, private val listener: VuzixSensorListener) : VuzixApi(context) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var threadSync = Object()
    private lateinit var connection: UsbDeviceConnection
    private lateinit var sensorUsbInterface: UsbInterface
    private lateinit var endpoint: UsbEndpoint
    @Volatile
    private var getData: Boolean = false
    private var smooth: Boolean = false
    private var MAX_RETRIES = 3
    private var usbReaderJob : Job? = null

    private val SENSOR_ACCELEROMETER_ID = 1   // kSENSOR_Accelerometer
    private val SENSOR_GYRO_ID = 2            // kSENSOR_Gyro
    private val SENSOR_MAGNETOMETER_ID = 3    // kSENSOR_Magnetometer
    private val SENSOR_ORIENTATION_ID = 4     // kSENSOR_DeviceOrientation

    private val SENSOR_STOP = 1 // kStop
    private val SENSOR_RUN = 2  // kRun

    private val  USB_DEVICE_CONFIG_HID_CLASS_CODE = (0x03U)

    /*! @brief Request code to get report of HID class. */
    private val USB_DEVICE_HID_REQUEST_GET_REPORT = (0x01U)
    private val USB_DEVICE_HID_REQUEST_GET_REPORT_TYPE_INPUT = (0x01U)
    private val USB_DEVICE_HID_REQUEST_GET_REPORT_TYPE_OUPUT = (0x02U)
    private val USB_DEVICE_HID_REQUEST_GET_REPORT_TYPE_FEATURE = (0x03U)
    /*! @brief Request code to get idle of HID class. */
    private val USB_DEVICE_HID_REQUEST_GET_IDLE = (0x02U)
    /*! @brief Request code to get protocol of HID class. */
    private val  USB_DEVICE_HID_REQUEST_GET_PROTOCOL = (0x03U)
    /*! @brief Request code to set report of HID class. */
    private val  USB_DEVICE_HID_REQUEST_SET_REPORT = (0x09U)
    /*! @brief Request code to set idle of HID class. */
    private val USB_DEVICE_HID_REQUEST_SET_IDLE = (0x0AU)
    /*! @brief Request code to set protocol of HID class. */
    private val USB_DEVICE_HID_REQUEST_SET_PROTOCOL = (0x0BU)
    private val USB_REQUEST_TYPE_DIR_OUT = (0U)
    private val USB_REQUEST_TYPE_DIR_IN = (0x80U)
    private val USB_REQUEST_TYPE_TYPE_CLASS = (0x20U)
    private val USB_REQUEST_TYPE_RECIPIENT_INTERFACE = (0x01U)

    protected override fun getUsbVendorId() : Int {
        return M400cConstants.HID_VID;
    }

    protected override fun getUsbProductId() : Int {
        return M400cConstants.HID_PID;
    }

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
        synchronized(threadSync) {
            usbDevice = getDevice()
            usbDevice?.let {
                sensorUsbInterface = it.getInterface(M400cConstants.HID_SENSOR)
                endpoint = sensorUsbInterface.getEndpoint(M400cConstants.HID_SENSOR_INBOUND)
                connection = usbManager.openDevice(usbDevice)
                if (!connection.claimInterface(sensorUsbInterface, true)) {
                    throw Exception("Failed to claim Sensor Interface")
                }
                connected = connection.setInterface(sensorUsbInterface)
            } ?: throw Exception("Compatible device is not connected")
        }
    }

    /**
     * Function used to close down the [UsbDeviceConnection].
     */
    override fun disconnect() {
        coroutineScope.launch {
            LogUtil.debug("disconnect")
            stopSensorStream();
            synchronized(threadSync) {
                try {
                    connection?.releaseInterface(sensorUsbInterface)
                    connection?.close()
                } catch (e: Exception) {
                    // Eat it
                }
                connected = false
                usbDevice = null
            }
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
        if(connection == null){
            throw Exception("Must call connect() before initializeSensors")
        }
        usbReaderJob = coroutineScope.launch {
            // Use a do-while-false so we can break when we get an error
            do {
                LogUtil.debug("Starting sensors")
                if(!initSensor(SENSOR_ACCELEROMETER_ID, true)) {
                    listener.onError(Exception("Accelerometer failed to initialize."))
                    break;
                }
                if(!initSensor(SENSOR_GYRO_ID, true)){
                    listener.onError(Exception("Gyrometer failed to initialize."))
                    break;
                }
                if(!initSensor(SENSOR_MAGNETOMETER_ID, true)){
                    listener.onError(Exception("Magnetometer failed to initialize."))
                    break
                }
                if(!initSensor(SENSOR_ORIENTATION_ID, true)) {
                    listener.onError(Exception("Orientation Sensor failed to initialize."))
                    break;
                }
                // Success!
                listener.onSensorInitialized()
                // We now repurpose this coroutine to pull the data from the USB
                runSensorStream();
            } while(false)
            LogUtil.debug("Sensors loop completed. Stopping")
            // Disable all the sensors, ignore errors in case the device was unplugged
            initSensor(SENSOR_ORIENTATION_ID, false)
            initSensor(SENSOR_MAGNETOMETER_ID, false)
            initSensor(SENSOR_GYRO_ID, false)
            initSensor(SENSOR_ACCELEROMETER_ID, false)
        }
    }

    /**
     * Common function to initialize a given sensor. Makes three attempts.
     */
    private suspend fun initSensor(sensor: Int, useSensor: Boolean ): Boolean {
        val action= if(useSensor)"Initializing" else "De-initializing"
        LogUtil.debug("$action sensor $sensor")
        delay(100) // Hack since some sensors don't start
        var count = 0
        val bytes = getSensorControlPacket(sensor, useSensor)
        while (count < MAX_RETRIES) {
            var xfered = connection.controlTransfer(
                (USB_REQUEST_TYPE_DIR_OUT or USB_REQUEST_TYPE_TYPE_CLASS or USB_REQUEST_TYPE_RECIPIENT_INTERFACE).toInt(),
                USB_DEVICE_HID_REQUEST_SET_REPORT.toInt(),
                sensor,
                sensorUsbInterface.id,
                bytes,
                bytes.size,
                TimeUnit.SECONDS.toMillis(1).toInt()
            )
            if( xfered >= 0 ) {
                // Read it back for sanity
                val incomingBytes = ByteArray(endpoint.maxPacketSize)
                xfered = connection.controlTransfer(
                    (USB_REQUEST_TYPE_DIR_IN or USB_REQUEST_TYPE_TYPE_CLASS or USB_REQUEST_TYPE_RECIPIENT_INTERFACE).toInt(),
                    USB_DEVICE_HID_REQUEST_GET_REPORT.toInt(),
                    sensor,
                    sensorUsbInterface.id,
                    incomingBytes,
                    incomingBytes.size,
                    TimeUnit.SECONDS.toMillis(1).toInt()
                )
                if (xfered >= 0) {
                    LogUtil.debug("$action Sensor: $sensor complete. Packet: ", incomingBytes, xfered)
                    return true;
                }
            }
            LogUtil.debug("$action Sensor: $sensor failed: $xfered")
            count++
            delay(500)
        }
        if(useSensor) {
            LogUtil.rel("Sensor: $sensor failed to initialize")
        }
        return false;
    }

    /**
     * Function used to begin consuming the sensor data stream.
     *
     * This is called immediately after notifying the listener of onSensorInitialized()
     */
    private suspend fun runSensorStream() {
        getData = true
        LogUtil.debug("Starting sensor stream")
        while (getData) {
            val bytes = ByteArray(endpoint.maxPacketSize)
            // Interval is the device specified value needed in between each read.
            delay(endpoint.interval.toLong())
            val read = connection.bulkTransfer(
                endpoint, bytes, bytes.size,
                TimeUnit.SECONDS.toMillis(1).toInt()
            )
            //LogUtil.debug("Sensor sent $read bytes: ", bytes, read)
            if (read > 0) {
                listener.onSensorChanged(createSensorEvent(bytes.take(read).toByteArray()))
            } else {
                listener.onError(Exception("USB sensor read failed $read closing interface"))
                break
            }
        }
        LogUtil.debug("Finished sensor stream")
    }

    /**
     * Function used to stop consuming the sensor data stream separate from
     * the use of the [disconnect] function.
     *
     * Note: This should be implemented publicly once we de-initialize the sensor to put the M400C
     *       back in an idle state. Until then, let clients disconnect.
     */
    suspend fun stopSensorStream() {
        getData = false
        usbReaderJob?.join()
    }

    /**
     * Function to take the raw sensor byte data and convert it into data appropriate
     * for each sensor.
     */
    private fun createSensorEvent(byteArray: ByteArray): VuzixSensorEvent {
        val mutableList: MutableList<Float> = mutableListOf()
        val reportId = byteArray[0]
        val sensorState = byteArray[1]
        val sensorEvent = byteArray[2]
        val deviceXData: Short = bytesToShort(byteArray[4], byteArray[3])
        val deviceYData: Short = bytesToShort(byteArray[6], byteArray[5])
        val deviceZData: Short = bytesToShort(byteArray[8], byteArray[7])

        return when (byteArray[0].toInt()) {
            SENSOR_ACCELEROMETER_ID -> {
                //LogUtil.debug("Accelerometer: ID=${reportId} state=${sensorState} event=${sensorEvent} X=${deviceXData},Y=${deviceYData},Z=${deviceZData}")
                // Device X+ is towards power button; Y+ is toward camera; Z+ towards nav buttons
                val xData: Short = (-deviceXData).toShort()
                val yData: Short = (-deviceZData).toShort()
                val zData: Short = (deviceYData).toShort()
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
                //LogUtil.debug("Gyro: ID=${reportId} state=${sensorState} event=${sensorEvent} X=${deviceXData},Y=${deviceYData},Z=${deviceZData}")
                // Device X+ is towards power button; Y+ is toward camera; Z+ towards nav buttons
                val xData: Short = (deviceXData).toShort()
                val yData: Short = (deviceZData).toShort()
                val zData: Short = (-deviceYData).toShort()
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
                val deviceAccuracy: Byte = byteArray[9]
                // Different from orientation! Device X+ is towards power button; Y+ is toward USB; Z+ towards bottom of hinge
                val xData: Short = (deviceXData).toShort()
                val yData: Short = (deviceZData).toShort()
                val zData: Short = (deviceYData).toShort()
                //LogUtil.debug("Magnetometer: ID=${reportId} state=${sensorState} event=${sensorEvent} X=${deviceXData},Y=${deviceYData},Z=${deviceZData}, Accuracy=${deviceAccuracy}")
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
                val deviceWData: Short = bytesToShort(byteArray[10], byteArray[9])
                //LogUtil.debug("Orientation: ID=${reportId} state=${sensorState} event=${sensorEvent} X=${deviceXData},W=${deviceYData},Z=${deviceZData},W=${deviceWData}")
                // todo: Re-map to a consistent axes as done in the simpler sensors
                mutableList.add(deviceXData / 1000f)
                mutableList.add(deviceYData / 1000f)
                mutableList.add(deviceZData / 1000f)
                mutableList.add(deviceWData / 1000f)
                VuzixSensorEvent(Sensor.TYPE_ROTATION_VECTOR, mutableList.toFloatArray())
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
    private fun getSensorControlPacket(sensorId: Int, run: Boolean, reportIntervalMs: Long = 4): ByteArray {
        return byteArrayOf(
            sensorId.toByte(), // 0 kReportID
            2, // 1 kConnectionType (ignored)
            ( if (run) SENSOR_RUN else SENSOR_STOP).toByte(), // 2 kReportingState
            2, // 3 kPowerState (ignored)
            2, // 4 kSensorState (ignored)
            (reportIntervalMs and 0xFF).toByte(), // 5 kReportInterval0
            ((reportIntervalMs shr 8) and 0xFF).toByte(), // 6 kReportInterval1
            ((reportIntervalMs shr 16) and 0xFF).toByte(), // 7 kReportInterval2
            ((reportIntervalMs shr 24) and 0xFF).toByte(), // 8 kReportInterval3
            0, // 9 kChangeSensitivity0 (ignored)
            0 // 10 kChangeSensitivity1 (ignored)
            //(0xFF).toByte(), // 11 (ignored)
            //0x1F, // 12 (ignored)
            //0, // 13 (ignored)
            //0 // 14 (ignored)
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