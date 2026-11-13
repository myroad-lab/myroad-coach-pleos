package com.myroad.coach.engine

import com.myroad.coach.data.RoadType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 시연용 '출근길' 주행 시나리오.
 * 실제 SDK 이벤트와 같은 순서·형식으로 CoachEngine에 신호를 넣어, PT에서 매번 같은 장면을 재현합니다.
 *  1) 출발(기어 D) · 우천
 *  2) 고속도로 합류 구간 접근 → 500m 전 음성 코칭 → 40m 전 제동(늦은 감속 기록)
 *  3) IC 진출 → 방향지시등 없이 조향(방향지시등 미사용 기록)
 *  4) 어린이보호구역 단속 카메라 과속 안내
 *  5) 도착(기어 P) → 정차 후 하이라이트로 전환
 */
class ScenarioPlayer(private val engine: CoachEngine, private val scope: CoroutineScope) {

    private var job: Job? = null
    val isPlaying get() = job?.isActive == true

    fun play(speed: Float = 1f) {
        stop()
        job = scope.launch {
            val tick = (450 / speed).toLong()
            var minute = 31
            engine.clockOverride = { "08:%02d".format(minute.coerceAtMost(59)) }
            engine.setSource("시나리오 재생")
            engine.onDriveState(true)
            engine.onConditions(rainy = true, night = false)
            engine.onTripDistance(24.6f)
            delay(tick * 4)

            // ── 합류 구간 ──
            minute = 41
            var d = 900
            while (isActive && d >= 0) {
                engine.onTbt(RoadType.MERGE, d, "본선 합류")
                if (d == 40) { minute = 42; engine.onBrake(true) }
                if (d == 0) engine.onBrake(false)
                d -= if (d > 100) 50 else 20
                delay(tick)
            }
            engine.onTbt(RoadType.MERGE, 0, "본선 합류")
            delay(tick * 4)

            // ── IC 진출 (방향지시등 없이 진출) ──
            minute = 54
            d = 1200
            while (isActive && d >= 0) {
                engine.onTbt(RoadType.IC_EXIT, d, "우측 IC 진출")
                if (d == 60) { minute = 55; engine.onSteering(38f) }
                d -= if (d > 100) 100 else 20
                delay(tick)
            }
            engine.onTbt(RoadType.IC_EXIT, 0, "우측 IC 진출")
            engine.onSteering(0f)
            delay(tick * 4)

            // ── 어린이보호구역 과속 안내 ──
            minute = 2 + 60 // 09:02 표시용
            engine.clockOverride = { "09:%02d".format(minute - 60) }
            engine.onCautionPoint(RoadType.SCHOOL_ZONE, 150)
            delay(tick)
            engine.onCautionPoint(RoadType.SCHOOL_ZONE, 20)
            delay(tick)
            engine.onCameraAlert(overSpeed = true, speedLimit = 30, inSchoolZone = true)
            delay(tick * 4)

            // ── 도착 ──
            minute = 7 + 60
            engine.onDestinationArrived()
            engine.setSource("시나리오 재생 완료")
            engine.clockOverride = null
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
