package com.awareframework.android.sensor.bluetooth

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.awareframework.android.core.db.Engine
import com.awareframework.android.sensor.bluetooth.model.BluetoothData
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 * <p>
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getTargetContext()

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
    }
}
