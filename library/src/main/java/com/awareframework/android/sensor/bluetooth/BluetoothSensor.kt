package com.awareframework.android.sensor.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlarmManager.RTC_WAKEUP
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import com.awareframework.android.core.AwareSensor
import com.awareframework.android.core.R
import com.awareframework.android.core.db.model.DbSyncConfig
import com.awareframework.android.core.model.SensorConfig
import com.awareframework.android.core.util.NotificationUtil
import com.awareframework.android.core.util.NotificationUtil.Companion.AWARE_NOTIFICATION_ID
import com.awareframework.android.sensor.bluetooth.model.BluetoothData


/**
 * Bluetooth Module. For now, scans and returns surrounding bluetooth devices and RSSI dB values.
 *
 * @author  sercant
 * @date 14/08/2018
 */
class BluetoothSensor : AwareSensor() {

    companion object {
        const val TAG = "AWARE::Bluetooth"

        /**
         * Broadcasted event: new bluetooth device detected
         */
        const val ACTION_AWARE_BLUETOOTH_NEW_DEVICE = "ACTION_AWARE_BLUETOOTH_NEW_DEVICE"
        const val ACTION_AWARE_BLUETOOTH_NEW_DEVICE_BLE = "ACTION_AWARE_BLUETOOTH_NEW_DEVICE_BLE"
        const val EXTRA_DEVICE = "extra_device"

        /**
         * Broadcasted event: bluetooth scan started
         */
        const val ACTION_AWARE_BLUETOOTH_SCAN_STARTED = "ACTION_AWARE_BLUETOOTH_SCAN_STARTED"
        const val ACTION_AWARE_BLUETOOTH_BLE_SCAN_STARTED = "ACTION_AWARE_BLUETOOTH_BLE_SCAN_STARTED"

        /**
         * Broadcasted event: bluetooth scan ended
         */
        const val ACTION_AWARE_BLUETOOTH_SCAN_ENDED = "ACTION_AWARE_BLUETOOTH_SCAN_ENDED"
        const val ACTION_AWARE_BLUETOOTH_BLE_SCAN_ENDED = "ACTION_AWARE_BLUETOOTH_BLE_SCAN_ENDED"

        /**
         * Broadcast receiving event: request a bluetooth scan
         */
        const val ACTION_AWARE_BLUETOOTH_REQUEST_SCAN = "ACTION_AWARE_BLUETOOTH_REQUEST_SCAN"

        /**
         * Request user permission for bt scanning
         */
        private const val ACTION_AWARE_ENABLE_BT = "ACTION_AWARE_ENABLE_BT";

        const val ACTION_AWARE_BLUETOOTH_START = "com.awareframework.android.sensor.bluetooth.SENSOR_START"
        const val ACTION_AWARE_BLUETOOTH_STOP = "com.awareframework.android.sensor.bluetooth.SENSOR_STOP"

        const val ACTION_AWARE_BLUETOOTH_SET_LABEL = "com.awareframework.android.sensor.bluetooth.SET_LABEL"
        const val EXTRA_LABEL = "label"

        const val ACTION_AWARE_BLUETOOTH_SYNC = "com.awareframework.android.sensor.bluetooth.SENSOR_SYNC"

        val CONFIG = Config()

        val REQUIRED_PERMISSIONS = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION
        )

