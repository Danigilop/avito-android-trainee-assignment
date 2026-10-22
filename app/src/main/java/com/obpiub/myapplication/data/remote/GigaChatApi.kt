package com.obpiub.myapplication.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

interface GigaChatAuthApi {
    @FormUrlEncoded
    @Headers("Accept: application/json")
    @POST("api/v2/oauth")
    suspend fun token(
        @Header("Authorization") authorization: String,
        @Header("RqUID") requestId: String,
        @Field("scope") scope: String,
    ): AccessTokenDto
}

interface GigaChatApi {
    @Headers("Accept: application/json")
    @GET("v1/balance")
    suspend fun balance(@Header("Authorization") authorization: String): BalanceDto
}

data class AccessTokenDto(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("expires_at") val expiresAt: Long?,
)
data class BalanceDto(val balance: List<BalanceItemDto>?)
data class BalanceItemDto(val usage: String?, val value: Long?)