package com.example.no4ble

class BleUtilData {
    //private val ble_uuid = "[00000001-0002-0003-0004-1234567890ab]"
    private val ble_uuid = "[6e400001-b5a3-f393-e0a9-e50e24dcca9e]" /* nordic nus */

    fun getUuid():String {
        return ble_uuid
    }
/*
I/printGattTable: Service 00001801-0000-1000-8000-00805f9b34fb
    Characteristics:
    |--00002a05-0000-1000-8000-00805f9b34fb
I/printGattTable: Service 00001800-0000-1000-8000-00805f9b34fb
    Characteristics:
    |--00002a00-0000-1000-8000-00805f9b34fb
    |--00002a01-0000-1000-8000-00805f9b34fb
    |--00002a04-0000-1000-8000-00805f9b34fb
    |--00002aa6-0000-1000-8000-00805f9b34fb
    Service 00000001-0002-0003-0004-1234567890ab
    Characteristics:
    |--00000002-0002-0003-0004-1234567890ab
    |--00000003-0002-0003-0004-1234567890ab
 */
    private val svc_user = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    private val char_wr = "6e400002-b5a3-f393-e0a9-e50e24dcca9e" /* incoming into the peripheral */
    private val char_rd = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
    private val char_rd_ccc = "00002902-0000-1000-8000-00805f9b34fb"

    fun getCharSvc():String {
        return svc_user
    }
    fun getCharRd():String {
        return char_rd
    }
    fun getCharWr():String {
        return char_wr
    }
    fun getCharRdCCC():String {
        return char_rd_ccc
    }
    fun getCharWrData():ByteArray {
        return byteArrayOf(0x21, 0x22, 0x23, 0x41, 0x42, 0x43)
    }
}
