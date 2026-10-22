package com.obpiub.myapplication

import com.obpiub.myapplication.data.remote.*
import com.obpiub.myapplication.data.repository.GigaChatBalanceRepository
import com.obpiub.myapplication.domain.repository.BalanceException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

class GigaChatBalanceTest {
    private val server = MockWebServer()
    private lateinit var auth: GigaChatAuthApi
    private lateinit var api: GigaChatApi

    @Before
    fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build()
        auth = retrofit.create(GigaChatAuthApi::class.java)
        api = retrofit.create(GigaChatApi::class.java)
    }

    @After
    fun tearDown() { server.shutdown() }

    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun token(value: String) = response("""{"access_token":"$value","expires_at":100000}""")
    private fun balance() = response("""{"balance":[{"usage":"GigaChat","value":123},{"usage":"embeddings","value":0}]}""")
    private fun provider(key: String = "test-only") = GigaChatTokenProvider(auth, key, "GIGACHAT_API_PERS") { 1000L }

    @Test
    fun authorizationUsesFormAndUuidAndTokenIsReused() = runBlocking {
        server.enqueue(token("first"))
        server.enqueue(balance())
        server.enqueue(balance())
        val repository = GigaChatBalanceRepository(api, provider())
        assertEquals(listOf(123L, 0L), repository.getBalance().map { it.tokens })
        repository.getBalance()
        val authRequest = server.takeRequest()
        assertEquals("/api/v2/oauth", authRequest.path)
        assertEquals("POST", authRequest.method)
        assertEquals("Basic test-only", authRequest.getHeader("Authorization"))
        assertEquals(4, UUID.fromString(authRequest.getHeader("RqUID")).version())
        assertEquals("scope=GIGACHAT_API_PERS", authRequest.body.readUtf8())
        val request = server.takeRequest()
        assertEquals("/v1/balance", request.path)
        assertEquals("Bearer first", request.getHeader("Authorization"))
        assertEquals("Bearer first", server.takeRequest().getHeader("Authorization"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun unauthorizedBalanceRefreshesTokenOnlyOnce() = runBlocking {
        server.enqueue(token("old"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(token("new"))
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            GigaChatBalanceRepository(api, provider()).getBalance()
            fail("Должна вернуться ошибка авторизации")
        } catch (error: BalanceException) {
            assertTrue(error.message!!.contains("авторизоваться"))
        }
        assertEquals(4, server.requestCount)
    }

    @Test
    fun expiredTokenIsRenewedUsingMilliseconds() = runBlocking {
        var now = 1000L
        val tokens = GigaChatTokenProvider(auth, "test-only", "GIGACHAT_API_PERS") { now }
        server.enqueue(token("old"))
        server.enqueue(token("new"))
        assertEquals("old", tokens.getToken())
        now = 90_000L
        assertEquals("new", tokens.getToken())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun missingKeyMakesNoNetworkRequest() = runBlocking {
        try {
            GigaChatBalanceRepository(api, provider("")).getBalance()
            fail("Нужна ошибка конфигурации")
        } catch (error: BalanceException) {
            assertTrue(error.message!!.contains("не настроен"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun malformedBalanceIsNotShownAsZero() = runBlocking {
        server.enqueue(token("token"))
        server.enqueue(response("""{"balance":[{"usage":"GigaChat"}]}"""))
        try {
            GigaChatBalanceRepository(api, provider()).getBalance()
            fail("Некорректный ответ не должен отображаться как баланс")
        } catch (error: BalanceException) {
            assertTrue(error.message!!.contains("некорректный"))
        }
    }

    @Test
    fun forbiddenResponseExplainsAccountRestriction() = runBlocking {
        server.enqueue(token("token"))
        server.enqueue(MockResponse().setResponseCode(403).setBody("private server details"))
        try {
            GigaChatBalanceRepository(api, provider()).getBalance()
            fail("Ожидалась ошибка доступа")
        } catch (error: BalanceException) {
            assertTrue(error.message!!.contains("пакета токенов"))
            assertFalse(error.message!!.contains("private"))
        }
    }
}
