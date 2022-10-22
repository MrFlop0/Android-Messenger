package workflow.onech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException


class HighResImage : AppCompatActivity() {
    private lateinit var link: String


    private val pictureReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            val path = p1!!.getStringExtra("link")
            link = path!!.replace("/", ".")
            val picture = File(this@HighResImage.cacheDir, link)

            var fis: FileInputStream? = null
            try {
                fis = FileInputStream(picture)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
            val bitmap = BitmapFactory.decodeStream(fis)

            val image = findViewById<ImageView>(R.id.HighRes)
            image.setImageBitmap(bitmap)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.high_res)

        LocalBroadcastManager.getInstance(this@HighResImage)
            .registerReceiver(pictureReceiver, IntentFilter("Loaded"))

    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pictureReceiver)
    }


}