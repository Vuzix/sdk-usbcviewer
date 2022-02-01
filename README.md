# Vuzix USB-C Viewer Android Support
## Overview
This SDK allows Android developers to integrate the Vuzix USB-C Viewers into an application running on an Android smart phone.

Please note that Vuzix smart glasses fall into two categories:
1. Stand-alone smart glasses, such as the M400, which are *not* supported by this SDK.
2. USB-C Viewers, such as the M400C, for which this SDK is intended.

The USB-C Viewer must be connected via a USB-C cable to an Android phone supporting USB-C DisplayPort Alt Mode.

## Supported Features
1. Display - The display is automatically detected by the phone. No SDK methods are required to support this.
2. Audio - The microphones and speaker are automatically detected by the phone. No SDK methods are required to support this.
3. Button input - The buttons are delivered to your application as
[Android Key Events](https://developer.android.com/reference/android/view/KeyEvent).
4. Touchpad input - The touchpad interaction is delivered to your application as
[Android Motion Events](https://developer.android.com/reference/android/view/MotionEvent).
5. Orientation Sensor - The SDK provides access to the proprietary data format of the USB-C Viewer orientation sensor in a
similar manner to [Android Sensor Events](https://developer.android.com/reference/android/hardware/SensorEvent).
6. Flashlight - The SDK provides the ability to turn the flashlight on the USB-C viewer on and off.
7. Camera - The USB-C Viewer camera acts as a standard UVC camera, and can be accessed using standard UVC camera APIs.

## Installation
Most developers should use the stable pre-built libraries to get the required SDK behavior. You may integrate the Vuzix USB-C
Viewer SDK into an Android development project by referencing the appropriate Maven artifacts.

For gradle projects, specify the repository in your root gradle:

```
allprojects {
  repositories {
	  ...
	  maven { url 'https://jitpack.io' }
  }
}
```

And add the dependecy to your project gradle:

```
dependencies {
  implementation 'com.vuzix:sdk-usbcviewer:0.0.1'
}
```

For a full list of available release tags, and syntax to add to non-gradle projects, please see our
[Jitpack Package Repository](https://jitpack.io/#com.vuzix/sdk-usbcviewer).

## Application Manifest
The *AndroidManifest.xml* file may require changes to interact with the USB-C Viewer.

### Feature Declaration
Ensure the following line is in your *AndroidManifest.xml* to declare that you use the USB feature for your app:

`<uses-feature android:name="android.hardware.usb.host" />`

### Automatically Launching Your Application

Optionally, you can add an `intent-filter` in your *AndroidManifest.xml* within an `activity` tag to automatically launch your app when the
USB-C Viewer is connected to the phone.

```xml
<activity>
    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
    </intent-filter>
    <meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
        android:resource="@xml/device_filter" />
</activity>
```

## Usage

### Common
The SDK exposes functionality of the external hardware. There are multiple SDK interface classes to handle each set of related behaviors. These are
described in subsequent sections below. Before using the specific behavior of each interface class, you must explicitly connect to the interface. To
do so, create an instance of the appropriate interface class, then call its `connect()` method.

If the USB-C Viewer is not available, the `connect()` call will throw an exception.  You may optionally query `isDeviceAvailable()` prior
to `connect()` to avoid generatiung an exception.

Close the connection by calling `disconnect()` when the interface is no longer required.

To track whether or not you have connected you may evaluate the `connected` boolean property of the interface class.

A sample of that flow, minus exception handling, might look like:

```
if( !usbcInterface.connected ) {
	if( usbcInterface.isDeviceAvailable() ) {
		usbcInterface.connect();
	}
}
if( usbcInterface.connected ) {
	//todo: do something with the interface while connected, then
	usbcInterface.disconnect();
}
```

### Flashlight
The Flashlight interface is `com.vuzix.sdk.usbcviewer.flashlight.Flashlight`.

The Flashlight interface class will allow you to toggle the ON/OFF state of the device Flashlight.

Once a connection is initialized you may simply call `turnFlashlightOn()` and `turnFlashlightOff()`.

### Sensors
The Sensors interface is `com.vuzix.sdk.usbcviewer.sensors.Sensors`.

The Sensors interface class will allow you to receive data from the device sensors. They come back in the following formats:

* Accelerometer: *meters per second squared*
* Gyrometer: *degrees per second*
* Magnetometer/Compass: *µ Teslas*

The `Sensors` object requires a `VuzixSensorListener`. This is very similar to an
[Android SensorEventListener](https://developer.android.com/reference/android/hardware/SensorEventListener) and all results are given to
the `onSensorChanged()` method. `VuzixSensorListener` also adds an `onSensorInitialized()` to know when the asynchronous initialization completes on the
device, and an `onError()` in case the initialization fails or the device disconnects after being initialized.

Once the connection is established, you will need to enable the sensors by calling `initializeSensors()`.

Once the sensor data is initialized, you will receive the data in the form of a `VuzixSensorEvent` object. This contains a `sensorType`
value of type `Sensor.TYPE_ACCELEROMETER`, `Sensor.TYPE_MAGNETIC_FIELD`, or `Sensor.TYPE_GYROSCOPE`.

The data is stored in the `values` array and the axis corresponds as such:
* `values[0]` = x-axis
* `values[1]` = y-axis
* `values[2]` = z-axis

The axes returned by the SDK are defined according to the standard [Android Mobile Device Axes](https://source.android.com/devices/sensors/sensor-types)
when the device being worn on the right eye. Note: The axes from the SDK different are different than the raw data described in the M400C data sheet.

![M400-C Sensor Orientation](docs/M400C-Android_Sensors.png)

## Technical Support
Developers that own Vuzix USB-C viewer hardware may direct integration questions to
[Vuzix technical support](https://www.vuzix.com/pages/contact-technical-support).
