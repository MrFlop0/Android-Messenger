package workflow.onech.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import workflow.onech.HighResImage
import workflow.onech.Message
import workflow.onech.R
import java.io.File
import java.lang.Thread.sleep
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


class MessageAdapter(private val context: Context, private val values: List<Message>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name = view.findViewById<TextView>(R.id.user)!!
        val messageText = view.findViewById<TextView>(R.id.message)!!
        val date = view.findViewById<TextView>(R.id.date)!!
        val wholeMessage = view.findViewById<LinearLayout>(R.id.whole_message)!!
    }

    class ViewHolderI(view: View) : RecyclerView.ViewHolder(view) {
        val name = view.findViewById<TextView>(R.id.user)!!
        val messageImage = view.findViewById<ImageView>(R.id.message)!!
        val date = view.findViewById<TextView>(R.id.date)!!
        val wholeMessage = view.findViewById<LinearLayout>(R.id.whole_message)!!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            0 -> {
                ViewHolderI(
                    LayoutInflater.from(context).inflate(R.layout.image_message, parent, false)
                )
            }
            else -> {
                ViewHolder(
                    LayoutInflater.from(context).inflate(R.layout.text_message, parent, false)
                )
            }
        }
    }


    override fun getItemViewType(position: Int): Int {
        return if (values[position].message.image == null) {
            1
        } else {
            0
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = values[position]


        when (getItemViewType(position)) {
            1 -> {
                val newHolder = holder as ViewHolder
                newHolder.name.text = message.user
                newHolder.messageText.text = message.message.text!!.text
                val a = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ENGLISH)
                newHolder.date.text = a.format(Date(message.date!!.toLong()))

                if (message.user == context.getString(R.string.USERNAME)) { // check that message is from our user
                    val background =
                        AppCompatResources.getDrawable(context, R.drawable.background_message_user)
                    val newBackground = background?.let { DrawableCompat.wrap(it) }
                    newHolder.wholeMessage.background = newBackground
                } else {
                    val background =
                        AppCompatResources.getDrawable(context, R.drawable.background_message)
                    val newBackground = background?.let { DrawableCompat.wrap(it) }
                    newHolder.wholeMessage.background = newBackground
                }

            }
            else -> {
                val newHolder = holder as ViewHolderI
                newHolder.name.text = message.user

                if (message.message.image?.image == null) {
                    val name = message.message.image?.link?.replace("/", ".").toString()
                    val f = File(context.cacheDir, name)
                    if (f.exists()) {
                        val highResBitmap =
                            BitmapFactory.decodeFile(f.absolutePath)
                        val proportion =
                            highResBitmap.width.toFloat() / highResBitmap.height.toFloat()
                        message.message.image?.image = Bitmap.createScaledBitmap(
                            highResBitmap,
                            400,
                            (400 / proportion).roundToInt(),
                            false
                        )
                    }
                    newHolder.messageImage.setImageBitmap(message.message.image?.image)
                } else {
                    newHolder.messageImage.setImageBitmap(message.message.image.image)
                }

                val a = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ENGLISH)
                newHolder.date.text = a.format(Date(message.date!!.toLong()))

                if (message.user == context.getString(R.string.USERNAME)) {
                    val background =
                        AppCompatResources.getDrawable(context, R.drawable.background_message_user)
                    val newBackground = background?.let { DrawableCompat.wrap(it) }
                    newHolder.wholeMessage.background = newBackground
                } else {
                    val background =
                        AppCompatResources.getDrawable(context, R.drawable.background_message)
                    val newBackground = background?.let { DrawableCompat.wrap(it) }
                    newHolder.wholeMessage.background = newBackground
                }

                newHolder.messageImage.setOnClickListener {
                    Thread {
                        val launch = Intent(context, HighResImage::class.java)
                        context.startActivity(launch)

                        sleep(1000)

                        val intent = Intent("Loaded")
                        intent.putExtra("link", message.message.image?.link)
                        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                    }.start()
                }
            }
        }

    }

    override fun getItemCount(): Int {
        return values.size
    }


}
