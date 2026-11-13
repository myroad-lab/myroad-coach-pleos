package com.myroad.coach.sdk

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale
import android.speech.tts.TextToSpeech as AndroidTts

/**
 * 음성 안내.
 * 1순위: Pleos TextToSpeech SDK (ON_DEVICE 모드 → 앱 등록 불필요)
 * 2순위: Pleos TTS 초기화에 실패하면 안드로이드 기본 TTS로 대체 (에뮬레이터 환경 차이 대비)
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private var pleosTts: TextToSpeech? = null
    private var pleosReady = false
    private var fallback: AndroidTts? = null

    var engineName: String = "준비 중"
        private set

    fun initialize() {
        try {
            val tts = TextToSpeech(appContext, Mode.ON_DEVICE)
            pleosTts = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    pleosReady = true
                    engineName = "Pleos TTS"
                    runCatching { tts.setLocale(Locale.KOREAN) }
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "Pleos TTS 초기화 실패: $errorType → 기본 TTS로 대체")
                    initFallback()
                }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos TTS 사용 불가 → 기본 TTS로 대체", e)
            initFallback()
        }
    }

    private fun initFallback() {
        if (fallback != null) return
        fallback = AndroidTts(appContext) { status ->
            if (status == AndroidTts.SUCCESS) {
                fallback?.setLanguage(Locale.KOREAN)
                engineName = "Android TTS"
            }
        }
    }

    fun speak(text: String) {
        try {
            if (pleosReady) {
                pleosTts?.speak(text, true)
                return
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos TTS speak 실패", e)
        }
        fallback?.speak(text, AndroidTts.QUEUE_FLUSH, null, "myroad")
    }

    fun release() {
        runCatching { pleosTts?.stop() }
        runCatching { pleosTts?.release() }
        runCatching { fallback?.shutdown() }
    }

    companion object {
        private const val TAG = "MyRoadSpeaker"
    }
}
