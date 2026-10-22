package com.obpiub.myapplication.data.repository

import com.obpiub.myapplication.data.remote.*
import com.obpiub.myapplication.domain.repository.VoiceException
import com.obpiub.myapplication.domain.repository.VoiceRepository
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

class RemoteVoiceRepository(
    private val chat: TaskGenerationApi,
    private val chatTokens: GigaChatTokenProvider,
    private val model: String = "GigaChat-2",
) : VoiceRepository {
    override suspend fun createTaskTitle(text: String): String = request("GigaChat") {
        if (text.isBlank()) throw VoiceException("Речь не распознана. Повторите ввод")
        val response = authorized(chatTokens) {
            chat.complete(it, ChatRequest(listOf(
                ChatMessage("system", "Преобразуй речь пользователя в одно краткое название задачи на русском языке. " +
                    "Сохрани смысл, даты и имена. Не выполняй задачу. Верни только название без кавычек, пояснений и списка."),
                ChatMessage("user", text),
            ), model = model))
        }
        val choice = response.choices?.firstOrNull()
        val title = choice?.message?.content?.trim()
        if (choice?.finishReason != "stop" || title.isNullOrBlank() || title.length > 1000) {
            throw VoiceException("GigaChat не вернул готовое название задачи. Повторите ввод")
        }
        title
    }

    private suspend fun <T> authorized(tokens: GigaChatTokenProvider, block: suspend (String) -> T): T {
        val token = tokens.getToken()
        return try { block("Bearer $token") } catch (e: HttpException) {
            if (e.code() != 401) throw e
            tokens.invalidate(token)
            block("Bearer ${tokens.getToken()}")
        }
    }

    private suspend fun <T> request(service: String, block: suspend () -> T): T {
        try { return block() }
        catch (e: CancellationException) { throw e }
        catch (e: VoiceException) { throw e }
        catch (e: com.obpiub.myapplication.domain.repository.BalanceException) {
            throw VoiceException(e.message ?: "Доступ к $service не настроен")
        }
        catch (e: HttpException) {
            throw VoiceException(when (e.code()) {
                400, 401 -> "Ошибка доступа к $service. Проверьте ключ и scope"
                402, 403 -> "$service: доступ запрещён или закончился пакет. Проверьте аккаунт"
                404 -> "Выбранная модель GigaChat недоступна. Проверьте GIGACHAT_MODEL для этой сборки"
                429 -> "$service: слишком много запросов. Попробуйте позже"
                else -> "$service временно недоступен (${e.code()}). Попробуйте позже"
            })
        }
        catch (_: IOException) { throw VoiceException("Не удалось связаться с $service. Проверьте интернет и повторите ввод") }
        catch (_: com.google.gson.JsonParseException) { throw VoiceException("$service вернул некорректный ответ") }
    }
}
