# Vuzix USB-C Viewer Android Support
## Overview
This SDK allows Android developers to integrate the Vuzix USB-C Viewers into an application running on an Android smart phone.

Please note that Vuzix smart glasses fall into two categories:
1. Stand-alone smart glasses, such as the M400, which are *not* supported by this SDK.
2. USB-C Viewers, such as the M400C, for which this SDK is intended

The USB-C Viewer must be connected via a USB-C cable to an Android phone supporting USB-C DisplayPort Alt Mode. 

## Supported Features
1. Display - The display is automatically detected by the phone. No SDK methods are required to support this.
2. Audio - The microphones and speaker are automatically detected by the phone. No SDK methods are required to support this.
3. Button input - The buttons are delivered to your application as [Android Key Events](https://developer.android.com/reference/android/view/KeyEvent).
4. Touchpad input - The touchpad interaction is delivered to your application as [Android Motion Events](https://developer.android.com/reference/android/view/MotionEvent).
5. Orientation Sensor - The SDK provides access to the proprietary data format of the USB-C Viewer orientation sensor in a similar manner to 
[Android Sensor Events](https://developer.android.com/reference/android/hardware/SensorEvent).
6. Flashlight - The SDK provides the ability to turn the flashlight on the USB-C viewer on and off.
7. Camera - The USB-C Viewer camera acts as a standard UVC camera, and can be accessed using standard UVC camera APIs.

## Installation
You may integrate the Vuzix USB-C Viewer SDK into an Android development project by referencing the appropriate Maven artifacts.

For gradle projects specify the repository in your root gradle
```
allprojects {
  repositories {
	  ...
	  maven { url 'https://jitpack.io' }
  }
}
```
And add the dependecy to your project gradle
```
dependencies {
  implementation 'com.vuzix:sdk-usbcviewer:Tag'
} 
```
For a full list of available release tags, and syntax to add to non-gradle projects, please see our [Jitpack Package Repository](https://jitpack.io/#com.vuzix/sdk-usbcviewer).

## Usage
Please refer to the sample client application for usage related to the provided entry points:
- com.vuzix.sdk.usbcviewer.flashlight.Flashlight
- com.vuzix.sdk.usbcviewer.sensors.Sensors

## Technical Support
Developers that own Vuzix USB-C viewer hardware may direct integration questions to [Vuzix technical support](https://www.vuzix.com/pages/contact-technical-support).
