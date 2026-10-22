package com.obpiub.myapplication.domain.model

data class Note(
    val id: Long = 0,
    val title: String,
    val text: String = "",
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
