package com.example.model

data class VpnServer(
    val id: String,
    val name: String,
    val country: String,
    val countryCode: String, // "th", "sg", "jp", "us", "kr", "de", "gb"
    val host: String, // Real host to ping for socket connection
    val port: Int = 80,
    val fakeIp: String, // UI-only display IP
    val currentPing: Int = -1, // -1 means untested
    val loadPercent: Int = (20..85).random(),
    val speedMbps: Float = (150..950).random().toFloat() / 10f,
    val isPremium: Boolean = false,
    val protocol: String = "SSH SSL/TLS"
) {
    val flagEmoji: String
        get() = when (countryCode.lowercase()) {
            "th" -> "🇹🇭"
            "sg" -> "🇸🇬"
            "jp" -> "🇯🇵"
            "us" -> "🇺🇸"
            "kr" -> "🇰🇷"
            "de" -> "🇩🇪"
            "gb" -> "🇬🇧"
            else -> "🌐"
        }
}
