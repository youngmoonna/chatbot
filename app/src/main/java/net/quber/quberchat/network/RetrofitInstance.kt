package net.quber.quberchat.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {
//    var BASE_URL = "http://221.165.27.101:7847/"
    var BASE_URL = "http://221.165.27.101:46831/"

    val client = Retrofit
        .Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .connectTimeout(1000 * 30L, TimeUnit.MILLISECONDS)
                .readTimeout(1000 * 30L, TimeUnit.MILLISECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }).build()
        )
        .build()

    fun getInstance(): Retrofit{
        return client
    }
}