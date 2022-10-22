package workflow.onech

import android.graphics.Bitmap
import com.fasterxml.jackson.annotation.JsonProperty

data class Message(
    val id: Long?=null,
    @JsonProperty ("from")
    val user: String,
    @JsonProperty ("data")
    val message: Data,
    @JsonProperty ("time")
    val date: String?=null
)

data class Image(var link: String?=null, var image: Bitmap?=null)
data class Text(val text: String?=null)
data class Data(
    val image: Image?=null,
    val text: Text?=null
)


