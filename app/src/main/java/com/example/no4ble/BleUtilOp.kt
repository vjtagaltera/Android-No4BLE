package com.example.no4ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.IBinder
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/* search: android ble sample
   ref:    https://punchthrough.com/android-ble-guide/

   AndroidManifest.xml add:
        <uses-permission android:name="android.permission.BLUETOOTH" />
        <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
        <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

        <uses-feature
                android:name="android.hardware.bluetooth_le"
                android:required="true" />
 */

/* search: android service example
   ref:    [1] https://www.vogella.com/tutorials/AndroidServices/article.html
           [2] https://www.geeksforgeeks.org/services-in-android-with-example/
               mainly use [2] for normal service.
           [3] https://proandroiddev.com/bound-and-foreground-services-in-android-a-step-by-step-guide-5f8362f4ae20
               https://github.com/iambaljeet/BoundServiceDemo

   mention service in manifest:
        <!-- Mention the service name here -->
        <service android:name=".NewService"/>
 */


class BleOperationType {

}


class BleStateType {
    public var state_idx: Int = 0
    public var state_phase: Int = 0
    public var result_ok: Boolean = false
}


class BleUtilOp : Service() {

    /**
     * Class used for the client Binder. The Binder object is responsible for returning an instance
     * of "MyService" to the client.
     */
    inner class MyBinder : Binder() {
        // Return this instance of MyService so clients can call public methods
        val service: BleUtilOp
            get() =// Return this instance of MyService so clients can call public methods
                this@BleUtilOp
    }

    // Binder given to clients (notice class declaration below)
    private val mBinder: IBinder = MyBinder()

    // Channel ID for notification
    val CHANNEL_ID = "Random number notification"

    /**
     * This is how the client gets the IBinder object from the service. It's retrieve by the "ServiceConnection"
     * which you'll see later.
     */
    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    /**
     * Used for creating and starting notification
     * whenever we start our Bound service
     */
    private fun startNotification() {
        /*TODO: not used */
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID,
                "My Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("A service is running in the background")
            .setContentText("Generating random number").build()
        startForeground(1, notification)
    }


    private val ENABLE_BLUETOOTH_REQUEST_CODE = 1
    private val GATT_MAX_MTU_SIZE = 517

    private val ble_data = BleUtilData()

    private val target_uuid_service = ble_data.getUuid()
    /* Address: 12:34:56:78:90:ab, Name: cd-4567, RSSI: -64, Uuid: [00000001-0002-0003-0004-010203040506] */

    private val operationQueue = ConcurrentLinkedQueue<BleOperationType>()
    private var pendingOperation: BleOperationType? = null

    private val throttle_max_scan = 5
    private val throttle_max_scan_period = 30 /* max 5 startScan in 30 seconds */

    private var state_current: BleStateType? = null
    private var state_previous: BleStateType? = null

    private var is_scanning_in = false
    private var scan_result_data: BleResultData_c? = null
    private var scan_result_work: BleResultDataWork_c? = null

    private var connect_trigger_request = false
    private var connect_trigger_failed = false
    private var connect_gatt: BluetoothGatt? = null /* set when connected */

    private var service_discovered = false
    private var service_mtu = 20 /* default 23, 23 - 3 user usable. 3 att header. */

    private var data_read_record:String? = null
    private var data_received_history:String? = null


    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .build()

