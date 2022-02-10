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
import com.vuzix.sdk.usbcviewer.BuildConfig
import com.vuzix.sdk.usbcviewer.M400cConstants
import com.vuzix.sdk.usbcviewer.VuzixApi
import com.vuzix.sdk.usbcviewer.utils.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.concurrent.TimeUnit

class Sensors(context: Context, private val listener: VuzixSensorListener) : VuzixApi(context) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var threadSync = Object()
    private lateinit var connection: UsbDeviceConnection
    private lateinit var sensorUsbInterface: UsbInterface
    private lateinit var endpoint: UsbEndpoint

    @Volatile
    private var getData: Boolean = false
    private var smooth: Boolean = false
    private var MAX_RETRIES = 3
    private var usbReaderJob: Job? = null

    /* Definitions for M400C as reported in HID report */
    private val SENSOR_ACCELEROMETER_ID = 1   // kSENSOR_Accelerometer
    private val SENSOR_GYRO_ID = 2            // kSENSOR_Gyro
    private val SENSOR_MAGNETOMETER_ID = 3    // kSENSOR_Magnetometer
    private val SENSOR_ORIENTATION_ID = 4     // kSENSOR_DeviceOrientation

    private var useAccelerometer = false
    private var useGyroscope = false
    private var useMagnetometer = false
    private var useOrientation = false

    private var sensorInitTrackingList = arrayListOf<Int>()

    /* Enumerated controls for M400C */
    private val SENSOR_STOP = 1 // kStop
    private val SENSOR_RUN = 2  // kRun

    /* USB HID definitions (not specific to M400C) */
    private val USB_DEVICE_HID_REQUEST_GET_REPORT = (0x01U)
    private val USB_DEVICE_HID_REQUEST_SET_REPORT = (0x09U)

    private val USB_REQUEST_TYPE_DIR_OUT = (0U)
    private val USB_REQUEST_TYPE_DIR_IN = (0x80U)
    private val USB_REQUEST_TYPE_TYPE_STANDARD = (0U)
    private val USB_REQUEST_TYPE_TYPE_CLASS = (0x20U)
    private val USB_REQUEST_TYPE_RECIPIENT_INTERFACE = (0x01U)

    private val USB_REQUEST_STANDARD_SET_FEATURE = (0x03U)
    private val USB_REQUEST_STANDARD_GET_DESCRIPTOR = (0x06U)
    private val USB_DESCRIPTOR_TYPE_CONFIGURE = (0x02U)
    private val USB_DESCRIPTOR_TYPE_HID_REPORT = (0x22U)

    override fun getUsbVendorId(): Int {
        return M400cConstants.HID_VID
    }

    override fun getUsbProductId(): Int {
        return M400cConstants.HID_PID
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
            usbDevice?.let { device ->
                sensorUsbInterface = device.getInterface(M400cConstants.HID_SENSOR)
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
            stopSensorStream()
            synchronized(threadSync) {
                try {
                    connection.releaseInterface(sensorUsbInterface)
                    connection.close()
                    LogUtil.rel("Sensors disconnected")
                } catch (e: Exception) {
                    // Eat it
                }
                connected = false
                usbDevice = null
                usbReaderJob?.cancel()
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
    fun initializeSensors(
        accelerometer: Boolean,
        gyroscope: Boolean,
        magnetometer: Boolean,
        orientation: Boolean
    ) {
        if (usbDevice == null || !connected) {
            throw Exception("Must call connect() before initializeSensors")
        }
        if (!(accelerometer or gyroscope or magnetometer or orientation)) {
            throw Exception("No sensors selected")
        }
        useAccelerometer = accelerometer
        useGyroscope = gyroscope
        useMagnetometer = magnetometer
        useOrientation = orientation

        usbReaderJob = coroutineScope.launch {
            // Use a do-while-false so we can break when we get an error
            do {
                LogUtil.debug("Starting sensors")
                if (BuildConfig.DEBUG) {
                    if (!readDeviceConfiguration()) {
                        listener.onError(Exception("HID read device configuration failed."))
                        break
                    }
                    if (!readHidReport()) {
                        listener.onError(Exception("HID device descriptor failed."))
                        break
                    }
                }
                if (useAccelerometer && !setSensorState(SENSOR_ACCELEROMETER_ID, true)) {
                    listener.onError(Exception("Accelerometer failed to initialize."))
                    break
                }
                if (useGyroscope && !setSensorState(SENSOR_GYRO_ID, true)) {
                    listener.onError(Exception("Gyrometer failed to initialize."))
                    break
                }
                if (useMagnetometer && !setSensorState(SENSOR_MAGNETOMETER_ID, true)) {
                    listener.onError(Exception("Magnetometer failed to initialize."))
                    break
                }
                if (useOrientation && !setSensorState(SENSOR_ORIENTATION_ID, true)) {
                    listener.onError(Exception("Orientation Sensor failed to initialize."))
                    break
                }
                // Success!
                listener.onSensorInitialized()

                // We now repurpose this coroutine to pull the data from the USB
                runSensorStream()

            } while (false)
            LogUtil.debug("Sensors loop completed. Stopping")
            // Disable all the sensors, ignore errors in case the device was unplugged
            setSensorState(SENSOR_ORIENTATION_ID, false)
            setSensorState(SENSOR_MAGNETOMETER_ID, false)
            setSensorState(SENSOR_GYRO_ID, false)
            setSensorState(SENSOR_ACCELEROMETER_ID, false)
        }
    }

    private fun readDeviceConfiguration(): Boolean {
        val incomingBytes = ByteArray(1024) // Plenty of space
        val requestType = (USB_REQUEST_TYPE_DIR_IN or
                USB_REQUEST_TYPE_TYPE_STANDARD or
                USB_REQUEST_TYPE_RECIPIENT_INTERFACE).toInt()
        val request = USB_REQUEST_STANDARD_GET_DESCRIPTOR.toInt()
        val requestValue = ((USB_DESCRIPTOR_TYPE_CONFIGURE shl (8))).toInt()
        val requestIndex = sensorUsbInterface.id
        val timeoutMs = TimeUnit.SECONDS.toMillis(1).toInt()

        for (attemptNum in 1..MAX_RETRIES) {
            val bytesRead = connection.controlTransfer(
                requestType,
                request,
                requestValue,
                requestIndex,
                incomingBytes,
                incomingBytes.size,
                timeoutMs
            )
            if (bytesRead == incomingBytes.size) {
                LogUtil.rel("WARNING: HID device configuration truncated at $bytesRead")
            }
            if (bytesRead > 0) {
                LogUtil.debug(
                    "HID device configuration for request: " +
                            "0x${requestType.toString(16)} ${request.toString(16)} " +
                            "${requestValue.toString(16)} ${requestIndex.toString(16)} " +
                            "returned size $bytesRead of ${incomingBytes.size} : ",
                    incomingBytes,
                    bytesRead
                )
                return true
            } else {
                LogUtil.rel(
                    "HID device configuration failed $bytesRead. " +
                            "Request: 0x${requestType.toString(16)}" +
                            " ${request.toString(16)} " +
                            "${requestValue.toString(16)} ${requestIndex.toString(16)}"
                )
            }
        }
        return false
    }

    private fun readHidReport(): Boolean {
        val incomingBytes = ByteArray(4 * 1024) // Plenty of space
        val requestType =
            (USB_REQUEST_TYPE_DIR_IN or USB_REQUEST_TYPE_TYPE_STANDARD or USB_REQUEST_TYPE_RECIPIENT_INTERFACE).toInt()
        val request = USB_REQUEST_STANDARD_GET_DESCRIPTOR.toInt()
        val requestValue = ((USB_DESCRIPTOR_TYPE_HID_REPORT shl (8))).toInt()
        val requestIndex = sensorUsbInterface.id
        val timeoutMs = TimeUnit.SECONDS.toMillis(1).toInt()
        for (attemptNum in 1..MAX_RETRIES) {
            val bytesRead = connection.controlTransfer(
                requestType,
                request,
                requestValue,
                requestIndex,
                incomingBytes,
                incomingBytes.size,
                timeoutMs
            )
            if (bytesRead == incomingBytes.size) {
                LogUtil.rel("WARNING: HID report for sensor: truncated at $bytesRead")
            }
            if (bytesRead > 0) {
                // Decode the full output this with https://eleccelerator.com/usbdescreqparser/
                LogUtil.debug(
                    "HID report for request: 0x${requestType.toString(16)} ${request.toString(16)}" +
                            " ${requestValue.toString(16)} ${requestIndex.toString(16)} returned size $bytesRead" +
                            " of ${incomingBytes.size} : ", incomingBytes, bytesRead
                )
                return true
            } else {
                LogUtil.rel(
                    "HID report failed $bytesRead. Request: 0x${requestType.toString(16)} " +
                            request.toString(16) +
                            " ${requestValue.toString(16)} " +
                            requestIndex.toString(16)
                )
            }
        }
        return false
    }

    /**
     * Common function to initialize or stop a given sensor. Makes three attempts.
     */
    private suspend fun setSensorState(sensor: Int, useSensor: Boolean): Boolean {
        val action = if (useSensor) "Initializing" else "De-initializing"
        if ((!useSensor) && (!sensorInitTrackingList.contains(sensor))) {
            // Nothing to do. We're stopping a sensor we never started.
            return true
        }
        LogUtil.debug("$action sensor $sensor")
        delay(100) // Hack since some sensors don't start
        val (lastByteToReadBack, requestBytes) = getSensorControlPacket(sensor, useSensor)
        for (count in 1..MAX_RETRIES) {
            var xfered = connection.controlTransfer(
                (USB_REQUEST_TYPE_DIR_OUT or USB_REQUEST_TYPE_TYPE_CLASS or USB_REQUEST_TYPE_RECIPIENT_INTERFACE).toInt(),
                USB_DEVICE_HID_REQUEST_SET_REPORT.toInt(),
                (USB_REQUEST_STANDARD_SET_FEATURE shl (8) or sensor.toUInt()).toInt(),
                sensorUsbInterface.id,
                requestBytes,
                requestBytes.size,
                TimeUnit.SECONDS.toMillis(1).toInt()
            )
            if (xfered >= 0) {
                // Read it back for sanity
                val incomingBytes = ByteArray(endpoint.maxPacketSize)
                xfered = connection.controlTransfer(
                    (USB_REQUEST_TYPE_DIR_IN or USB_REQUEST_TYPE_TYPE_CLASS or USB_REQUEST_TYPE_RECIPIENT_INTERFACE).toInt(),
                    USB_DEVICE_HID_REQUEST_GET_REPORT.toInt(),
                    (USB_REQUEST_STANDARD_SET_FEATURE shl (8) or sensor.toUInt()).toInt(),
                    sensorUsbInterface.id,
                    incomingBytes,
                    incomingBytes.size,
                    TimeUnit.SECONDS.toMillis(1).toInt()
                )
                if (xfered >= 0) {
                    val readBackOk = (requestBytes.copyOfRange(0, lastByteToReadBack)
                        .contentEquals(incomingBytes.copyOfRange(0, lastByteToReadBack)))
                    if (readBackOk) {
                        if (useSensor) {
                            if (waitForDataFromSensor(sensor)) {
                                sensorInitTrackingList.add(sensor)
                                return true
                            }
                            return false
                        } else {
                            // Not using the sensor, we're done
                            sensorInitTrackingList.remove(sensor)
                            return true
                        }
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
            delay(500)
        }
        if (useSensor) {
            LogUtil.rel("Sensor: $sensor failed to initialize")
        }
        return false
    }

    /**
     * Read all data and discard until the selected sensor reports in
     *
     * @param sensor valid sensor 1-4, or negative when purging
     */
    private fun waitForDataFromSensor(sensor: Int): Boolean {
        LogUtil.debug("Waiting for sensor $sensor")
        val startTime = System.currentTimeMillis()
        val MAX_MILLISECS_TO_WAIT = 5000L
        while ((System.currentTimeMillis() - startTime) < MAX_MILLISECS_TO_WAIT) {
            val bytes = ByteArray(endpoint.maxPacketSize)
            val read = connection.bulkTransfer(
                endpoint,
                bytes,
                bytes.size,
                TimeUnit.SECONDS.toMillis(1).toInt()
            )
            if (read > 0) {
                if (bytes[0].toInt() == sensor) {
                    // Data is coming from the correct sensor
                    LogUtil.debug("Got data from $sensor")
                    return true
                } else {
                    // LogUtil.debug("Got data from wrong $sensor continuing to wait")
                }
            } else {
                LogUtil.rel("Failed to read-back sensor $sensor : $read")
                return false
            }
        }
        LogUtil.rel("Sensor $sensor not sending data. Aborting.")
        return false
    }

    /**
     * Function used to begin consuming the sensor data stream.
     *
     * This is called immediately after notifying the listener of onSensorInitialized()
     */
    private fun runSensorStream() {
        getData = true
        LogUtil.debug("Starting sensor stream")
        while (getData) {
            val bytes = ByteArray(endpoint.maxPacketSize)
            // Interval is the device specified value needed in between each read.
            val read = connection.bulkTransfer(
                endpoint, bytes, bytes.size,
                TimeUnit.SECONDS.toMillis(1).toInt()
            )
            // LogUtil.debug("Sensor sent $read bytes: ", bytes, read)
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
        val reportId = byteArray[0]
        val sensorState = byteArray[1] // Used for debug purposes
        val sensorEvent = byteArray[2] // Used for debug purposes
        val deviceXData: Short = bytesToShort(byteArray[4], byteArray[3])
        val deviceYData: Short = bytesToShort(byteArray[6], byteArray[5])
        val deviceZData: Short = bytesToShort(byteArray[8], byteArray[7])

        return when (reportId.toInt()) {
            SENSOR_ACCELEROMETER_ID -> {
                // LogUtil.debug("Accelerometer: ID=${reportId} state=${sensorState} event=${sensorEvent} X=${deviceXData},Y=${deviceYData},Z=${deviceZData}")
                // Device X+ is towards power button; Y+ is toward camera; Z+ towards nav buttons
                val accel = floatArrayOf(
                    calculateAccelData((-deviceXData).toShort()),
                    calculateAccelData((-deviceZData).toShort()),
                    calculateAccelData((deviceYData))
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
                // LogUtil.debug("Gyro: ID=${reportId} state=${sensorState} event=${sensorEvent} X=${deviceXData},Y=${deviceYData},Z=${deviceZData}")
                // Device X+ is towards power button; Y+ is toward camera; Z+ towards nav buttons
                val gyroData = floatArrayOf(
                    calculateGyroData((deviceXData)),
                    calculateGyroData((deviceZData)),
                    calculateGyroData((-deviceYData).toShort())
                )
                if (smooth) {
                    val gyroAvg = floatArrayOf(0f, 0f, 0f)
                    smoothSensorData(gyroData, gyroAvg, SENSOR_GYRO_ID)
                    VuzixSensorEvent(Sensor.TYPE_GYROSCOPE, gyroAvg)
                } else {
                    VuzixSensorEvent(Sensor.TYPE_GYROSCOPE, gyroData)
                }
            }
            SENSOR_MAGNETOMETER_ID -> {
                val deviceAccuracy: Byte = byteArray[9] // Used for debug purposes
                // LogUtil.debug("Magnetometer: ID=${reportId} state=${sensorState} event=${sensorEvent}" +
                // " X=${deviceXData},Y=${deviceYData},Z=${deviceZData}, Accuracy=${deviceAccuracy}")
                // Different from orientation! Device X+ is towards power button; Y+ is toward USB; Z+ towards bottom of hinge
                val magnetometerData = floatArrayOf(
                    calculateMagnetometerData((deviceXData)),
                    calculateMagnetometerData((deviceZData)),
                    calculateMagnetometerData((deviceYData))
                )
                if (smooth) {
                    val magAvg = floatArrayOf(0f, 0f, 0f)
                    smoothSensorData(magnetometerData, magAvg, SENSOR_MAGNETOMETER_ID)
                    VuzixSensorEvent(Sensor.TYPE_MAGNETIC_FIELD, magAvg)
                } else {
                    VuzixSensorEvent(Sensor.TYPE_MAGNETIC_FIELD, magnetometerData)
                }
            }
            SENSOR_ORIENTATION_ID -> {
                val deviceWData: Short = bytesToShort(byteArray[10], byteArray[9])
                // https://usb.org/sites/default/files/hut1_2.pdf defines the order as X,Y,Z,W
                //
                // https://stackoverflow.com/questions/4436764/rotating-a-quaternion-on-1-axis
                // Device X+ is towards power button; Y+ is toward camera; Z+ towards nav buttons
                // So rotate the reported data 90 degrees around X and the axes move appropriately
                val sensorQuaternion = Quaternion(
                    calculateRotationData(deviceXData),
                    calculateRotationData(deviceYData),
                    calculateRotationData(deviceZData),
                    calculateRotationData(deviceWData)
                )
                val manipulationQuaternion = Quaternion.axisAngle(
                    1.0f,
                    0.0f,
                    0.0f,
                    90.0f
                ) // rotate about X axis by 90 degrees
                val axisRemappedData = Quaternion.multiply(sensorQuaternion, manipulationQuaternion)
                val rotationData = floatArrayOf(
                    axisRemappedData.x,
                    axisRemappedData.y,
                    axisRemappedData.z,
                    axisRemappedData.w
                )
                // LogUtil.debug("Orientation rotated: X=${rotationData[0]},Y=${rotationData[1]},Z=${rotationData[2]},W=${rotationData[3]} with $manipulationQuaternion")
                VuzixSensorEvent(Sensor.TYPE_ROTATION_VECTOR, rotationData)
            }
            else -> throw Exception("Unknown Sensor Type Detected: ${byteArray[0]}")
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