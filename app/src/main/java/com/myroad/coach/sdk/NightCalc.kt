package com.myroad.coach.sdk

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 위치와 시각으로 해가 졌는지 계산합니다 (NOAA 간이식, 오차 수 분).
 * 전조등 상태는 운전자 설정에 따라 낮에도 켜지므로 야간 판정에는 쓰지 않습니다.
 */
object NightCalc {

    fun isNight(lat: Double, lng: Double, epochMillis: Long): Boolean {
        val days = epochMillis / 86_400_000.0
        val dayOfYear = ((days % 365.2422) + 365.2422) % 365.2422
        val gamma = 2 * Math.PI / 365 * dayOfYear
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val latR = Math.toRadians(lat)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
        if (cosHa >= 1) return true       // 극야
        if (cosHa <= -1) return false     // 백야
        val ha = Math.toDegrees(acos(cosHa))
        val noonUtcMin = 720 - 4 * lng - eqTime
        val sunriseUtc = noonUtcMin - 4 * ha
        val sunsetUtc = noonUtcMin + 4 * ha
        val minuteOfDayUtc = ((epochMillis % 86_400_000L + 86_400_000L) % 86_400_000L) / 60_000.0
        fun norm(m: Double) = ((m % 1440) + 1440) % 1440
        val rise = norm(sunriseUtc)
        val set = norm(sunsetUtc)
        val t = minuteOfDayUtc
        val isDay = if (rise < set) t in rise..set else t >= rise || t <= set
        return !isDay
    }
}
