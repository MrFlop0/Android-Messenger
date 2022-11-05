package workflow.onech

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import workflow.onech.adapters.MessageAdapter


class MainActivity : AppCompatActivity() {
    lateinit var recycler: RecyclerView
    private var service: FKNService? = null
    private var isBound = false

    private val launchSomeActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val selectedImageUri: Uri? = data!!.data

            val intent = Intent("SendPhoto")
            intent.putExtra("uri", selectedImageUri.toString())
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)


        }
    }

    private val boundServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val binderBridge = binder as FKNService.MyBinder
            service = binderBridge.getMyService()
            isBound = true
            createRecycler()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            service = null
        }

    }

    private val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val from = intent.getIntExtra("from", 0)
            val to = intent.getIntExtra("till", 0)
            recycler.adapter?.notifyItemRangeInserted(from, to)
        }
    }

    private val toastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val warning =
                Toast.makeText(this@MainActivity, intent.getStringExtra("text"), Toast.LENGTH_SHORT)
            warning.setGravity(Gravity.BOTTOM, 0, 0)
            warning.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = Intent(this, FKNService::class.java)
        startService(intent)
        bindService(intent, boundServiceConnection, BIND_AUTO_CREATE)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            mMessageReceiver, IntentFilter("Update")
        )

        LocalBroadcastManager.getInstance(this).registerReceiver(
            toastReceiver, IntentFilter("Toast")
        )

    }

    private fun createRecycler() {
        recycler = findViewById(R.id.RV)
        recycler.apply {
            adapter = MessageAdapter(this@MainActivity, service!!.messages)
            layoutManager =
                LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver)
        if (isBound) {
            unbindService(boundServiceConnection)
        }
        val intent = Intent(this, FKNService::class.java)
        stopService(intent)
    }

    fun paperClipFunction(view: View) {
        val i = Intent()
        i.type = "image/*"
        i.action = Intent.ACTION_GET_CONTENT

        launchSomeActivity.launch(i)
    }


    fun sendFunction(view: View) {

        val editText = findViewById<TextView>(R.id.ET)
        val text = Text(editText.text.toString())
        editText.text = ""
        val data = Data(null, text)
        val message = Message(user = this.getString(R.string.USERNAME), message = data)

        val mapper = JsonMapper
            .builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build()
            .registerModule(KotlinModule.Builder().build())

        val json = mapper.writeValueAsString(message)

        val intent = Intent("Send")
        intent.putExtra("json", json)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun scrollDown(view: View) {
        recycler.scrollToPosition(recycler.adapter!!.itemCount - 1)
    }

}