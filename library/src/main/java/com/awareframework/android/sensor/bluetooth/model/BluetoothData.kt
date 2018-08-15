package com.awareframework.android.sensor.bluetooth.model

import com.awareframework.android.core.model.AwareObject

/**
 * Contains the scan results data.
 *
 * @author  sercant
 * @date 15/08/2018
 */
data class BluetoothData(
        var address: String? = null,
        var name: String? = null,
        var rssi: Int = -1,
        var scanLabel: String = ""
) : AwareObject(jsonVersion = 1) {

    companion object {
        const val TABLE_NAME = "bluetoothData"
    }

    override fun toString(): String = toJson()
}
