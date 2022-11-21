/*
 * BleResultDataWork.kt
 */

package com.example.no4ble

import android.bluetooth.BluetoothDevice


class BleResultDataWork_c (val result_data:BleResultData_c) {

    private var selected_idx:Int = -1

    fun clear_devices_list() {
        while( result_data.devices.size > 0 ) {
            result_data.devices.removeAt(0)
        }
        selected_idx = -1
    }

    private fun remove_invalid() {
        while(true) {
            var dev_invalid_found:Boolean = false
            val sz = result_data.devices.size
            var dev_found:Boolean = false
            for ( idx in 0..(sz-1) ) {
                if (result_data.devices[idx].valid != true) {
                    result_data.devices.removeAt(idx)
                    dev_invalid_found = true
                    break
                }
            }
            if ( ! dev_invalid_found ) {
                break
            }
        }
    }

    fun add_ble_device(dev_addr:String, dev_name:String, dev_uuid:String,
                        dev_rssi:Int, tgt_uuid:String,
                        dev_device: BluetoothDevice
    ) {
        val sz = result_data.devices.size
        var dev_found:Boolean = false
        if ( dev_uuid != tgt_uuid ) {
            return
        }
        for ( idx in 0..(sz-1) ) {
            if ( result_data.devices[idx].device_address == dev_addr ) {
                if ( result_data.devices[idx].valid ) {
                    if ( result_data.devices[idx].device_name != dev_name ||
                         result_data.devices[idx].service_uuid != dev_uuid ) {
                        result_data.devices[idx].valid = false
                        continue
                    }
                    val v0:Float = result_data.devices[idx].rssi
                    val v1:Float = dev_rssi.toFloat()
                    result_data.devices[idx].rssi = (v0 * 0.95 + v1 * 0.05).toFloat()
                    result_data.devices[idx].result_count ++
                    result_data.devices[idx].last_seen_ms = System.currentTimeMillis()
                    result_data.devices[idx].device = dev_device
                    dev_found = true
                    break
                }
            }
        }
        if ( ! dev_found ) {
            val center_data = BleCenter_c(
                dev_addr, dev_name, dev_uuid,
                dev_rssi.toFloat(), 1,
                System.currentTimeMillis(),
                true,
                dev_device
            )
            result_data.devices.add(center_data)
        }
        remove_invalid()
    }

    fun get_devices_list():MutableList<BleCenter_c> {
        val ret_devs:MutableList<BleCenter_c> = mutableListOf()
        val sz = result_data.devices.size
        for ( idx in 0..(sz-1) ) {
            val data = result_data.devices[idx]
            val ctr = BleCenter_c(data.device_address,
                                    data.device_name,
                                    data.service_uuid,
                                    data.rssi,
                                    data.result_count,
                                    data.last_seen_ms,
                                    data.valid,
                                    data.device)
            ret_devs.add(ctr)
        }
        return ret_devs
    }

    fun get_devices_is_valid():Boolean {
        val now_ms = System.currentTimeMillis()

        val sz = result_data.devices.size
        var ret_valid:Boolean = true
        if ( sz < 2 ) {
            ret_valid = false
        }
        for ( idx in 0..(sz-1) ) {
            if ( ! ret_valid ) {
                break
            }
            val data = result_data.devices[idx]
            if ( data.result_count <= 6 ) {
                ret_valid = false
                break
            }
        }
        return ret_valid
    }

    fun select_device_by_index(msg:String) {
        val sel = msg.toInt()
        val sz = result_data.devices.size
        if (sel >= 1 && sel <= sz) {
            selected_idx = sel
        }
    }

    fun get_selected_device_index():Int {
        return selected_idx
    }
}

