package com.obpiub.myapplication.data.remote

import com.obpiub.myapplication.BuildConfig
import com.obpiub.myapplication.data.repository.RemoteVoiceRepository
import com.obpiub.myapplication.domain.repository.VoiceRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object VoiceClient {
    fun create(): VoiceRepository {
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build()
        fun retrofit(url: String) = Retrofit.Builder().baseUrl(url).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
        val auth = retrofit("https://ngw.devices.sberbank.ru:9443/").create(GigaChatAuthApi::class.java)
        return RemoteVoiceRepository(
            retrofit("https://api.giga.chat/").create(TaskGenerationApi::class.java),
            GigaChatTokenProvider(auth, BuildConfig.GIGACHAT_AUTH_KEY, BuildConfig.GIGACHAT_SCOPE),
            BuildConfig.GIGACHAT_MODEL,
        )
    }
}
