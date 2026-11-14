package com.firstride.platform.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.firstride.engine.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 설정값을 앱 전용 저장소에 둔다(POL-S-1, POL-S-3). 기록(`progress` 파일)과 다른 파일(`settings`)이라서
 * "기록 지우기"가 파일을 비워도 남는다(POL-S-5). 앱에서 하나만 만든다.
 */
class DataStoreSettingsStore private constructor(context: Context) : SettingsStore {
    private val store = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("settings") },
    )

    override val voiceEnabled: Flow<Boolean> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[VOICE] ?: true }

    override suspend fun setVoiceEnabled(enabled: Boolean) {
        store.edit { it[VOICE] = enabled }
    }

    companion object {
        private val VOICE = booleanPreferencesKey("voice.enabled")
        fun create(context: Context) = DataStoreSettingsStore(context.applicationContext)
    }
}
