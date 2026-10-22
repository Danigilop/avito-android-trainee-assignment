package com.obpiub.myapplication.data.remote

import com.obpiub.myapplication.domain.repository.BalanceException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class GigaChatTokenProvider(
    private val api: GigaChatAuthApi,
    private val authKey: String,
    private val scope: String,
    private val service: String = "GigaChat",
    private val currentTime: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var cached: AccessTokenDto? = null

    suspend fun getToken(): String = mutex.withLock {
        val key = authKey.trim().removePrefix("Basic ").trim()
        if (key.isEmpty()) throw BalanceException("Доступ к $service не настроен для этой сборки.")
        val token = cached
        if (token != null && (token.expiresAt ?: 0L) > currentTime() + 30_000) {
            return@withLock requireNotNull(token.accessToken)
        }
        val response = api.token("Basic $key", UUID.randomUUID().toString(), scope)
        if (response.accessToken.isNullOrBlank() || (response.expiresAt ?: 0L) <= currentTime()) {
            throw BalanceException("$service вернул некорректный токен доступа.")
        }
        cached = response
        response.accessToken
    }

    suspend fun invalidate(rejectedToken: String) = mutex.withLock {
        if (cached?.accessToken == rejectedToken) cached = null
    }
}
