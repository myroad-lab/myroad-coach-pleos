package com.myroad.coach.engine

import com.myroad.coach.data.DemoRoute
import com.myroad.coach.data.Direction
import com.myroad.coach.data.RoadType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 시연용 '출근길' 주행 시나리오 (판교 → 경부고속도로 → 양재IC → 강남, 실제 도로 좌표).
 * 실제 SDK 이벤트와 같은 순서·형식으로 CoachEngine에 신호를 넣어, PT에서 매번 같은 장면을 재현합니다.
 *  1) 출발(우천) · 위치를 경로를 따라 입력
 *  2) 경부고속도로 합류 접근 → 500m 전 음성 코칭 → 40m 전 첫 제동(구간 직전 제동 기록)
 *  3) 양재IC 우측 진출 → 우측 방향지시등 없이 조향(방향지시등 미사용 기록)
 *  4) 어린이보호구역 단속 카메라 과속 안내 (카메라 1대당 1회)
 *  5) 도착 → 정차 후 하이라이트로 전환 (실제 지도에 경로와 위험 지점 표시)
 */
class ScenarioPlayer(private val engine: CoachEngine, private val scope: CoroutineScope) {

    private var job: Job? = null
    val isPlaying get() = job?.isActive == true

    private var routeIndex = 0

    /** 경로 좌표를 [toIndex]까지 차례로 엔진에 입력 */
    private fun advanceTo(toIndex: Int) {
        val pts = DemoRoute.points
        val target = toIndex.coerceAtMost(pts.lastIndex)
        while (routeIndex <= target) {
            val p = pts[routeIndex]
            engine.onLocation(p.lat, p.lng)
            routeIndex++
        }
    }

    /** [from]~[to] 구간에서 진행률(0~1)에 맞는 위치까지 입력 */
    private fun advanceFraction(from: Int, to: Int, fraction: Float) =
        advanceTo(from + ((to - from) * fraction.coerceIn(0f, 1f)).toInt())

    fun play(speed: Float = 1f) {
        stop()
        routeIndex = 0
        job = scope.launch {
            val tick = (450 / speed).toLong()
            var clock = "08:31"
            engine.clockOverride = { clock }
            engine.setSource("시나리오 재생")
            engine.onDriveState(true)
            engine.onConditions(rainy = true, night = false)
            // 실차처럼 신호 구독 직후 첫 값이 들어온 상태로 시작 (판정 가능 상태)
            engine.onTurnSignal(SignalSide.OFF)
            engine.onSteering(0f)
            engine.onBrake(false)

            // ── 출발 → 합류 접근 전 ──
            val beforeMerge = DemoRoute.MERGE_INDEX - 12
            repeat(4) { i ->
                advanceFraction(0, beforeMerge, (i + 1) / 4f)
                delay(tick)
            }

            // ── 경부고속도로 합류 (900m → 0m) ──
            clock = "08:34"
            var d = 900
            while (isActive && d >= 0) {
                advanceFraction(beforeMerge, DemoRoute.MERGE_INDEX, 1f - d / 900f)
                engine.onTbt(RoadType.MERGE, d, "경부고속도로 본선 합류", Direction.LEFT)
                if (d == 40) engine.onBrake(true)
                if (d == 0) engine.onBrake(false)
                d -= if (d > 100) 50 else 20
                delay(tick)
            }
            engine.onTbt(RoadType.MERGE, 0, "경부고속도로 본선 합류", Direction.LEFT)
            delay(tick * 3)

            // ── 양재IC 진출 (방향지시등 없이 진출) ──
            clock = "08:43"
            d = 1200
            while (isActive && d >= 0) {
                advanceFraction(DemoRoute.MERGE_INDEX, DemoRoute.IC_EXIT_INDEX, 1f - d / 1200f)
                engine.onTbt(RoadType.IC_EXIT, d, "양재IC 진출", Direction.RIGHT)
                if (d == 60) engine.onSteering(38f)
                d -= if (d > 100) 100 else 20
                delay(tick)
            }
            engine.onTbt(RoadType.IC_EXIT, 0, "양재IC 진출", Direction.RIGHT)
            engine.onSteering(0f)
            delay(tick * 3)

            // ── 어린이보호구역 과속 안내 ──
            clock = "08:49"
            repeat(3) { i ->
                advanceFraction(DemoRoute.IC_EXIT_INDEX, DemoRoute.SCHOOL_INDEX, (i + 1) / 3f)
                delay(tick)
            }
            engine.onCautionPoint(RoadType.SCHOOL_ZONE, 150)
            delay(tick)
            engine.onCameraAlert("school-cam-1", overSpeed = false, speedLimit = 30, inSchoolZone = true, remainMeters = 150)
            engine.onCautionPoint(RoadType.SCHOOL_ZONE, 20)
            engine.onCameraAlert("school-cam-1", overSpeed = true, speedLimit = 30, inSchoolZone = true, remainMeters = 20)
            // 같은 카메라의 안내가 반복돼도 한 번만 셉니다
            engine.onCameraAlert("school-cam-1", overSpeed = true, speedLimit = 30, inSchoolZone = true, remainMeters = 5)
            delay(tick * 3)

            // ── 도착 ──
            clock = "08:52"
            advanceTo(DemoRoute.points.lastIndex)
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
