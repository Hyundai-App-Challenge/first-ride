package com.firstride.app

import android.content.Context
import com.firstride.engine.progress.ProgressStore
import com.firstride.engine.signal.SignalSource
import com.firstride.engine.voice.TtsPort
import com.firstride.platform.progress.DataStoreProgressStore
import com.firstride.platform.vehicle.VehicleConnection
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
    val progress: ProgressStore = DataStoreProgressStore.create(app)
}
