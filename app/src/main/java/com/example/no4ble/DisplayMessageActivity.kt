package com.example.no4ble

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView

const val LOG_PREFIX = "No2BLE"

class DisplayMessageActivity : AppCompatActivity() {

    // Variable for storing instance of our service class
    var mService: BleUtilOp? = null

    // Boolean to check if our activity is bound to service or not
    var mIsBound: Boolean? = null

    var mBleDataWorker:BleResultDataWork_c? = null

    /**
     * Method for listening to random numbers generated by our service class
     */
    private fun getRandomNumberFromService() {
        //mService?.randomNumberLiveData?.observe(this
        //    , Observer {
        //        resultTextView?.text = "Random number from service: $it"
        //    })
    }

    /**
     * Interface for getting the instance of binder from our service class
     * So client can get instance of our service class and can directly communicate with it.
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, iBinder: IBinder) {
            Log.d(LOG_PREFIX, "ServiceConnection: connected to service.")
            // We've bound to MyService, cast the IBinder and get MyBinder instance
            val binder = iBinder as BleUtilOp.MyBinder
            mService = binder.service
            mIsBound = true
            getRandomNumberFromService() // return a random number from the service
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.d(LOG_PREFIX, "ServiceConnection: disconnected from service.")
            mIsBound = false
            mBleDataWorker = null
        }
    }

    /**
     * Used to bind to our service class
     */
    private fun bindService() {
        Intent(this, BleUtilOp::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Used to unbind and stop our service class
     */
    private fun unbindService() {
        Intent(this, BleUtilOp::class.java).also { intent ->
            unbindService(serviceConnection)
        }
    }

    fun onClick(v: View?) {
        when (v?.id) {
            R.id.buttonStartService -> {
                click_start_count ++
                if ( click_path_state == 0 ) {
                    click_path_state = 1
                }
                bindService()
            }
            R.id.buttonStopService -> {
                if (mIsBound == true) {
                    unbindService()
                    mIsBound = false
                    mBleDataWorker = null
                }
                click_path_state = 0
            }
            //R.id.startActivityButton -> {
            //    val intent = Intent(this, ResultActivity::class.java)
            //    startActivity(intent)
            //}
            R.id.buttonSelectIdx -> {
                val editText = findViewById<EditText>(R.id.editTextTextPersonName2)
                val message = editText.text.toString()
                val ble_dev = mBleDataWorker
                if ( ble_dev != null ) {
                    ble_dev.select_device_by_index(message)
                }
            }
        }
    }


    val cfg_use_bound_service = true // false to use unbound


    private var call_cnt = 0
    private var call_msg = "msg0\n"
    private val call_this = this
    private var click_start_count = 0
    private var click_path_state = 0 /* track click path */
    private var click_connect_ok_count = 0
    private var click_connect_fail_count = 0
    private var click_data_ok_count = 0

    fun calculate_content():String {
        val numbers = listOf(1, 2, 3, 4, 5, 6)
        var retv = ""
        retv = retv + String.format("List: $numbers\n")
        retv = retv + String.format("Size: ${numbers.size}\n")

        val data_wkr:BleResultDataWork_c? = mBleDataWorker
        if ( data_wkr != null ) {
            val dlst:MutableList<BleCenter_c> = data_wkr.get_devices_list()
            val sz = dlst.size
            for ( idx in 0..(sz-1) ) {
                val tm_now = System.currentTimeMillis()
                val tm_diff = ((tm_now - dlst[idx].last_seen_ms)/1000).toInt()
                retv += String.format(" idx=${idx+1}, " +
                                        "addr=${dlst[idx].device_address}, " +
                                        "rssi=${dlst[idx].rssi.toInt()}, " +
                                        "count=${dlst[idx].result_count}, " +
                                        "age=${tm_diff}" +
                                        "\n            name=${dlst[idx].device_name}, " +
                                        "\n")
            }

            val dev_valid:Boolean = data_wkr.get_devices_is_valid()
            val dev_sel:Int = data_wkr.get_selected_device_index()

            retv = retv + String.format("\n")
            var vld:String = "list-invalid"
            if ( dev_valid ) vld = "list-valid"
            retv = retv + String.format(" ${vld}  sel=${dev_sel}, sz=${sz}\n")
            retv = retv + String.format("\n")

            val conn_trig:Boolean = mService?.get_progress_connect_trigger()?:false
            val conn_fail:Boolean = mService?.get_progress_connect_failed()?:false
            val conn_ok:Boolean = mService?.get_progress_connect_ok()?:false
            if ( conn_trig ) {
                if (conn_fail) {
                    retv = retv + String.format(" connection requested and failed\n")
                    retv = retv + String.format("\n")
                    if ( click_path_state == 1 ) {
                        click_connect_fail_count ++
                        click_path_state = 2
                    }
                } else if (conn_ok) {
                    retv = retv + String.format(" connection requested and connected ok\n")
                    retv = retv + String.format("\n")
                    if ( click_path_state == 1 ) {
                        click_connect_ok_count ++
                        click_path_state = 2
                    }
                } else {
                    retv = retv + String.format(" connection requested\n")
                    retv = retv + String.format("\n")
                }
            }
            val recv_data:String? = mService?.get_progress_received_data()
            if ( recv_data != null ) {
                retv = retv + String.format(" received data ${recv_data}\n")
                retv = retv + String.format("\n")
                if ( click_path_state == 2 ) {
                    click_data_ok_count ++
                    click_path_state = 3
                }
            }
        } else {
            retv = retv + String.format("data_worker: null\n")
        }

        retv = retv + String.format("\n")
        retv = retv + String.format("counts:  start=${click_start_count}, ")
        retv += String.format(" connect_fail=${click_connect_fail_count}, \n")
        retv += String.format("                             ")
        retv += String.format(" connect_ok=${click_connect_ok_count}, ")
        retv += String.format(" data_ok=${click_data_ok_count}\n")
        retv = retv + String.format("call_cnt: ${call_cnt}\n")
        call_cnt ++

        return retv
    }

    /* https://stackoverflow.com/questions/55570990/kotlin-call-a-function-every-second/ */
    private lateinit var mainHandler: Handler
    private val updateTextTask = object : Runnable {
        override fun run() {
            //minusOneSecond()
            val textView = findViewById<TextView>(R.id.textView)
            textView.apply {
                var text_to_set = call_msg + calculate_content()
                text = text_to_set
            }

            if ( ! cfg_use_bound_service ) {
                startService(Intent(call_this, BleUtilOp::class.java))
            } else {
                if ( this@DisplayMessageActivity.mIsBound == true ){
                    val tmp_wkr = mService?.progress_ble_actions()?:null
                    this@DisplayMessageActivity.mBleDataWorker = tmp_wkr
                } else {

                }
            }

            mainHandler.postDelayed(this, 1000)
            Log.w(LOG_PREFIX, "User message: handler-run()")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(updateTextTask)

        if ( ! cfg_use_bound_service ) {
            stopService(Intent(this, BleUtilOp::class.java))
        } else {
            if (mIsBound == true) {
                unbindService()
                mIsBound = false
                mBleDataWorker = null
            }
        }

        Log.w(LOG_PREFIX, "User message: onDestroy()")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        /*TODO */
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_message)

        // Get the Intent that started this activity and extract the string
        val message = intent.getStringExtra(EXTRA_MESSAGE)

        if ( ! cfg_use_bound_service ) {
            startService(Intent(this, BleUtilOp::class.java))
        }

        // Capture the layout's TextView and set the string as its text
        call_msg += "No4_BLE.app" + "\n" + message + "\n"
        call_cnt = 0
        val textView = findViewById<TextView>(R.id.textView)
        textView.apply {
            var text_to_set = call_msg + calculate_content()
            text = text_to_set
        }

        mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(updateTextTask)

        Log.w(LOG_PREFIX, "User message: onCreate()")

        /* modify the AndroidManifest.xml so that it contains parentActivityName:
        <activity
            android:name=".DisplayMessageActivity"
            android:parentActivityName=".MainActivity">
            <!-- The meta-data tag is required if you support API level 15 and lower -->
            <meta-data
                android:name="android.support.PARENT_ACTIVITY"
                android:value=".MainActivity" />
        </activity>
         */

    }
}
