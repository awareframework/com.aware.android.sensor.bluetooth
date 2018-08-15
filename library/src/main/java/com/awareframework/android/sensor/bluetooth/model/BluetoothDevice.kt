package com.awareframework.android.sensor.bluetooth.model

import com.awareframework.android.core.model.AwareObject

/**
 * Contains the mobile device’s Bluetooth sensor information.
 *
 * @author  sercant
 * @date 15/08/2018
 */
data class BluetoothDevice(
        var address: String = "",
        var name: String = ""
) : AwareObject(jsonVersion = 1) {

    companion object {
        const val TABLE_NAME = "bluetoothDevice"
    }

    override fun toString(): String = toJson()
}