        fun start(context: Context, config: Config? = null) {
            if (config != null)
                CONFIG.replaceWith(config)
            context.startService(Intent(context, BluetoothSensor::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BluetoothSensor::class.java))
        }
    }

    private lateinit var alarmManager: AlarmManager
    private lateinit var bluetoothScan: PendingIntent

    private var scanTimestamp = 0L
    private var currentFrequency: Float = -1f

    private var bluetoothAdapter: BluetoothAdapter? = null

    private lateinit var notificationManager: NotificationManager
    private lateinit var enableBT: Intent

    private var bleSupport = false

    private val mBLEHandler: Handler = Handler()

    private val scanSettings: ScanSettings? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
    } else {
        null
    }
    private var isBLEScanning = false
    private val discoveredBLE = HashMap<String, BluetoothDevice>()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return

            when (intent.action) {
                ACTION_AWARE_BLUETOOTH_SET_LABEL -> {
                    intent.getStringExtra(EXTRA_LABEL)?.let {
                        CONFIG.label = it
                    }
                }

                ACTION_AWARE_BLUETOOTH_SYNC -> onSync(intent)
            }
        }
    }

    private val bluetoothMonitor = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val extras = intent?.extras ?: return

            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val btDevice: BluetoothDevice? = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE)
                    if (btDevice != null) {
                        val btDeviceRSSI = extras.getShort(BluetoothDevice.EXTRA_RSSI)
                        val data = BluetoothData().apply {
                            timestamp = System.currentTimeMillis()
                            deviceId = CONFIG.deviceId
                            label = CONFIG.label

                            address = btDevice.address // TODO add encryption
                            name = btDevice.name // TODO add encryption
                            rssi = btDeviceRSSI.toInt()
                            scanLabel = scanTimestamp.toString()
                        }

                        dbEngine?.save(data, BluetoothData.TABLE_NAME)
                        CONFIG.sensorObserver?.onBluetoothDetected(data)

                        logd("$ACTION_AWARE_BLUETOOTH_NEW_DEVICE: $data")

                        sendBroadcast(Intent(ACTION_AWARE_BLUETOOTH_NEW_DEVICE).apply {
                            putExtra(EXTRA_DEVICE, data.toJson())
                        })
                    } else {
                        logd("No Bluetooth device was discovered during the scan")
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    CONFIG.sensorObserver?.onScanEnded()

                    logd(ACTION_AWARE_BLUETOOTH_SCAN_ENDED)
                    sendBroadcast(Intent(ACTION_AWARE_BLUETOOTH_SCAN_ENDED))

                    mBLEHandler.post(scanRunnable)
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    CONFIG.sensorObserver?.onScanStarted()

                    scanTimestamp = System.currentTimeMillis()
                    logd(ACTION_AWARE_BLUETOOTH_SCAN_STARTED)
                    sendBroadcast(Intent(ACTION_AWARE_BLUETOOTH_SCAN_STARTED))
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (extras.getInt(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_OFF -> notifyMissingBluetooth(false)
                        BluetoothAdapter.STATE_ON -> notifyMissingBluetooth(true)
                    }
                }

                ACTION_AWARE_BLUETOOTH_REQUEST_SCAN -> {
                    bluetoothAdapter?.let {
                        //interrupt ongoing scans
                        if (it.isDiscovering) it.cancelDiscovery()

                        if (!it.isDiscovering) {
                            if (it.isEnabled) it.startDiscovery()
                            else {
                                //Bluetooth is off
                                logd("Bluetooth is turned off...")

                                val data = BluetoothData().apply {
                                    timestamp = System.currentTimeMillis()
                                    deviceId = CONFIG.deviceId
                                    label = CONFIG.label

                                    name = "disabled"
                                    address = "disabled"
                                    scanLabel = "disabled"
                                }

                                dbEngine?.save(data, BluetoothData.TABLE_NAME)

                                CONFIG.sensorObserver?.onBluetoothDisabled()
                            }
                        }
                    }
                }
            }
        }
    }

    private val scanRunnable = Runnable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val scanner =
                    bluetoothAdapter?.bluetoothLeScanner

            if (scanner != null && !isBLEScanning) {
                mBLEHandler.postDelayed(stopScan, 3000)
                scanner.startScan(null, scanSettings, scanCallback)

                CONFIG.sensorObserver?.onBLEScanStarted()
                logd(ACTION_AWARE_BLUETOOTH_BLE_SCAN_STARTED)

                sendBroadcast(Intent(ACTION_AWARE_BLUETOOTH_BLE_SCAN_STARTED))

                isBLEScanning = true
            }
        }
    }

    private val stopScan = Runnable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val scanner =
                    bluetoothAdapter?.bluetoothLeScanner

            if (scanner != null && isBLEScanning) {
                scanner.stopScan(scanCallback)
                CONFIG.sensorObserver?.onBLEScanEnded()

                logd(ACTION_AWARE_BLUETOOTH_BLE_SCAN_ENDED)
                sendBroadcast(Intent(ACTION_AWARE_BLUETOOTH_BLE_SCAN_ENDED))

                discoveredBLE.clear()

                isBLEScanning = false
            }
        }
    }

    private val scanCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        object : ScanCallback() {
            @SuppressLint("NewApi")
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)

                val btDevice = result?.device ?: return
                if (discoveredBLE.containsKey(btDevice.address)) return

                discoveredBLE.put(btDevice.address, btDevice)

                val data = BluetoothData().apply {
                    timestamp = System.currentTimeMillis()
                    deviceId = CONFIG.deviceId
                    label = CONFIG.label

                    address = btDevice.address // TODO add encryption
                    name = btDevice.name // TODO add encryption
                    rssi = result.rssi
                    scanLabel = scanTimestamp.toString()
                }

                dbEngine?.save(data, BluetoothData.TABLE_NAME)
                CONFIG.sensorObserver?.onBluetoothBLEDetected(data)

                logd("$ACTION_AWARE_BLUETOOTH_NEW_DEVICE_BLE: $data")

                sendBroadcast(Intent(ACTION_AWARE_BLUETOOTH_NEW_DEVICE_BLE).apply {
                    putExtra(EXTRA_DEVICE, data.toJson())
                })
            }
        }
    } else {
        null
    }

    override fun onCreate() {
        super.onCreate()

        initializeDbEngine(CONFIG)
        NotificationUtil.createNotificationChannel(this)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        registerReceiver(bluetoothMonitor, IntentFilter().apply {
            addAction(ACTION_AWARE_BLUETOOTH_REQUEST_SCAN)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        })

        val backgroundService = Intent(ACTION_AWARE_BLUETOOTH_REQUEST_SCAN)
        bluetoothScan = PendingIntent.getBroadcast(this, 0, backgroundService, PendingIntent.FLAG_UPDATE_CURRENT)

        bluetoothAdapter =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                    (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                else
                    BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            stopSelf()
            return
        }

        bleSupport = packageManager.hasSystemFeature(FEATURE_BLUETOOTH_LE)

        enableBT = Intent(this, BluetoothSensor::class.java).apply {
            putExtra("action", ACTION_AWARE_ENABLE_BT)
        }

        registerReceiver(bluetoothReceiver, IntentFilter().apply {
            addAction(ACTION_AWARE_BLUETOOTH_SET_LABEL)
            addAction(ACTION_AWARE_BLUETOOTH_SYNC)
        })

        logd("Bluetooth service created!")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (REQUIRED_PERMISSIONS.any { ContextCompat.checkSelfPermission(this, it) != PERMISSION_GRANTED }) {
            logw("Missing permissions detected.")
            return START_NOT_STICKY
        }

        intent?.let {
            if (it.getStringExtra("action") == ACTION_AWARE_ENABLE_BT)
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }

        val bleAdapter = bluetoothAdapter

        if (bleAdapter == null) {
            logw("No bluetooth is detected on this device")
            stopSelf()
            return START_NOT_STICKY
        } else {
            if (!bleAdapter.isEnabled) {
                notifyMissingBluetooth(false)
            }

            saveDevice(bleAdapter)

            if (currentFrequency != CONFIG.frequency) {
                currentFrequency = CONFIG.frequency

                alarmManager.cancel(bluetoothScan)
                alarmManager.setRepeating(
                        RTC_WAKEUP,
                        System.currentTimeMillis() + (currentFrequency * 60000).toLong(),
                        (currentFrequency * 2 * 60000).toLong(),
                        bluetoothScan
                )
            }
        }

        logd("Bluetooth service is active: $currentFrequency m")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        dbEngine?.close()

        unregisterReceiver(bluetoothReceiver)
        unregisterReceiver(bluetoothMonitor)

        logd("Bluetooth service terminated.")
    }

    private fun saveDevice(btAdapter: BluetoothAdapter?) {
        btAdapter ?: return

        val device = com.awareframework.android.sensor.bluetooth.model.BluetoothDevice().apply {
            timestamp = System.currentTimeMillis()
            deviceId = CONFIG.deviceId
            label = CONFIG.label

            address = btAdapter.address // TODO add encryption
            name = btAdapter.name // TODO add encryption
        }

        dbEngine?.save(device, com.awareframework.android.sensor.bluetooth.model.BluetoothDevice.TABLE_NAME, 0)
        logd("Bluetooth local information: $device")
    }

    private fun notifyMissingBluetooth(dismiss: Boolean) {
        if (!dismiss) {
            val builder = NotificationCompat.Builder(applicationContext, AWARE_NOTIFICATION_ID)
                    .setSmallIcon(R.drawable.ic_aware_accessibility_white)
                    .setContentTitle("AWARE: Bluetooth needed")
                    .setContentText("Tap to enable Bluetooth for nearby scanning.")
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getService(
                            this,
                            123,
                            enableBT,
                            PendingIntent.FLAG_UPDATE_CURRENT
                    ))

            try {
                notificationManager.notify(123, builder.build())
            } catch (e: NullPointerException) {
                logd("Notification exception: ${e.message}")
            }
        } else {
            try {
                notificationManager.cancel(123)
            } catch (e: NullPointerException) {
                logd("Notification exception: ${e.message}")
            }
        }
    }

    override fun onSync(intent: Intent?) {
        dbEngine?.startSync(BluetoothData.TABLE_NAME)
        dbEngine?.startSync(
                com.awareframework.android.sensor.bluetooth.model.BluetoothDevice.TABLE_NAME,
                DbSyncConfig(removeAfterSync = false
                ))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    data class Config(
            var sensorObserver: Observer? = null,
            var frequency: Float = 1f
    ) : SensorConfig(dbPath = "aware_bluetooth") {

        override fun <T : SensorConfig> replaceWith(config: T) {
            super.replaceWith(config)

            if (config is Config) {
                sensorObserver = config.sensorObserver
                frequency = config.frequency
            }
        }
    }

    interface Observer {
        fun onBluetoothDetected(data: BluetoothData)
        fun onBluetoothBLEDetected(data: BluetoothData)
        fun onScanStarted()
        fun onScanEnded()
        fun onBLEScanStarted()
        fun onBLEScanEnded()
        fun onBluetoothDisabled()
    }

    class BluetoothSensorBroadcastReceiver : AwareSensor.SensorBroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return

            logd("Sensor broadcast received. action: " + intent?.action)

            when (intent?.action) {
                SENSOR_START_ENABLED -> {
                    logd("Sensor enabled: " + CONFIG.enabled)

                    if (CONFIG.enabled) {
                        start(context)
                    }
                }

                ACTION_AWARE_BLUETOOTH_STOP,
                SENSOR_STOP_ALL -> {
                    logd("Stopping sensor.")
                    stop(context)
                }

                ACTION_AWARE_BLUETOOTH_START -> {
                    start(context)
                }
            }
        }
    }
}

private fun logd(text: String) {
    if (BluetoothSensor.CONFIG.debug) Log.d(BluetoothSensor.TAG, text)
}

private fun logw(text: String) {
    Log.w(BluetoothSensor.TAG, text)
}