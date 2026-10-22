package com.obpiub.myapplication.data.repository

import com.google.gson.JsonParseException
import com.obpiub.myapplication.data.remote.GigaChatApi
import com.obpiub.myapplication.data.remote.GigaChatTokenProvider
import com.obpiub.myapplication.domain.model.TokenBalance
import com.obpiub.myapplication.domain.repository.BalanceException
import com.obpiub.myapplication.domain.repository.BalanceRepository
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

class GigaChatBalanceRepository(
    private val api: GigaChatApi,
    private val tokens: GigaChatTokenProvider,
) : BalanceRepository {
    override suspend fun getBalance(): List<TokenBalance> {
        try {
            val token = tokens.getToken()
            val response = try {
                api.balance("Bearer $token")
            } catch (error: HttpException) {
                if (error.code() != 401) throw error
                tokens.invalidate(token)
                api.balance("Bearer ${tokens.getToken()}")
            }
            val items = response.balance ?: throw BalanceException("GigaChat вернул некорректный баланс.")
            return items.map {
                val name = it.usage
                val count = it.value
                if (name.isNullOrBlank() || count == null) {
                    throw BalanceException("GigaChat вернул некорректный баланс.")
                }
                TokenBalance(name, count)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: BalanceException) {
            throw error
        } catch (error: HttpException) {
            throw BalanceException(when (error.code()) {
                400, 401 -> "Не удалось авторизоваться в GigaChat. Проверьте параметры доступа."
                403 -> "Баланс недоступен для этого аккаунта. Метод требует пакета токенов и не поддерживает оплату по использованию."
                429 -> "Слишком много запросов. Попробуйте позже."
                else -> "Ошибка GigaChat (${error.code()}). Попробуйте позже."
            })
        } catch (_: SSLException) {
            throw BalanceException("Не удалось установить защищённое соединение с GigaChat.")
        } catch (_: SocketTimeoutException) {
            throw BalanceException("GigaChat не ответил вовремя. Попробуйте ещё раз.")
        } catch (_: IOException) {
            throw BalanceException("Не удалось связаться с GigaChat. Проверьте подключение к интернету.")
        } catch (_: JsonParseException) {
            throw BalanceException("GigaChat вернул некорректный ответ.")
        }
    }
}