    /* scan callback */
    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            //Log.w("ScanCallback: ", "$callbackType $result")
            /* 1
               ScanResult{device=12:34:56:78:90:ab,
                        scanRecord=ScanRecord [mAdvertiseFlags=6,
                                    mServiceUuids=[00000001-0002-0003-0004-010203040506],
                                    mServiceSolicitationUuids=[], mManufacturerSpecificData={},
                                    mServiceData={},
                                    mTxPowerLevel=-2147483648, mDeviceName=ab-1234],
                                    rssi=-65, timestampNanos=988230279152291, eventType=27,
                                    primaryPhy=1, secondaryPhy=0, advertisingSid=255,
                                    txPower=127, periodicAdvertisingInterval=0}
            */
            with(result) {
                //Log.i("ScanCallback",
                //      "Found BLE device! Address: ${device}, Name: ${scanRecord?:"Unnamed"}")
                var rec: ScanRecord? = this.scanRecord
                var vnam: String = "<no-name>"
                var vflag: Int? = null
                var src_uuid: List<ParcelUuid>? = null
                if ( rec != null ) {
                    val tmp_nam = rec.getDeviceName()
                    if ( tmp_nam != null ) {
                        vnam = tmp_nam
                    }
                    val tmp_flag:Int = rec.getAdvertiseFlags()
                    if (tmp_flag >= 0) {
                        vflag = tmp_flag
                    }
                    src_uuid = rec.getServiceUuids()
                }
                val vrssi = this.rssi
                val vuuid = src_uuid?.toString()?:"<no-uuid>"

                while ( src_uuid != null )
                { /* scope */

                    val dev_addr = device.toString()
                    val wkr = scan_result_work
                    if ( wkr == null ) {
                        break /* scope */
                    }
                    wkr.add_ble_device(dev_addr, vnam, vuuid, vrssi,
                                        target_uuid_service, result.device)
                    Log.i(LOG_PREFIX, "ScanCallback: " +
                        "Found BLE device! Address: ${dev_addr}, " +
                                "Name: ${vnam}, " +
                                "RSSI: ${vrssi}, " +
                                "flag: ${vflag}, " +
                                "Uuid: ${vuuid}")
                    break /* scope */
                }
            }
        }
    }

    val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }


    /* gatt callback */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w(LOG_PREFIX,
                        "BluetoothGattCallback: Successfully connected to $deviceAddress")
                    // TODO: Store a reference to BluetoothGatt
                    connect_gatt = gatt
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(LOG_PREFIX,
                        "BluetoothGattCallback: Successfully disconnected from $deviceAddress")
                    gatt.close()
                    connect_gatt = null
                }
            } else {
                Log.e(LOG_PREFIX,
                    "BluetoothGattCallback: " +
                    "Error $status encountered for $deviceAddress! Disconnecting..."
                )
                /* seen the infamous 133 error:
                D/BluetoothGatt: onClientConnectionState() - status=133 clientIf=11 device=12:34:56:78:90:ab
                W/BluetoothGattCallback: Error 133 encountered for 12:34:56:78:90:ab! Disconnecting...
                 */
                gatt.close()
                connect_gatt = null
                connect_trigger_failed = true
            }
        }

        private fun BluetoothGatt.printGattTable() {
            if (services.isEmpty()) {
                Log.i(LOG_PREFIX,
                    "printGattTable: " +
                    "No service and characteristic available, call discoverServices() first?"
                )
                return
            }
            services.forEach { service ->
                val characteristicsTable = service.characteristics.joinToString(
                    separator = "\n|--",
                    prefix = "|--"
                ) { it.uuid.toString() }
                Log.i(LOG_PREFIX,
                    "printGattTable: " +
                    "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
                )
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w(LOG_PREFIX,
                    "BluetoothGattCallback: " +
                    "Discovered ${services.size} services for ${device.address}"
                )
                printGattTable() // See implementation just above this section
                // Consider connection setup as complete here
                service_discovered = true
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                service_mtu = mtu
                Log.w(LOG_PREFIX,
                    "Ble MTU: " +
                    "ATT MTU changed to $mtu, success: " +
                            "${status == BluetoothGatt.GATT_SUCCESS}"
                )
            } else {
                Log.e(LOG_PREFIX,
                    "Ble MTU: " +
                    "ATT MTU failed to change to $mtu, success: " +
                            "${status == BluetoothGatt.GATT_SUCCESS}"
                )
            }
        }
        /* requesting 517 on ble 4.2 will get a reply for 247:
            D/BluetoothGatt: configureMTU() - device: 12:34:56:78:90:ab mtu: 517
            D/BluetoothGatt: onConfigureMTU() - Device=12:34:56:78:90:ab mtu=247 status=0
            W/Ble MTU:: ATT MTU changed to 247, success: true
           requesting 527 on ble 5 will get no reply. always assume the minimal 23.
         */

        /* char read */
        override fun onCharacteristicRead(gatt: BluetoothGatt,
                                          characteristic: BluetoothGattCharacteristic,
                                          status: Int )
        {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(LOG_PREFIX,
                            "BluetoothGattCallback: " +
                            "Read characteristic $uuid:\n${value.toHexString()}"
                        )
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e(LOG_PREFIX,
                            "BluetoothGattCallback: Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(LOG_PREFIX,
                            "BluetoothGattCallback: " +
                            "Characteristic read failed for $uuid, error: $status"
                        )
                    }
                }
            }
        }

        /* char write */
        override fun onCharacteristicWrite(gatt: BluetoothGatt,
                                           characteristic: BluetoothGattCharacteristic,
                                           status: Int)
        {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(LOG_PREFIX,
                            "BluetoothGattCallback: " +
                            "Wrote to characteristic $uuid | value: ${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e(LOG_PREFIX,
                            "BluetoothGattCallback: Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e(LOG_PREFIX,
                            "BluetoothGattCallback: Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e(LOG_PREFIX,
                            "BluetoothGattCallback: " +
                            "Characteristic write failed for $uuid, error: $status")
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt,
                                             characteristic: BluetoothGattCharacteristic)
        {
            with(characteristic) {
                Log.i(LOG_PREFIX,
                    "BluetoothGattCallback: " +
                    "Characteristic $uuid changed | value: ${value.toHexString()}")
                data_read_record = value.toHexString()
            }
        }
    }

    // ... somewhere outside BluetoothGattCallback
    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

    /* extentions to gatt characteristics */
    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    //fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
    //    return properties and property != 0
    //}

    fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean =
        properties and property != 0

    /* data rw */
    private fun bleDataRead_user() {
        val userDataServiceUuid = UUID.fromString( ble_data.getCharSvc() )
        val userDataRdCharUuid = UUID.fromString( ble_data.getCharRd() )
        val conn_gatt = connect_gatt
        if ( conn_gatt == null ) {
            Log.e(LOG_PREFIX,"Ble data: gatt null")
            return
        }
        val uRdChar = conn_gatt
                .getService(userDataServiceUuid)?.getCharacteristic(userDataRdCharUuid)
        if (uRdChar?.isReadable() == true) {
            conn_gatt.readCharacteristic(uRdChar)
        } else {
            Log.e(LOG_PREFIX,"Ble data: gatt char null or non-readable")
            return
        }
    }

    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        /* WRITE_TYPE_DEFAULT for acknowledged write */
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }

        connect_gatt?.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        } ?: error("Not connected to a BLE device!")
    }

    private fun bleDataWrite_user() {
        val userDataServiceUuid = UUID.fromString( ble_data.getCharSvc() )
        val userDataWrCharUuid = UUID.fromString( ble_data.getCharWr() )
        val conn_gatt = connect_gatt
        if ( conn_gatt == null ) {
            Log.e(LOG_PREFIX,"Ble data: gatt null")
            return
        }
        val uWrChar = conn_gatt
            .getService(userDataServiceUuid)?.getCharacteristic(userDataWrCharUuid)
        if (uWrChar?.isWritable() == true) {
            //var wr_data:ByteArray = byteArrayOf(0x21, 0x22, 0x23, 0x41, 0x42, 0x43)
            var wr_data:ByteArray = ble_data.getCharWrData()
            writeCharacteristic(uWrChar, wr_data)
        } else {
            Log.e(LOG_PREFIX,"Ble data: gatt char null or non-readable")
            return
        }
    }

    fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        connect_gatt?.let { gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        } ?: error("Not connected to a BLE device!")
    }

    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString( ble_data.getCharRdCCC() /*CCC_DESCRIPTOR_UUID*/)
        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e(LOG_PREFIX,
                    "ConnectionManager: " +
                    "${characteristic.uuid} doesn't support notifications/indications")
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (connect_gatt?.setCharacteristicNotification(characteristic, true) == false) {
                Log.e(LOG_PREFIX,
                    "ConnectionManager: " +
                    "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e(LOG_PREFIX,
            "ConnectionManager: " +
            "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }

    fun enable_user_notif() {
        val userDataServiceUuid = UUID.fromString( ble_data.getCharSvc() )
        val userDataRdCharUuid = UUID.fromString( ble_data.getCharRd() )
        val conn_gatt = connect_gatt
        if ( conn_gatt == null ) {
            Log.e(LOG_PREFIX,"Ble data: gatt null")
            return
        }
        val uRdChar = conn_gatt
            .getService(userDataServiceUuid)?.getCharacteristic(userDataRdCharUuid)
        if ( uRdChar != null ) {
            enableNotifications(uRdChar)
        } else {
            Log.e(LOG_PREFIX,"Ble data: read char null")
        }
    }

    fun disableNotifications(characteristic: BluetoothGattCharacteristic) {
        if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
            Log.e(LOG_PREFIX,
                "ConnectionManager: " +
                "${characteristic.uuid} doesn't support indications/notifications")
            return
        }

        /* the CCC desc will be: "00002902-0000-1000-8000-00805f9b34fb" */
        val cccdUuid = UUID.fromString( ble_data.getCharRdCCC() /*CCC_DESCRIPTOR_UUID*/)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (connect_gatt?.setCharacteristicNotification(characteristic, false) == false) {
                Log.e(LOG_PREFIX,
                    "ConnectionManager: " +
                    "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        } ?: Log.e(LOG_PREFIX,
            "ConnectionManager: " +
            "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }


    @Synchronized
    private fun enqueueOperation(operation: BleOperationType) {
        operationQueue.add(operation)
        if (pendingOperation == null) {
            doNextOperation()
        }
    }

    @Synchronized
    private fun doNextOperation() {
        if (pendingOperation != null) {
            Log.e(LOG_PREFIX,
                "ConnectionManager: " +
                "doNextOperation() called when an operation is pending! Aborting.")
            return
        }

        val operation = operationQueue.poll() ?: run {
            Log.w(LOG_PREFIX,
                "ConnectionManager: Operation queue empty, returning")
            return
        }
        pendingOperation = operation

        when (operation) {
            //is Connect -> // operation.device.connectGatt(...)
            //is Disconnect -> // ...
            //is CharacteristicWrite -> // ...
            //is CharacteristicRead -> // ...
            // ...
        }
    }

    @Synchronized
    private fun signalEndOfOperation() {
        Log.d(LOG_PREFIX,"ConnectionManager: End of $pendingOperation")
        pendingOperation = null
        if (operationQueue.isNotEmpty()) {
            doNextOperation()
        }
    }

    private fun promptEnableBluetooth(activity: AppCompatActivity) {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    fun on_resume_check(activity: AppCompatActivity) {
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth(activity)
        }
    }

    fun get_progress_connect_trigger():Boolean {
        return connect_trigger_request
    }

    fun get_progress_connect_failed():Boolean {
        return connect_trigger_failed
    }

    fun get_progress_connect_ok():Boolean {
        if ( connect_gatt != null ) {
            return true
        }
        return false
    }

    fun get_progress_received_data():String? {
        return data_received_history
    }

    fun progress_ble_actions():BleResultDataWork_c? {
        val state_tmp: BleStateType? = state_current
        if ( state_tmp == null ) {
            val state_new_init: BleStateType = BleStateType()
            state_new_init.state_idx = 1

            scan_result_data = BleResultData_c()
            val s_data = scan_result_data
            if (s_data != null) {
                scan_result_work = BleResultDataWork_c(s_data)
            }

            state_current = state_new_init

        } else if ( state_tmp.state_idx == 1 ) {
            Log.w(LOG_PREFIX, "Ble scan: BLE enabled ?")
            val state_new = BleStateType()
            if ( bluetoothAdapter.isEnabled ) {
                state_new.state_idx = 2 // next state
                state_tmp.result_ok = true
                Log.w(LOG_PREFIX, "Ble scan: BLE enabled OK!")
            } else {
                state_new.state_idx = 999 // next state. fail.
                Log.w(LOG_PREFIX, "Ble scan: BLE enabled FAILURE!")
            }
            state_previous = state_tmp
            state_current = state_new

        } else if ( state_tmp.state_idx == 2 ) {
            Log.w(LOG_PREFIX, "Ble scan: Location permission?")
            val state_new = BleStateType()
            if ( isLocationPermissionGranted ) {
                state_new.state_idx = 3 // next state
                state_tmp.result_ok = true
                Log.w(LOG_PREFIX, "Ble scan: Location permission granted OK!")
            } else {
                state_new.state_idx = 999 // next state. fail.
                Log.w(LOG_PREFIX, "Ble scan: Location permission FAILURE!")
            }
            state_previous = state_tmp
            state_current = state_new

        } else if ( state_tmp.state_idx == 3 ) {
            Log.w(LOG_PREFIX, "Ble scan: Scanning ...")
            if (state_tmp.state_phase == 0) {
                scan_result_work?.clear_devices_list()
                Log.w(LOG_PREFIX, "Ble scan: Start scanning ...")
                bleScanner.startScan(null, scanSettings, scanCallback)
                is_scanning_in = true
                state_tmp.state_phase = 1
            } else if (state_tmp.state_phase == 1) {
                val cond:Boolean = scan_result_work?.get_devices_is_valid()?:false
                if ( cond ) {
                    bleScanner.stopScan(scanCallback)
                    is_scanning_in = false

                    val state_new = BleStateType()
                    state_new.state_idx = 4 // next state
                    state_tmp.result_ok = true
                    Log.w(LOG_PREFIX, "Ble scan: Scanning finished OK!")
                    state_previous = state_tmp
                    state_current = state_new
                } else {
                    //if (scanning failed) {
                    //    state_new.state_idx = 999 // next state. fail.
                    //    Log.w("Ble scan: ", "Location permission FAILURE!")
                    //    state_previous = state_tmp
                    //    state_current = state_new
                    //}
                }
            }

        } else if ( state_tmp.state_idx == 4 ) {
            Log.w(LOG_PREFIX, "Ble scan: Connecting ...")
            if (state_tmp.state_phase == 0) {

                val dev_list:MutableList<BleCenter_c>? = scan_result_work?.get_devices_list()?:null
                val dev_list_valid:Boolean = scan_result_work?.get_devices_is_valid()?:false
                val dev_sel:Int = scan_result_work?.get_selected_device_index()?:-1
                val sz = dev_list?.size?:0

                var dev:BleCenter_c? = null
                if ( sz > 0 && dev_sel >= 1 && dev_sel <= sz && dev_list_valid ) {
                    dev = dev_list?.get(dev_sel-1) ?: null
                }

                if (dev != null) {
                    with( dev.device ) {
                        /* search: android connectGatt context
                           ref: https://stackoverflow.com/questions/56642912/why-android-bluetoothdevice-conenctgatt-require-context-if-it-not-use-it
                         */
                        connectGatt(null, false, gattCallback,
                                    /* search: android ble error 133
                                     * medium.com: making android ble work -- part 2
                                     * stackoverflow.com: android-bluetoothgatt-status-133-register-callback
                                     * github.com/dotintent/FlutterBleLib/issues/565: need api 23
                                     */
                                    BluetoothDevice.TRANSPORT_LE)
                    }
                    connect_trigger_request = true
                    Log.w(LOG_PREFIX, "Ble scan: " +
                            "Trigger connecting ... idx ${dev_sel} ... " +
                            "${dev.device_address} ${dev.device_name} ")
                    state_tmp.state_phase = 1
                } else {
                    //val state_new = BleStateType()
                    //state_new.state_idx = 999 // next state
                    //Log.w("Ble scan: ", "Trigger connecting previous FAILURE!")
                    //state_previous = state_tmp
                    //state_current = state_new
                }
            } else if (state_tmp.state_phase == 1) {
                if ( connect_gatt != null ) {
                    val state_new = BleStateType()
                    state_new.state_idx = 5 // next state
                    state_tmp.result_ok = true
                    Log.w(LOG_PREFIX, "Ble scan: Wait connecting finished OK!")
                    state_previous = state_tmp
                    state_current = state_new
                }
            }

        } else if ( state_tmp.state_idx == 5 ) {
            Log.w(LOG_PREFIX, "Ble discovery: Trigger discovery ...")
            if (state_tmp.state_phase == 0) {
                val conn_gatt_tmp = connect_gatt
                if (conn_gatt_tmp != null) {
                    service_discovered = false
                    conn_gatt_tmp.discoverServices()
                    state_tmp.state_phase = 1
                } else {
                    val state_new = BleStateType()
                    state_new.state_idx = 999 // next state
                    Log.w(LOG_PREFIX, "Ble discovery: Trigger discovery previous FAILURE!")
                    state_previous = state_tmp
                    state_current = state_new
                }
            } else if (state_tmp.state_phase == 1) {
                if ( service_discovered ) {
                    val state_new = BleStateType()
                    state_new.state_idx = 6 // next state
                    state_tmp.result_ok = true
                    Log.w(LOG_PREFIX, "Ble discovery: Wait discovery finished OK!")
                    state_previous = state_tmp
                    state_current = state_new
                }
            }

        } else if ( state_tmp.state_idx == 6 ) {
            Log.w(LOG_PREFIX, "Ble mtu: Trigger mtu ...")
            if (state_tmp.state_phase == 0) {
                val conn_gatt_tmp = connect_gatt
                if ( conn_gatt_tmp != null ) {
                    service_mtu = 0 /* invaid */
                    conn_gatt_tmp.requestMtu(GATT_MAX_MTU_SIZE)
                    state_tmp.state_phase = 1
                } else {
                    val state_new = BleStateType()
                    state_new.state_idx = 999 // next state
                    Log.w(LOG_PREFIX, "Ble mtu: Trigger mtu previous FAILURE!")
                    state_previous = state_tmp
                    state_current = state_new
                }
            } else if (state_tmp.state_phase == 1) {
                if ( service_mtu > 0 ) {
                    val state_new = BleStateType()
                    state_new.state_idx = 7 // next state
                    state_tmp.result_ok = true
                    Log.w(LOG_PREFIX, "Ble mtu: Wait mtu finished OK!")
                    state_previous = state_tmp
                    state_current = state_new
                }
            }

        } else if ( state_tmp.state_idx == 7 ) {
            Log.w(LOG_PREFIX, "Ble subscription: Trigger subscription ...")
            var failed = 0
            if (state_tmp.state_phase == 0) {
                val conn_gatt_tmp = connect_gatt
                if ( conn_gatt_tmp != null ) {
                    enable_user_notif()
                    state_tmp.state_phase = 1
                } else {
                    failed = 1
                }
            } else if (state_tmp.state_phase != 0) {
                failed = 1
            }
            val state_new = BleStateType()
            if ( failed == 0 ) {
                state_new.state_idx = 8 // next state
                state_tmp.result_ok = true
                Log.w(LOG_PREFIX, "Ble mtu: Wait subscription finished OK!")
            } else {
                state_new.state_idx = 999 // next state
                Log.w(LOG_PREFIX, "Ble mtu: Wait subscription FAILURE!")
            }
            state_previous = state_tmp
            state_current = state_new

        } else if ( state_tmp.state_idx == 8 ) {
            Log.w(LOG_PREFIX, "Ble data: Trigger data read and write ...")
            var failed = false
            if (state_tmp.state_phase == 0) {
                val conn_gatt_tmp = connect_gatt
                if ( conn_gatt_tmp != null ) {
                    data_read_record = null /* init to invaid */
                    Log.w(LOG_PREFIX, "Ble data: Trigger write ...")
                    //bleDataRead_user()
                    bleDataWrite_user()
                    state_tmp.state_phase = 1
                } else {
                    failed = true
                }
            } else if (state_tmp.state_phase == 1) {
                Log.w(LOG_PREFIX, "Ble data: Wait data read ...")
                if ( data_read_record != null ) {
                    Log.w(LOG_PREFIX, "Ble data: Received data ...")
                    if (data_received_history != null) {
                        data_received_history += data_read_record
                    } else {
                        data_received_history = data_read_record
                    }
                    data_read_record = null
                }
            }
            if ( failed ) {
                val state_new = BleStateType()
                state_new.state_idx = 999 // next state
                Log.w(LOG_PREFIX, "Ble data: Data failed with FAILURE!")
                state_previous = state_tmp
                state_current = state_new
            }

        } else if ( state_tmp.state_idx == 999 ) {
            if ( state_tmp.state_phase != 999 ) {
                Log.e(LOG_PREFIX, "Ble state: " +
                      "Previous state ${state_previous?.state_idx?:-9992} " +
                              "phase ${state_previous?.state_phase?:-9992}")
                state_tmp.state_phase = 999
            }
        } else {
            Log.e(LOG_PREFIX, "Ble state: " +
                "Previous state ${state_previous?.state_idx?:-9993} " +
                        "phase ${state_previous?.state_phase?:-9993}")
            Log.e(LOG_PREFIX,"Ble state: " +
                "Current state ${state_current?.state_idx?:-9993} " +
                        "phase ${state_current?.state_phase?:-9993}")
            val state_new = BleStateType()
            state_new.state_idx = 999 // next state
            Log.w(LOG_PREFIX, "Ble state: Unknown state  failed with FAILURE!")
            state_previous = state_tmp
            state_current = state_new
        }

        return scan_result_work
    }


    // ref[2]:
    private var start_count = 0

    // execution of service will start
    // on calling this method
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        start_count ++
        if ( start_count <= 0 ) { /* avoid wrapping and 0 */
            start_count = 1
        }

        // creating a media player which
        // will play the audio of Default
        // ringtone in android device
        ///player = MediaPlayer.create(this, Settings.System.DEFAULT_RINGTONE_URI)

        // providing the boolean
        // value as true to play
        // the audio on loop
        ///player.setLooping(true)

        // starting the process
        ///player.start()

        if ( start_count >= 3 ) {
            progress_ble_actions()
        }
        Log.w(LOG_PREFIX, "User service: " + String.format("on-start() ${start_count}"))

        // returns the status
        // of the program
        return START_STICKY
    }

    // execution of the service will
    // stop on calling this method
    override fun onDestroy() {
        super.onDestroy()

        // stopping the process
        ///player.stop()
        if ( is_scanning_in ) {
            bleScanner.stopScan(scanCallback)
            is_scanning_in = false
        }

        val connect_gatt_tmp = connect_gatt
        if ( connect_gatt_tmp != null ) {
            connect_gatt_tmp.close()
            connect_gatt = null
            connect_trigger_request = false
        }

        Log.w(LOG_PREFIX, "User service: on-destroy()")
    }


    // ref[3]:
    /**
     * Called when service is created So  we will do our work here
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(LOG_PREFIX, "MyBoundService: onCreate called")

        //startNotification()

        //Handler().postDelayed({
        //    val randomNumber = mGenerator.nextInt(100)
        //    randomNumberLiveData.postValue(randomNumber)
        //}, 1000)
    }

}
