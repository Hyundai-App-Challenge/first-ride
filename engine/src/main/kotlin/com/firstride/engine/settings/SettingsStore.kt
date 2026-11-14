package com.firstride.engine.settings

import kotlinx.coroutines.flow.Flow

/** 설정값(POL-S-1). 기록 지우기로 지워지지 않는다(POL-S-5). */
interface SettingsStore {
    /** 음성 안내·음성 진행 켜짐. 저장된 값이 없으면 켜짐. */
    val voiceEnabled: Flow<Boolean>
    suspend fun setVoiceEnabled(enabled: Boolean)
}
