package workflow.onech

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import workflow.onech.database.DB
import workflow.onech.database.Entity
import java.io.*
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import kotlin.math.roundToInt


class FKNService : Service() {
    val messages = mutableListOf<Message>()
    private val db by lazy { DB.getDatabase(this).messagesDAO() }

    private fun Entity.toMessage(): Message {
        return Message(
            id,
            user,
            Data(
                if (image == null) null else Image(image),
                Text(text)
            ),
            date
        )
    }

    private val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val json = intent.getStringExtra("json")!!.replaceFirst("text", "Text")
            Thread {
                val connection: HttpURLConnection
                val url = URL("http://213.189.221.170:8008/1ch")

                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    doInput = true
                }
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                connection.connect()

                connection.outputStream.use { it.write(json.toByteArray()) }
                println(connection.responseCode)
                connection.disconnect()
            }.start()
        }
    }

    private val sPhotoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            Thread {
                val stringUri = p1!!.getStringExtra("uri")!!

                val uri = Uri.parse(stringUri)

                @Suppress("DEPRECATION")
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }

                val url = URL("http://213.189.221.170:8008/1ch")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    doInput = true
                    doOutput = true
                }
                val boundary = "------" + System.currentTimeMillis() + "------"
                connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=$boundary"
                )

                val CRLF = "\r\n"
                val json = "{\"from\":\"MrFlop0\"}"
                val picture = File(this@FKNService.cacheDir, "${System.currentTimeMillis()}.png")
                picture.createNewFile()
                try {
                    val out = FileOutputStream(picture)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 0, out)

                    out.flush()
                    out.close()
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()

                } catch (e: IOException) {
                    e.printStackTrace()
                }

                val outputStream = connection.outputStream
                val outputStreamWriter = OutputStreamWriter(outputStream)
                outputStream.use {
                    outputStreamWriter.use {
                        with(it) {
                            append("--").append(boundary).append(CRLF)
                            append("Content-Disposition: form-data; name=\"json\"").append(CRLF)
                            append("Content-Type: application/json; charset=utf-8").append(CRLF)
                            append(CRLF)
                            append(json).append(CRLF)
                            flush()
                            appendFile(picture, boundary, outputStream)
                            append(CRLF)
                            append("--").append(boundary).append("--").append(CRLF)
                        }
                    }
                }

                picture.delete()
                println(connection.responseCode)
                connection.disconnect()

            }.start()
        }

    }

    private fun OutputStreamWriter.appendFile(
        file: File,
        boundary: String,
        outputStream: OutputStream,
        crlf: String = "\r\n"
    ) {
        val contentType = URLConnection.guessContentTypeFromName(file.name)
        val fis = FileInputStream(file)
        fis.use {
            append("--").append(boundary).append(crlf)
            append("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"")
            append(crlf)
            append("Content-Type: $contentType").append(crlf)
            append("Content-Length: ${file.length()}").append(crlf)
            append("Content-Transfer-Encoding: binary").append(crlf)
            append(crlf)
            flush()

            val buffer = ByteArray(4096)

            var n: Int
            while (fis.read(buffer).also { n = it } != -1) {
                outputStream.write(buffer, 0, n)
            }
            outputStream.flush()
            append(crlf)
            flush()
        }
    }

    val handlerLoop = Handler(Looper.getMainLooper())
    private val updateFun = object : Runnable {
        override fun run() {

            Thread {
                try {
                    val connection: HttpURLConnection
                    val url: URL = if (messages.isNotEmpty()) {
                        URL("http://213.189.221.170:8008/1ch?limit=1000&lastKnownId=${messages.size + 1}")
                    } else {
                        URL("http://213.189.221.170:8008/1ch?limit=1000")
                    }


                    connection = url.openConnection() as HttpURLConnection
                    connection.connect()
                    var inputStream: String

                    connection.inputStream.use { it ->
                        it.bufferedReader().use { inputStream = it.readText() }
                    }

                    val mapper = JsonMapper
                        .builder()
                        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        .build()
                        .registerModule(KotlinModule.Builder().build())

                    val tmp = mapper.readValue<MutableList<Message>>(inputStream)
                    tmp.forEach {
                        if (it.message.image?.link != null) {
                            val url1 =
                                URL(" http://213.189.221.170:8008/img/${it.message.image.link}")
                            val highResBitmap = BitmapFactory.decodeStream(url1.openStream())

                            val proportion =
                                highResBitmap.width.toFloat() / highResBitmap.height.toFloat()
                            it.message.image.image = Bitmap.createScaledBitmap(
                                highResBitmap,
                                400,
                                (400 / proportion).roundToInt(),
                                false
                            )

                            val name = it.message.image.link!!.replace("/", ".") + ".png"
                            it.message.image.link = name
                            val image =
                                File(this@FKNService.cacheDir, name)
                            image.createNewFile()

                            try {
                                val out = FileOutputStream(image)
                                highResBitmap.compress(Bitmap.CompressFormat.PNG, 0, out)

                                out.flush()
                                out.close()
                            } catch (e: FileNotFoundException) {
                                e.printStackTrace()

                            } catch (e: IOException) {
                                e.printStackTrace()
                            }


                            val entity = Entity(0, it.user, it.date!!, null, name)
                            db.insert(entity)
                        } else {
                            val entity = Entity(0, it.user, it.date!!, it.message.text!!.text, null)
                            db.insert(entity)
                        }
                    }

                    if (tmp.isNotEmpty()) {
                        val from = messages.size
                        messages += tmp
                        val till = messages.size

                        val intent = Intent("Update")
                        intent.putExtra("from", from)
                        intent.putExtra("till", till)
                        LocalBroadcastManager.getInstance(this@FKNService).sendBroadcast(intent)
                    }
                    connection.disconnect()
                } catch (e: ConnectException) {
                } finally {
                    handlerLoop.postDelayed(this, 2000)
                }
            }.start()
        }
    }


    override fun onCreate() {
        super.onCreate()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            mMessageReceiver, IntentFilter("Send")
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            sPhotoReceiver, IntentFilter("SendPhoto")
        )
        checkDB()
    }

    private fun checkDB() {
        Thread {
            db.getAll().forEach { messages.add(it.toMessage()) }
            updateFun.run()
        }.start()

    }

    override fun onDestroy() {
        super.onDestroy()
        handlerLoop.removeCallbacks(updateFun)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sPhotoReceiver)
    }

    override fun onBind(p0: Intent?): IBinder {
        return MyBinder()
    }

    inner class MyBinder : Binder() {
        fun getMyService() = this@FKNService
    }
}