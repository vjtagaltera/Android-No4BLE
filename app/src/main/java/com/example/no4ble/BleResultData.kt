/*
 * BleResultData.kt
 */

package com.example.no4ble

import android.bluetooth.BluetoothDevice


class BleStateActivity_c {
    val state:BleStateType = BleStateType()
    val stateTimeMs = System.currentTimeMillis()
}

class BleCenter_c ( val device_address:String,
                    val device_name:String,
                    val service_uuid:String,
                    var rssi:Float,
                    var result_count:Int,
                    var last_seen_ms:Long,
                    var valid:Boolean,
                    var device:BluetoothDevice
                    ) {
}

class BleConnection_c ( val bel_center:BleCenter_c,
                        val connect_ms:Long
                        ) {
}


class BleResultData_c {

    val state_trace:MutableList<BleStateActivity_c> = mutableListOf()
    var state_current:BleStateActivity_c = BleStateActivity_c()

    val devices:MutableList<BleCenter_c> = mutableListOf()
    var connection:BleConnection_c? = null
}

