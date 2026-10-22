package com.obpiub.myapplication.domain.repository

import com.obpiub.myapplication.domain.model.TokenBalance

interface BalanceRepository {
    suspend fun getBalance(): List<TokenBalance>
}

class BalanceException(message: String) : Exception(message)