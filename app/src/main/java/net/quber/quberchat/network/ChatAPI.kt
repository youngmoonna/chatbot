package net.quber.quberchat.network

import net.quber.quberchat.data.RequestMsg
import net.quber.quberchat.data.RequestTTS
import net.quber.quberchat.data.ResponseMsg
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ChatAPI {

    @POST("/llm/chat")
    fun llm_chat(
        @Body messages: RequestMsg
    ): Call<ResponseMsg>

    @POST("/tts/generate-tts")
    fun generate_tts(
        @Body requestTTS: RequestTTS
    ): Call<ResponseBody>
}