package com.obpiub.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import com.obpiub.myapplication.data.remote.GigaChatClient
import com.obpiub.myapplication.data.remote.VoiceClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Before

class LiveGigaChatTest {
    @Before fun requireLiveMode() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveGigaChat") == "true")
    }
    @Test fun realBalanceAndTaskGeneration() = runBlocking {
        assertTrue("Локальный ключ не настроен", BuildConfig.GIGACHAT_AUTH_KEY.isNotBlank())
        val balances = GigaChatClient.createBalanceRepository().getBalance()
        assertTrue("API вернул пустой список баланса", balances.isNotEmpty())
        val title = VoiceClient.create().createTaskTitle("Завтра нужно купить хлеб")
        assertTrue("API вернул пустое название", title.isNotBlank())
        assertTrue("Название не сохранило смысл", title.contains("хлеб", ignoreCase = true))
    }
}
