package com.firstride.app

import android.content.Context
import com.firstride.engine.drive.DrivingGate
import com.firstride.engine.progress.ProgressStore
import com.firstride.engine.settings.SettingsStore
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.voice.SttPort
import com.firstride.engine.voice.TtsPort
import com.firstride.platform.drive.CarDrivingGate
import com.firstride.platform.progress.DataStoreProgressStore
import com.firstride.platform.settings.DataStoreSettingsStore
import com.firstride.platform.vehicle.VehicleConnection
import com.firstride.platform.voice.PleosStt
import com.firstride.platform.voice.PleosTts

/**
 * 앱 시작 때 한 번 만드는 객체들(기술 설계 2절, 직접 조립). 앱 수명 동안 하나씩 둔다.
 * 신호원은 빌드 종류마다 다르다: release는 늘 차량 SDK, debug는 [Variant]가 고른다.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    /**
     * 차량 SDK 연결. 가짜 신호원을 쓰는 debug 빌드에서는 만들지 않는다.
     * 앱 수명 동안 하나이고 닫지 않는다(프로세스가 끝날 때 함께 끝난다). 화면에서 close하지 않는다.
     */
    private val vehicle by lazy { VehicleConnection(app) }

    val signals: SignalSource = Variant.signalSource(app) { vehicle.signals }
    val tts: TtsPort by lazy { PleosTts(app) }
    /** 음성 진행(FR-06)의 듣기. 기기 안의 엔진만 쓰고, 한 번 듣기마다 SDK 인스턴스를 새로 만들어 정리한다. */
    val stt: SttPort by lazy { PleosStt(app) }
    val progress: ProgressStore = DataStoreProgressStore.create(app)
    /** 설정값(음성 켜기/끄기). 기록과 다른 파일이라 기록 지우기로 지워지지 않는다(POL-S-5). */
    val settings: SettingsStore = DataStoreSettingsStore.create(app)
    /** 주행 제한. 가짜 신호원을 쓰는 debug 빌드에서도 에뮬레이터의 실제 값(VC 기어)을 쓴다. */
    val drivingGate: DrivingGate = CarDrivingGate(app)
    /** 실습이 끊긴 시각을 재는 시계. 기기 시간 변경에 흔들리지 않는 부팅 후 경과 시간. */
    val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }
}
