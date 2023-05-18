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
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.*
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import workflow.onech.database.DB
import workflow.onech.database.Entity
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt


class FKNService : Service() {
    val messages = mutableListOf<Message>()
    private val db by lazy { DB.getDatabase(this).messagesDAO() }
    private val networkScope = CoroutineScope(Dispatchers.IO)
    private val retrofit = Retrofit.Builder()
        .baseUrl("") // URL for server goes here
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(
            JacksonConverterFactory.create(
                JsonMapper
                    .builder()
                    .serializationInclusion(JsonInclude.Include.NON_NULL)
                    .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .build()
                    .registerModule(KotlinModule.Builder().build())
            )
        )
        .build()

    private val networkWalker = retrofit.create(RetrofitInterface::class.java)

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
            fun code() {
                try {
                    val answer = networkWalker.sendTextMessage(json).execute()
                    println(answer)
                    if (answer.code() != 200) {
                        makeToast("Can't send your message. Try again later!")
                    }
                } catch (_: Exception) {
                    makeToast("Can't send your message. Try again later!")
                }
            }
            networkScope.launch { code() }
        }
    }

    private val sPhotoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            fun code() {
                val stringUri = p1!!.getStringExtra("uri")!!

                val uri = Uri.parse(stringUri)

                @Suppress("DEPRECATION")
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }

                val name = "${System.currentTimeMillis()}.png"
                val picture = File(this@FKNService.cacheDir, name)
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

                val requestBody =
                    RequestBody.create(MediaType.parse("multipart/form-data"), picture)
                val body = MultipartBody.Part.createFormData("picture", name, requestBody)
                val json = RequestBody.create(
                    MediaType.parse("application/json"),
                    "{\"from\":\"${p0!!.getString(R.string.USERNAME)}\"}"
                )

                try {
                    val answer = networkWalker.sendImageMessage(body, json).execute().code()
                    println(answer)
                    if (answer != 200) {
                        when (answer) {
                            413 -> makeToast("Image is too Big")
                            else -> makeToast("Sorry, something went wrong. Try again later!")
                        }
                    }
                } catch (_: Exception) {
                    makeToast("Sorry, something went wrong. Try again later!")
                }
            }
            networkScope.launch { code() }
        }
    }

    private fun makeToast(text: String) {
        val intent = Intent("Toast")
        intent.putExtra("text", text)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private val receiveMessages = networkScope.launch(start = CoroutineStart.LAZY) {
        while (isActive) {
            kotlin.runCatching {
                try {
                    val call = if (messages.isNotEmpty()) {
                        networkWalker.getMessages(messages.last().id!!.toInt()).execute()
                    } else {
                        networkWalker.getMessages(0).execute()
                    }
                    if (call.code() != 200) {
                        makeToast("Cant Download new Messages")
                    } else {
                        val tmp = call.body()
                        tmp!!.forEach {
                            if (it.message.image?.link != null) {
                                val byteArray =
                                    networkWalker.getByteArrayForImage(it.message.image.link.toString())
                                        .execute().body()!!.bytes()
                                val highResBitmap =
                                    BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

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
                                val entity =
                                    Entity(0, it.user, it.date!!, it.message.text!!.text, null)
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
                    }
                } catch (_: Exception) {
                    makeToast("Cant Download new Messages")
                } finally {
                    delay(2000L)
                }
            }
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
        fun code() {
            db.getAll().forEach { messages.add(it.toMessage()) }
            receiveMessages.start()
        }
        networkScope.launch { code() }

    }

    override fun onDestroy() {
        super.onDestroy()
        networkScope.cancel()
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
