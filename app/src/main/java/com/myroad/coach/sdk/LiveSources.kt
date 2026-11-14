package com.myroad.coach.sdk

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.CameraType
import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.WiperStateListener
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.myroad.coach.engine.CoachEngine
import com.myroad.coach.engine.SignalSide

/**
 * 실차(또는 에뮬레이터 버추얼 콘솔) 신호를 CoachEngine으로 전달합니다.
 * - Vehicle SDK: 브레이크 페달, 기어, 조향각, 방향지시등, 앞유리 와이퍼(우천 추정)
 * - NaviHelper SDK: 회전 안내(TBT, 방향 포함), 주의 구간, 단속 카메라(id), 재탐색·경로 취소, 목적지 도착
 * - FusedLocation SDK: 위치·시각·정확도 (주행 경로, 위험 순간 위치, 주행 거리, 일몰 여부)
 * SDK 콜백은 IPC 스레드에서 올 수 있으므로 메인 스레드로 옮겨서 처리합니다.
 * 지원되지 않는 항목은 onFailed로 알려 오며, 해당 기능만 꺼진 채로 앱은 계속 동작합니다.
 */
class LiveSources(context: Context, private val engine: CoachEngine) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null
    private var fused: FusedLocation? = null

    /** 연결 결과를 사람이 읽을 수 있게 모아 둡니다 (설정 화면/로그용). */
    val status = linkedMapOf<String, String>()

    private fun ui(block: () -> Unit) {
        main.post(block)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(p0: Boolean?) = ui { engine.onBrake(p0 == true) }
        override fun onFailed(p0: Exception) = fail("브레이크", p0)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(p0: VehicleGear?) = ui {
            when (p0) {
                VehicleGear.GEAR_PARK -> engine.onDriveState(false)
                null, VehicleGear.GEAR_UNKNOWN -> Unit
                else -> engine.onDriveState(true)
            }
        }
        override fun onFailed(p0: Exception) = fail("기어", p0)
    }

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(p0: Float?) = ui { engine.onSteering(p0 ?: 0f) }
        override fun onFailed(p0: Exception) = fail("조향각", p0)
    }

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(p0: TurnSignalValue?) = ui {
            engine.onTurnSignal(
                when (p0) {
                    TurnSignalValue.LEFT -> SignalSide.LEFT
                    TurnSignalValue.RIGHT -> SignalSide.RIGHT
                    else -> SignalSide.OFF
                },
            )
        }
        override fun onFailed(p0: Exception) = fail("방향지시등", p0)
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(p0: WiperState?) = ui {
            if (p0 != null) engine.onWiper(p0 == WiperState.ON)
        }
        override fun onFailed(p0: Exception) = fail("와이퍼", p0)
    }

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(p0: LocationData?) {
            val loc = p0 ?: return
            val t = if (loc.timestampMillis > 0L) loc.timestampMillis else System.currentTimeMillis()
            ui {
                engine.onLocation(loc.latitude, loc.longitude, t, loc.confidence)
                engine.onNight(NightCalc.isNight(loc.latitude, loc.longitude, t))
            }
        }
        override fun onError(p0: Exception) = fail("위치", p0)
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onTBTInfo(p0: List<TBTInfo>) {
            val first = p0.firstOrNull() ?: return
            ui {
                engine.onTbt(
                    RoadTypeMapper.fromTurn(first.type), first.distance.toInt(), first.description,
                    RoadTypeMapper.directionOf(first.type),
                )
            }
        }

        override fun onCautionPointInfo(p0: List<CautionPoint>) {
            val nearest = p0.minByOrNull { it.remainDistance } ?: return
            ui { engine.onCautionPoint(RoadTypeMapper.fromCaution(nearest.type), nearest.remainDistance) }
        }

        override fun onCameraAlert(p0: List<CameraAlert>) {
            val alerts = p0.toList()
            ui {
                alerts.forEach { a ->
                    engine.onCameraAlert(
                        id = a.id, overSpeed = a.isOverSpeed || a.isOverSectionAvrSpeed, speedLimit = a.speedLimit,
                        inSchoolZone = a.type.name.contains("SCHOOL"), remainMeters = a.remainDistance,
                    )
                }
            }
        }

        override fun onRouteStarted(p0: RouteStartInfo) = ui { engine.onRouteChanged() }
        override fun onReRouteEnded() = ui { engine.onRouteChanged() }
        override fun onRouteCancelled() = ui { engine.onRouteChanged() }

        override fun onDestinationArrived(p0: DestinationArrivedInfo) = ui { engine.onDestinationArrived() }
    }

    fun start() {
        engine.setSource("실차 신호")
        try {
            val v = Vehicle(appContext)
            v.initialize()
            vehicle = v
            v.getBrake().registerBrakePedalStatus(brakeListener)
            v.getGear().registerGearSelection(gearListener)
            v.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
            v.getTurnSignal().registerTurnSignalState(turnSignalListener)
            runCatching { v.getWiper().registerWiperState(FRONT, wiperListener) }.onFailure { fail("와이퍼", it) }
            status["Vehicle SDK"] = "연결"
        } catch (e: Throwable) {
            fail("Vehicle SDK", e)
        }
        try {
            val n = NaviHelper(appContext)
            n.initialize(
                { status["NaviHelper SDK"] = "연결" },
                { t -> fail("NaviHelper SDK", t) },
            )
            n.addListener(naviListener)
            navi = n
        } catch (e: Throwable) {
            fail("NaviHelper SDK", e)
        }
        try {
            val f = FusedLocation(appContext)
            f.initialize()
            // 초기화 직후에는 지원 여부가 아직 확정되지 않을 수 있어 항상 등록하고, 상태만 표시합니다.
            f.registerFusedLocationCallback(locationListener)
            status["FusedLocation SDK"] = if (runCatching { f.isSupported() }.getOrDefault(true)) "연결" else "미지원"
            fused = f
        } catch (e: Throwable) {
            fail("FusedLocation SDK", e)
        }
    }

    fun stop() {
        vehicle?.let { v ->
            runCatching { v.getBrake().unregisterBrakePedalStatus(brakeListener) }
            runCatching { v.getGear().unregisterGearSelection(gearListener) }
            runCatching { v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
            runCatching { v.getTurnSignal().unregisterTurnSignalState(turnSignalListener) }
            runCatching { v.getWiper().unregisterWiperState(FRONT, wiperListener) }
            runCatching { v.release() }
        }
        navi?.let { n ->
            runCatching { n.removeListener(naviListener) }
            runCatching { n.release() }
        }
        fused?.let { f ->
            runCatching { f.unregisterFusedLocationCallback(locationListener) }
            runCatching { f.release() }
        }
        vehicle = null
        navi = null
        fused = null
    }

    private fun fail(what: String, e: Throwable?) {
        status[what] = "사용 불가"
        Log.w(TAG, "$what 연결 실패: ${e?.message}")
    }

    companion object {
        private const val TAG = "MyRoadLive"
        private val FRONT = WindowArea(WindowValue.FRONT_WINDSHIELD)

        // 아래 import가 실제로 쓰이도록 남겨 둔 참조 (CameraType/CautionPointType 매핑 확장용)
        @Suppress("unused") private val keepTypes = listOf(CameraType::class, CautionPointType::class)
    }
}
