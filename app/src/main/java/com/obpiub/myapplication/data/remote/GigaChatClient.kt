package com.obpiub.myapplication.data.remote

import com.obpiub.myapplication.BuildConfig
import com.obpiub.myapplication.data.repository.GigaChatBalanceRepository
import com.obpiub.myapplication.domain.repository.BalanceRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GigaChatClient {
    fun createBalanceRepository(): BalanceRepository {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
        fun retrofit(baseUrl: String) = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val auth = retrofit("https://ngw.devices.sberbank.ru:9443/").create(GigaChatAuthApi::class.java)
        val api = retrofit("https://api.giga.chat/").create(GigaChatApi::class.java)
        return GigaChatBalanceRepository(api, GigaChatTokenProvider(
            auth, BuildConfig.GIGACHAT_AUTH_KEY, BuildConfig.GIGACHAT_SCOPE,
        ))
    }
}