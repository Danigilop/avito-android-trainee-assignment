package com.obpiub.myapplication.domain.model

data class Task(
    val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
