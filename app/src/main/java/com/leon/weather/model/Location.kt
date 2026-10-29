package com.leon.weather.model

import kotlinx.serialization.Serializable

@Serializable
data class Location(
    val id: Long,
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
) {
    val subtitle: String get() = listOf(region, country).filter(String::isNotBlank).distinct().joinToString(" · ")
}

object PopularLocations {
    val all = listOf(
        Location(1816670, "北京", "北京市", "中国", 39.9075, 116.39723, "Asia/Shanghai"),
        Location(1796236, "上海", "上海市", "中国", 31.22222, 121.45806, "Asia/Shanghai"),
        Location(1809858, "广州", "广东省", "中国", 23.11667, 113.25, "Asia/Shanghai"),
        Location(1795565, "深圳", "广东省", "中国", 22.54554, 114.0683, "Asia/Shanghai"),
    )
}
