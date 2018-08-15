# AWARE Bluetooth

[![jitpack-badge](https://jitpack.io/v/awareframework/com.aware.android.sensor.bluetooth.svg)](https://jitpack.io/#awareframework/com.aware.android.sensor.bluetooth)

The Bluetooth sensor logs the mobile device’s Bluetooth sensor and detects surrounding Bluetooth-enabled and visible devices with respective RSSI dB values at specified intervals (default is 1 minute). A scan session assigns the same timestamp for all the found Bluetooth devices. Android can take up to 60 seconds to resolve all the found Bluetooth device’s names. There is no way around this. The default and recommended scanning interval is 1 minute or higher.

## Public functions

### BluetoothSensor

+ `startService(context: Context, config: BluetoothConfig?)`: Starts the bluetooth sensor with the optional configuration.
+ `stopService(context: Context)`: Stops the service.

### BluetoothConfig

Class to hold the configuration of the sensor.

#### Fields

+ `debug: Boolean`: enable/disable logging to `Logcat`. (default = false)
+ `host: String`: Host for syncing the database. (default = null)
+ `key: String`: Encryption key for the database. (default = no encryption)
+ `host: String`: Host for syncing the database. (default = null)
+ `type: EngineDatabaseType`: Which db engine to use for saving data. (default = NONE)
+ `path: String`: Path of the database.
+ `deviceId: String`: Id of the device that will be associated with the events and the sensor. (default = "")
+ `sensorObserver: BluetoothSensor.SensorObserver`: Callback for live data updates.
+ `frequency: Float`: Frequency of the bluetooth data querying in minutes. (default = 1f)

## Broadcasts

### Fired Broadcasts

+ `BluetoothSensor.ACTION_AWARE_BLUETOOTH_NEW_DEVICE`: fired when a new Bluetooth device is detected. `BluetoothSensor.EXTRA_DEVICE` field in the intent extra contains the device’s information in JSON string format.
+ `BluetoothSensor.ACTION_AWARE_BLUETOOTH_SCAN_STARTED`: fired when a scan session has started.
+ `BluetoothSensor.ACTION_AWARE_BLUETOOTH_SCAN_ENDED`: fired when a scan session has ended.

If device supports low power bluetooth technology then the broadcasts are as follows:

+ `BluetoothSensor.ACTION_AWARE_BLUETOOTH_NEW_DEVICE_BLE`: fired when a new Bluetooth device is detected. `BluetoothSensor.EXTRA_DEVICE` field in the intent extra contains the device’s information in JSON string format.
+ `BluetoothSensor.ACTION_AWARE_BLUETOOTH_BLE_SCAN_STARTED`: fired when a scan session has started.
+ `BluetoothSensor.ACTION_AWARE_BLUETOOTH_BLE_SCAN_ENDED`: fired when a scan session has ended.

### Received Broadcasts

+ `BluetoothSensor.ACTION_AWARE_BLUETOOTH_REQUEST_SCAN`: received broadcast to request a Bluetooth scan as soon as possible, as Bluetooth scanning is monopolist.
+ `BluetoothSensor.ACTION_AWARE_BLUETOOTH_SYNC`: received broadcast to send sync attempt to the host.
+ `BluetoothSensor.ACTION_AWARE_BLUETOOTH_SET_LABEL`: received broadcast to set the data label. Label is expected in the `BluetoothSensor.EXTRA_LABEL` field of the intent extras.

## Data Representations

### Bluetooth Device

Contains the mobile device’s Bluetooth sensor information.

| Field     | Type   | Description                                       |
| --------- | ------ | ------------------------------------------------- |
| address   | String | the device’s Bluetooth sensor MAC address        |
| name      | String | the device’s Bluetooth sensor user assigned name |
| deviceId  | String | AWARE device UUID                                 |
| timestamp | Long   | unixtime milliseconds since 1970                  |
| timezone  | Int    | [Raw timezone offset][1] of the device            |
| os        | String | Operating system of the device (ex. android)      |

### Bluetooth Data

Contains the scan results data.

| Field     | Type   | Description                                       |
| --------- | ------ | ------------------------------------------------- |
| address   | String | the device’s Bluetooth sensor MAC address        |
| name      | String | the device’s Bluetooth sensor user assigned name |
| rssi      | Int    | the RSSI dB to the scanned device                 |
| scanLabel | String | unixtime miliseconds of the initial scan request  |
| deviceId  | String | AWARE device UUID                                 |
| timestamp | Long   | unixtime milliseconds since 1970                  |
| timezone  | Int    | [Raw timezone offset][1] of the device            |
| os        | String | Operating system of the device (ex. android)      |

## Example usage

```kotlin
// To start the service.
BluetoothSensor.startService(appContext, BluetoothSensor.BluetoothConfig().apply {
    sensorObserver = object : BluetoothSensor.SensorObserver {
        override fun onBluetoothDetected(data: BluetoothData) {
            // your code here...
        }

        override fun onBluetoothBLEDetected(data: BluetoothData) {
            // your code here...
        }

        override fun onScanStarted() {
            // your code here...
        }

        override fun onScanEnded() {
            // your code here...
        }

        override fun onBLEScanStarted() {
            // your code here...
        }

        override fun onBLEScanEnded() {
            // your code here...
        }

        override fun onBluetoothDisabled() {
            // your code here...
        }
    }
    dbType = Engine.DatabaseType.ROOM
    debug = true
    // more configuration...
})

// To stop the service
BluetoothSensor.stopService(appContext)
```

## License

Copyright (c) 2018 AWARE Mobile Context Instrumentation Middleware/Framework (http://www.awareframework.com)

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

[1]: https://developer.android.com/reference/java/util/TimeZone#getRawOffset()
