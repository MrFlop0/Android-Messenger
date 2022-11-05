package workflow.onech

import android.text.InputType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.*
import retrofit2.http.*

interface RetrofitInterface {
    @GET("1ch?limit=100")
    fun getMessages(@Query("lastKnownId") id: Int): Call<List<Message>>

    @GET("/img/{path}")
    fun getByteArrayForImage(@Path("path") path: String): Call<ResponseBody>

    @Headers("Content-Type: application/json;charset=UTF-8")
    @POST("1ch")
    fun sendTextMessage(@Body json: String): Call<Int>

    @Multipart
    @POST("1ch")
    fun sendImageMessage(@Part image: MultipartBody.Part, @Part("msg") json: RequestBody): Call<Int>
}