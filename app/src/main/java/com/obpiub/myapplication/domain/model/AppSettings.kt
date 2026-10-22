package com.obpiub.myapplication.domain.model

enum class ThemeMode { System, Light, Dark }
enum class AccentColor { Purple, Blue, Green }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val accent: AccentColor = AccentColor.Purple,
)