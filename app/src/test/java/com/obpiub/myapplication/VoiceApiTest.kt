package com.obpiub.myapplication

import com.google.gson.JsonParser
import com.obpiub.myapplication.data.remote.*
import com.obpiub.myapplication.data.repository.RemoteVoiceRepository
import com.obpiub.myapplication.domain.repository.VoiceException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class VoiceApiTest {
    private val server = MockWebServer()
    private lateinit var retrofit: Retrofit
    @Before fun setUp() {
        server.start()
        retrofit = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build()
    }
    @After fun tearDown() { server.shutdown() }
    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun token(value: String) = response("""{"access_token":"$value","expires_at":100000}""")
    private fun repository(key: String = "chat-only") = RemoteVoiceRepository(
        retrofit.create(TaskGenerationApi::class.java),
        GigaChatTokenProvider(retrofit.create(GigaChatAuthApi::class.java), key, "GIGACHAT_API_PERS") { 1000L },
    )

    @Test fun recognizedTextIsSentToGigaChatWithCorrectRequestBody() = runBlocking {
        server.enqueue(token("chat-token"))
        server.enqueue(response("""{"choices":[{"message":{"content":"Купить хлеб завтра"},"finish_reason":"stop"}]}"""))
        val repository = repository()
        val text = "Купить хлеб завтра"
        assertEquals("Купить хлеб завтра", repository.createTaskTitle(text))
        val chatAuth = server.takeRequest()
        assertEquals("Basic chat-only", chatAuth.getHeader("Authorization"))
        val chat = server.takeRequest()
        assertEquals("/v1/chat/completions", chat.path)
        assertEquals("Bearer chat-token", chat.getHeader("Authorization"))
        val body = JsonParser.parseString(chat.body.readUtf8()).asJsonObject
        assertFalse(body["stream"].asBoolean)
        assertEquals("GigaChat-2", body["model"].asString)
        assertEquals(text, body["messages"].asJsonArray[1].asJsonObject["content"].asString)
    }

    @Test fun missingKeyDoesNotSendText() = runBlocking {
        val error = runCatching { repository("").createTaskTitle("Задача") }.exceptionOrNull()
        assertTrue(error is VoiceException)
        assertTrue(error!!.message!!.contains("GigaChat"))
        assertEquals(0, server.requestCount)
    }

    @Test fun unauthorizedGenerationRefreshesOnlyOnce() = runBlocking {
        server.enqueue(token("old"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(token("new"))
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(runCatching { repository().createTaskTitle("Задача") }.exceptionOrNull() is VoiceException)
        assertEquals(4, server.requestCount)
    }

    @Test fun emptyRecognitionAndTruncatedGenerationAreErrors() = runBlocking {
        server.enqueue(token("chat"))
        server.enqueue(response("""{"choices":[{"message":{"content":"Обрезанный ответ"},"finish_reason":"length"}]}"""))
        val repository = repository()
        assertTrue(runCatching { repository.createTaskTitle(" ") }.exceptionOrNull() is VoiceException)
        assertTrue(runCatching { repository.createTaskTitle("Задача") }.exceptionOrNull() is VoiceException)
    }

    @Test fun networkErrorDoesNotExposeResponseBody() = runBlocking {
        server.enqueue(token("speech"))
        server.enqueue(MockResponse().setResponseCode(403).setBody("private account details"))
        val error = runCatching { repository().createTaskTitle("Задача") }.exceptionOrNull()
        assertTrue(error is VoiceException)
        assertFalse(error!!.message!!.contains("private"))
    }
}
