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

import android.hardware.Sensor
import android.hardware.usb.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.vuzix.sdk.usbcviewer.sensors.Quaternion
import com.vuzix.sdk.usbcviewer.sensors.VuzixSensorEvent
import com.vuzix.sdk.usbcviewer.sensors.VuzixSensorListener
import com.vuzix.sdk.usbcviewer.utils.LogUtil
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.TimeUnit


class SensorInterface(usbManager: UsbManager, device: UsbDevice, usbInterface: UsbInterface) : USBCDeviceInterface(usbManager,
    device, usbInterface) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var isSensorDataStreaming: Boolean = false
    private var smooth: Boolean = false
    private var MAX_RETRIES = 3
    private var usbReaderJob: Job? = null

    private val sensorHandlerThread = HandlerThread("com.vuzix.sensor_interface")

    private lateinit var sensorHandler: Handler

    /* Enumerated controls for M400C */
    private val SENSOR_STOP = 1 // kStop
    private val SENSOR_RUN = 2  // kRun

    private var isAccelerometerReporting = false
    private var isGyroscopeReporting = false
    private var isMagnetometerReporting = false
    private var isOrientationReporting = false

    private var sensorInitTrackingList = arrayListOf<Int>()

    private var listeners: ArrayList<VuzixSensorListener> = ArrayList()


    /*
    Register a Vuzix Sensor Listener for onSensorChange, onError and onInitialized
    A register listener must implemented before a sensor can start updating.
     */
    @Throws(Exception::class)
    fun registerListener(listener: VuzixSensorListener) {
        listeners.add(listener)
    }

    // Removes the listener
    // If no more listeners, than sensors will be turned off
    fun unregisterListener(listener: VuzixSensorListener) {
        listeners.remove(listener)
        // stop reporting if no one is listening.
        if (listeners.size == 0) {
            // turn off everybody
            if (isAccelerometerReporting) {
                stopUpdatingSensor(SensorType.ACCELEROMETER)
            }
            if (isOrientationReporting) {
                stopUpdatingSensor(SensorType.ORIENTATION)
            }
            if (isMagnetometerReporting) {
                stopUpdatingSensor(SensorType.MAGNETOMETER)
            }
            if (isGyroscopeReporting) {
                stopUpdatingSensor(SensorType.GYRO)
            }
        }
    }

    /**
    Starts updating the sensor.
    Param: reporting rate is in milliseconds and is at best.
    Listen for updates on VuzixSensorListener.onSensorChanged

    @param sensor the integer sensor type from android.hardware.Sensor of
        TYPE_MAGNETIC_FIELD, TYPE_ACCELEROMETER, TYPE_GYROSCOPE
    @param reportingRate maximum rate for receiving updates in milliseconds (optional) default: 4ms
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun startUpdatingSensor(sensor: Int, reportingRate: Long = 4) {
        // TODO Add TYPE_ROTATION_VECTOR to javadocs once we implement rotateQuaternionAxes()
        startUpdatingSensor(androidToSensorType(sensor), reportingRate)
    }

    private fun startUpdatingSensor(sensor: SensorType, reportingRate: Long) {
        if (listeners.size == 0) {
            throw Exception("No listeners. Must have at least one listener: call first addListener()")
        }


        // start thread
        if (!sensorHandlerThread.isAlive) {
            sensorHandlerThread.start()
            sensorHandler = Handler(sensorHandlerThread.looper)
        }

        val runnable = java.lang.Runnable {

                LogUtil.debug("startUpdatingSensor called, now calling setSensorState on ${sensor.value}")
                val status = setSensorState(sensor.value, true, reportingRate)
                if (!status) {
                    for (listener in listeners) {
                        listener.onError(Exception("${sensor.toString()} failed to initialize."))
                    }
                    stopUpdatingSensor(sensor)
                } else {
                    when (sensor) {
                        SensorType.GYRO -> isGyroscopeReporting = true
                        SensorType.ACCELEROMETER -> isAccelerometerReporting = true
                        SensorType.ORIENTATION -> isOrientationReporting = true
                        SensorType.MAGNETOMETER -> isMagnetometerReporting = true
                    }

                    for (listener in listeners) {
                        listener.onSensorInitialized()
                    }


                }
            }
        sensorHandler.post(runnable)

        if (!isSensorDataStreaming) {
            // We now repurpose this coroutine to pull the data from the USB
            isSensorDataStreaming = true
            usbReaderJob = coroutineScope.launch {
                runSensorStream()
            }
        }
    }

    /*
    Stops updating the sensor
     */
    fun stopUpdatingSensor(sensor: Int) {
        stopUpdatingSensor(androidToSensorType(sensor))
    }

    private fun stopUpdatingSensor(sensor: SensorType) {
        coroutineScope.launch {
            val status = setSensorState(sensor.value, false, 4)
            // check to see if we are streaming and cancel if nothing is listening.
            if (status) {
                when (sensor) {
                    SensorType.GYRO -> isGyroscopeReporting = false
                    SensorType.ACCELEROMETER -> isAccelerometerReporting = false
                    SensorType.ORIENTATION -> isOrientationReporting = false
                    SensorType.MAGNETOMETER -> isMagnetometerReporting = false
                }
            }

            if (!isGyroscopeReporting && !isAccelerometerReporting && !isMagnetometerReporting && !isOrientationReporting) {
                stopSensorStream()
            }
        }
    }

    /**
     * Common function to initialize or stop a given sensor. Makes three attempts.
     */
    private fun setSensorState(sensor: Int, useSensor: Boolean, reportRate: Long): Boolean {
        if(Looper.getMainLooper().thread == Thread.currentThread()) {
            LogUtil.debug("setSensorState should NOT be called on main thread!")
            return false
        }
        val action = if (useSensor) "Initializing" else "De-initializing"
        val sensorCurrentlyRunning = sensorInitTrackingList.contains(sensor)
        if (useSensor == sensorCurrentlyRunning) {
            // Nothing to do. We're stopping a sensor we never started, or we're starting one again
            LogUtil.debug("$action not required for $sensor")
            return true
        }
        LogUtil.debug("$action sensor $sensor")
        val (lastByteToReadBack, requestBytes) = getSensorControlPacket(sensor, useSensor, reportRate)
        for (count in 1..MAX_RETRIES) {
            val wValue = (0x03U shl (8) or sensor.toUInt()).toInt()
            var xfered = setHidReport(wValue, requestBytes)
            if (xfered >= 0) {
                // Read it back for sanity
                val incomingBytes = getHidReport(wValue)
                if (incomingBytes != null) {
                    val readBackOk = (requestBytes.copyOfRange(0, lastByteToReadBack)
                        .contentEquals(incomingBytes.copyOfRange(0, lastByteToReadBack)))
                    if (readBackOk) {
                        if (useSensor) {
                            sensorInitTrackingList.add(sensor)
                        } else {
                            sensorInitTrackingList.remove(sensor)
                        }
                        return true
                    }
                    LogUtil.rel("Read-back verification failure sensor $sensor")
                    LogUtil.debug(
                        "$action Sensor: $sensor requested. Packet: ",
                        requestBytes,
                        requestBytes.size
                    )
                    LogUtil.debug(
                        "$action Sensor: $sensor completed. Packet: ",
                        incomingBytes,
                        xfered
                    )
                }
            } else {
                LogUtil.debug("$action Sensor: $sensor failed: $xfered")
            }
            //delay(500)
            Thread.sleep(500)
        }
        if (useSensor) {
            LogUtil.rel("Sensor: $sensor failed to initialize")
        }
        return false
    }

    /**
     * Function used to begin consuming the sensor data stream.
     *
     * This is called immediately after notifying the listener of onSensorInitialized()
     */
    private fun runSensorStream() {
        isSensorDataStreaming = true
        LogUtil.debug("Starting sensor stream")
        while (isSensorDataStreaming && inEndpoint != null) {
            val bytes = ByteArray(inEndpoint.maxPacketSize)
            // Interval is the device specified value needed in between each read.
            val read = connection.bulkTransfer(
                inEndpoint, bytes, bytes.size,
                TimeUnit.SECONDS.toMillis(1).toInt()
            )
            // LogUtil.debug("Sensor sent $read bytes: ", bytes, read)
            if (read > 0) {
                for (listener in listeners) {
                    val event = createSensorEvent(bytes.take(read).toByteArray())
                    MainScope().launch {
                        listener.onSensorChanged(event)
                    }
                }
            } else {
                LogUtil.debug("Empty Read packet from sensor stream")
                for (listener in listeners) {
                    MainScope().launch {
                        listener.onError(Exception("USB sensor read failed $read closing interface"))
                    }
                }
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
    private fun stopSensorStream() {
        isSensorDataStreaming = false
        usbReaderJob?.cancel()
    }

    /**
     * Function to take the raw sensor byte data and convert it into data appropriate
     * for each sensor.
     */
    private fun createSensorEvent(byteArray: ByteArray): VuzixSensorEvent {
        val reportId = byteArray[0]
        //val sensorState = byteArray[1] // Used for debug purposes
        //val sensorEvent = byteArray[2] // Used for debug purposes
        val deviceXData: Short = bytesToShort(byteArray[4], byteArray[3])
        val deviceYData: Short = bytesToShort(byteArray[6], byteArray[5])
        val deviceZData: Short = bytesToShort(byteArray[8], byteArray[7])

        val sensorType = SensorType.fromInt(reportId.toInt())
            ?: throw Exception("Unknown Sensor Type Detected: ${byteArray[0]}")

        return when (sensorType) {
            SensorType.ACCELEROMETER -> {
                // LogUtil.debug("Accelerometer: ID=${reportId} state=${sensorState} event=${sensorEvent} X=${deviceXData},Y=${deviceYData},Z=${deviceZData}")
                // Device X+ is towards power button; Y+ is toward camera; Z+ towards nav buttons
                val accel = floatArrayOf(
                    calculateAccelData((-deviceXData).toShort()),
                    calculateAccelData(deviceZData),
                    calculateAccelData(deviceYData)
                )
                if (smooth) {
                    val accelAvg = floatArrayOf(0f, 0f, 0f)
                    smoothSensorData(accel, accelAvg, SensorType.ACCELEROMETER)
                    VuzixSensorEvent(sensorTypeToAndroid(sensorType), accelAvg)
                } else {
                    VuzixSensorEvent(sensorTypeToAndroid(sensorType), accel)
                }
            }
            SensorType.GYRO -> {
                // LogUtil.debug("Gyro: ID=${reportId} state=${sensorState} event=${sensorEvent} X=${deviceXData},Y=${deviceYData},Z=${deviceZData}")
                // Device X+ is towards power button; Y+ is toward camera; Z+ towards nav buttons
                val gyroData = floatArrayOf(
                    calculateGyroData((deviceXData)),
                    calculateGyroData((-deviceZData).toShort()),
                    calculateGyroData((deviceYData))
                )
                if (smooth) {
                    val gyroAvg = floatArrayOf(0f, 0f, 0f)
                    smoothSensorData(gyroData, gyroAvg, SensorType.GYRO)
                    VuzixSensorEvent( sensorTypeToAndroid(sensorType), gyroAvg)
                } else {
                    VuzixSensorEvent(sensorTypeToAndroid(sensorType), gyroData)
                }
            }
            SensorType.MAGNETOMETER -> {
                // val deviceAccuracy: Byte = byteArray[9] // Used for debug purposes
                // LogUtil.debug("Magnetometer: ID=${reportId} state=${sensorState} event=${sensorEvent}" +
                // " X=${deviceXData},Y=${deviceYData},Z=${deviceZData}, Accuracy=${deviceAccuracy}")
                // Different from orientation! Device X+ is towards power button; Y+ is toward USB; Z+ towards bottom of hinge
                val magnetometerData = floatArrayOf(
                    calculateMagnetometerData((-deviceXData).toShort()),
                    calculateMagnetometerData((-deviceZData).toShort()),
                    calculateMagnetometerData((deviceYData).toShort())
                )
                if (smooth) {
                    val magAvg = floatArrayOf(0f, 0f, 0f)
                    smoothSensorData(magnetometerData, magAvg, SensorType.MAGNETOMETER)
                    VuzixSensorEvent(sensorTypeToAndroid(sensorType), magAvg)
                } else {
                    VuzixSensorEvent(sensorTypeToAndroid(sensorType), magnetometerData)
                }
            }
            SensorType.ORIENTATION -> {
                // https://usb.org/sites/default/files/hut1_2.pdf defines the order as X,Y,Z,W
                val deviceWData: Short = bytesToShort(byteArray[10], byteArray[9])
                val updatedQuaternion = rotateQuaternionAxes( calculateRotationData(deviceXData),
                    calculateRotationData(deviceYData),
                    calculateRotationData(deviceZData),
                    calculateRotationData(deviceWData) )
                VuzixSensorEvent(sensorTypeToAndroid(sensorType), updatedQuaternion)
            }
        }
    }

    /** Function to convert the raw accelerometer data into m/s2 */
    private fun calculateAccelData(value: Short): Float {
        // This is described in the HID report under HID_USAGE_SENSOR_TYPE_MOTION_ACCELEROMETER_3D which
        // contains HID_USAGE_SENSOR_STATE for HID_USAGE_SENSOR_DATA_MOTION_ACCELERATION_X_AXIS,
        // HID_USAGE_SENSOR_DATA_MOTION_ACCELERATION_Y_AXIS, and
        // HID_USAGE_SENSOR_DATA_MOTION_ACCELERATION_Z_AXIS which all contain HID_UNIT_EXPONENT(0x0E)
        // https://usb.org/sites/default/files/hut1_2.pdf defines "Exponent E Sel 0.01".
        //
        // The sensor gives gravity data with 2 decimals of precision. So we divide by 100.
        // HID spec specifies the default unit of measure is G’s
        //
        // Android wants m/S^2 so we multiply by 9.8
        return ((value / 100.0) * 9.8).toFloat()
    }

    private fun calculateGyroData(value: Short): Float {
        // This is described in the HID report under HID_USAGE_SENSOR_TYPE_MOTION_GYROMETER_3D which
        // contains HID_USAGE_SENSOR_STATE for HID_USAGE_SENSOR_DATA_MOTION_ANGULAR_VELOCITY_X_AXIS,
        // HID_USAGE_SENSOR_DATA_MOTION_ANGULAR_VELOCITY_Y_AXIS, and
        // HID_USAGE_SENSOR_DATA_MOTION_ANGULAR_VELOCITY_z_AXIS which all contain HID_UNIT_EXPONENT(0x0E)
        // https://usb.org/sites/default/files/hut1_2.pdf defines "Exponent E Sel 0.01"
        //
        // The sensor gives angular velocity with 2 decimals of precision,o we divide by 100.
        // HID spec specifies the default unit is degrees per second.
        //
        // Android wants radians per second, so convert degrees to radians.
        return ((value / 100.0) * Math.PI / 180.0).toFloat()
    }

    private fun calculateMagnetometerData(value: Short): Float {
        // This is described in the HID report under HID_USAGE_SENSOR_TYPE_ORIENTATION_COMPASS_3D which
        // contains HID_USAGE_SENSOR_STATE for HID_USAGE_SENSOR_DATA_ORIENTATION_MAGNETIC_FLUX_X_AXIS,
        // HID_USAGE_SENSOR_DATA_ORIENTATION_MAGNETIC_FLUX_Y_AXIS, and
        // HID_USAGE_SENSOR_DATA_ORIENTATION_MAGNETIC_FLUX_Z_AXIS which all contain HID_UNIT_EXPONENT(0x0D)
        // https://usb.org/sites/default/files/hut1_2.pdf defines "Exponent D Sel 0.001"
        //
        // The sensor gives magnetic flux in 3 decimals of precision, so we divide by 1000
        // Hid spec specifies the default unit of measure is milligauss, Android wants micro-Tesla (µT).
        // (10mG = 1µT) so divide by another 10. (Total division by 10,000)
        return (value / 10000.0).toFloat()
    }

    private fun calculateRotationData(value: Short): Float {
        // This is described in the HID report under HID_USAGE_SENSOR_TYPE_ORIENTATION_DEVICE_ORIENTATION which
        // contains HID_USAGE_SENSOR_STATE for HID_USAGE_SENSOR_DATA_ORIENTATION_QUATERNION which
        // contains HID_UNIT_EXPONENT(0x0D)
        // https://usb.org/sites/default/files/hut1_2.pdf defines "Exponent D Sel 0.001"
        //
        // The sensor gives a quaternionin 3 decimals of precision, so we divide by 1000
        // Hid spec specifies it is "A matrix of 4 values (x, y, z and w, all ranging in value from -1.0 to
        //1.0) that represent rotation in space about a unit vector. No units
        //are specified and scaling is by the Unit Exponent usage."
        return (value / 1000.0).toFloat()
    }

    private fun rotateQuaternionAxes(x_val: Float, y_val: Float, z_val: Float, w_val: Float) : FloatArray {
        // https://stackoverflow.com/questions/4436764/rotating-a-quaternion-on-1-axis
        // Device X+ is towards power button; Y+ is toward camera; Z+ towards nav buttons
        // So rotate the reported data 90 degrees around X and the axes move appropriately
        val sensorQuaternion: Quaternion = Quaternion(x_val, y_val, z_val, w_val)
        val manipulationQuaternion = Quaternion.axisAngle(-1.0f, 0.0f, 0.0f, 90.0f)
        // TODO When we convert this data back using SensorManager.getOrientation we see the data
        // is mirrored / reflected on the X axis, so North and South are reversed, but East and West
        // are correct
        val axisRemappedData = Quaternion.multiply(sensorQuaternion, manipulationQuaternion)
        val rotationData = floatArrayOf(
            axisRemappedData.x,
            axisRemappedData.y,
            axisRemappedData.z,
            axisRemappedData.w
        )
        //LogUtil.debug("Orientation Orig: $sensorQuaternion Rotated: $axisRemappedData")
        return rotationData
    }

    /** Function used to generate the ByteArray needed to initialize a given sensor */
    private fun getSensorControlPacket(
        sensorId: Int,
        run: Boolean,
        reportIntervalMs: Long = 4
    ): Pair<Int, ByteArray> {
        val LAST_BYTE_FOR_COMPARISON = 8
        return Pair(
            LAST_BYTE_FOR_COMPARISON, byteArrayOf(
                sensorId.toByte(), // 0 kReportID
                2, // 1 kConnectionType (ignored)
                (if (run) SENSOR_RUN else SENSOR_STOP).toByte(), // 2 kReportingState
                2, // 3 kPowerState (ignored)
                2, // 4 kSensorState (ignored)
                (reportIntervalMs and 0xFF).toByte(), // 5 kReportInterval0
                ((reportIntervalMs shr 8) and 0xFF).toByte(), // 6 kReportInterval1
                ((reportIntervalMs shr 16) and 0xFF).toByte(), // 7 kReportInterval2
                ((reportIntervalMs shr 24) and 0xFF).toByte(), // 8 kReportInterval3
                // the LAST_BYTE_FOR_COMPARISON is immediately above here
                0, // 9 kChangeSensitivity0 (ignored)
                0, // 10 kChangeSensitivity1 (ignored)
                (0xFF).toByte(), // 11 (ignored)
                0x1F, // 12 (ignored)
                0, // 13 (ignored)
                0 // 14 (ignored)
            )
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
    private fun smoothSensorData(data: FloatArray, dataAvg: FloatArray, sensorType: SensorType) {
        val a = floatArrayOf(0f, 0f, 0f)
        val dataIter: Iterator<FloatArray>
        val size: Int = when (sensorType) {
            SensorType.ACCELEROMETER -> {
                accBuffer.pollFirst()
                accBuffer.addLast(data)
                dataIter = accBuffer.iterator()
                accBuffer.size
            }
            SensorType.MAGNETOMETER -> {
                magBuffer.pollFirst()
                magBuffer.addLast(data)
                dataIter = magBuffer.iterator()
                magBuffer.size
            }
            SensorType.GYRO -> {
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

    // This is an internal enumeration that must match the M400C USB interface.
    // It is not how Android devices will reference the sensors, so we keep private.
    // Externally we use android.hardware.Sensor values
    private enum class SensorType(val value: Int) {
        ACCELEROMETER(1),   // kSENSOR_Accelerometer, 1
        GYRO(2),            // kSENSOR_Gyro, 2
        MAGNETOMETER(3),    // kSENSOR_Magnetometer, 3
        ORIENTATION(4);     // kSENSOR_DeviceOrientation, 4

        companion object {
            fun fromInt(value: Int) = values().firstOrNull { it.value == value }
        }
    }

    private fun androidToSensorType(sensorAndroidInt : Int) : SensorType {
        return when(sensorAndroidInt) {
            Sensor.TYPE_MAGNETIC_FIELD -> SensorType.MAGNETOMETER
            Sensor.TYPE_ACCELEROMETER -> SensorType.ACCELEROMETER
            Sensor.TYPE_GYROSCOPE -> SensorType.GYRO
            // TODO Add TYPE_ROTATION_VECTOR support once we fix rotateQuaternionAxes()
            //Sensor.TYPE_ROTATION_VECTOR -> SensorType.ORIENTATION
            else -> throw Exception("Unsupported sensor requested: $sensorAndroidInt")
        }
    }

    private fun sensorTypeToAndroid(sensorType : SensorType) : Int {
        return when(sensorType) {
            SensorType.MAGNETOMETER -> Sensor.TYPE_MAGNETIC_FIELD
            SensorType.ACCELEROMETER -> Sensor.TYPE_ACCELEROMETER
            SensorType.GYRO -> Sensor.TYPE_GYROSCOPE
            SensorType.ORIENTATION -> Sensor.TYPE_ROTATION_VECTOR
        }
    }
}
