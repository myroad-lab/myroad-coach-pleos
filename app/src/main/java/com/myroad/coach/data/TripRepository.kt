package com.myroad.coach.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 주행 기록을 앱 내부 저장소(files/trips.json)에 저장합니다.
 * 운전 데이터는 기기 밖으로 보내지 않습니다.
 * 저장된 기록이 없으면 시연용 4주치 샘플 기록으로 시작합니다.
 */
class TripRepository(context: Context) {

    private val file = File(context.filesDir, "trips.json")

    fun load(): List<TripRecord> {
        if (!file.exists()) {
            val seed = SeedTrips.generate(System.currentTimeMillis())
            save(seed)
            return seed
        }
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "주행 기록을 읽지 못해 샘플로 다시 시작합니다", e)
            SeedTrips.generate(System.currentTimeMillis()).also { save(it) }
        }
    }

    fun save(trips: List<TripRecord>) {
        try {
            val arr = JSONArray()
            trips.forEach { arr.put(toJson(it)) }
            val tmp = File(file.parentFile, "trips.json.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(arr.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "주행 기록 저장 실패", e)
        }
    }

    /** 시연을 처음 상태로 되돌릴 때 사용합니다. */
    fun resetToSample(): List<TripRecord> {
        val seed = SeedTrips.generate(System.currentTimeMillis())
        save(seed)
        return seed
    }

    private fun toJson(t: TripRecord) = JSONObject().apply {
        put("id", t.id)
        put("startedAt", t.startedAt)
        put("startLabel", t.startLabel)
        put("endLabel", t.endLabel)
        put("distanceKm", t.distanceKm.toDouble())
        put("rainy", t.rainy)
        put("night", t.night)
        put("isSample", t.isSample)
        put("passes", JSONObject().apply { t.passes.forEach { (k, v) -> put(k.name, v) } })
        put("moments", JSONArray().apply {
            t.moments.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("time", m.time)
                    put("kind", m.kind.name)
                    put("roadType", m.roadType.name)
                    put("rainy", m.rainy)
                    put("night", m.night)
                    put("cause", m.cause)
                    put("mapX", m.mapX.toDouble())
                    put("mapY", m.mapY.toDouble())
                    put("judgement", m.judgement.name)
                })
            }
        })
    }

    private fun fromJson(o: JSONObject): TripRecord {
        val passesJson = o.getJSONObject("passes")
        val passes = mutableMapOf<RoadType, Int>()
        passesJson.keys().forEach { k ->
            runCatching { RoadType.valueOf(k) }.getOrNull()?.let { passes[it] = passesJson.getInt(k) }
        }
        val ms = o.getJSONArray("moments")
        val moments = (0 until ms.length()).mapNotNull { i ->
            val m = ms.getJSONObject(i)
            runCatching {
                RiskMoment(
                    id = m.getInt("id"),
                    time = m.getString("time"),
                    kind = MomentKind.valueOf(m.getString("kind")),
                    roadType = RoadType.valueOf(m.getString("roadType")),
                    rainy = m.getBoolean("rainy"),
                    night = m.getBoolean("night"),
                    cause = m.getString("cause"),
                    mapX = m.getDouble("mapX").toFloat(),
                    mapY = m.getDouble("mapY").toFloat(),
                    judgement = Judgement.valueOf(m.optString("judgement", Judgement.HABIT_SUSPECTED.name)),
                )
            }.getOrNull()
        }
        return TripRecord(
            id = o.getLong("id"),
            startedAt = o.getLong("startedAt"),
            startLabel = o.getString("startLabel"),
            endLabel = o.getString("endLabel"),
            distanceKm = o.getDouble("distanceKm").toFloat(),
            rainy = o.getBoolean("rainy"),
            night = o.getBoolean("night"),
            passes = passes,
            moments = moments,
            // 이전 버전에 저장된 샘플 기록은 isSample 값이 없으므로 샘플 고유 번호(1,000,000 미만)로 구분
            isSample = o.optBoolean("isSample", o.getLong("id") < 1_000_000L),
        )
    }

    companion object {
        private const val TAG = "MyRoadTrips"
    }
}
