package com.firstride.platform.progress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.firstride.engine.progress.CourseResult
import com.firstride.engine.progress.ProgressStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 진행 기록을 앱 전용 저장소(DataStore Preferences)에 둔다(POL-S-3). 저장하는 것은 POL-S-1의 것뿐이다.
 *
 * 이 파일(`progress`)에는 지울 수 있는 기록만 둔다. 설정값은 다른 파일에 두어 [clearAll]이 파일을 통째로 비워도 남는다(POL-S-5).
 * 키는 `course.<코스 ID>.result` 하나뿐이고 값은 [CourseResult]의 이름이다(순서 번호가 아니라 이름).
 *
 * 파일이 깨졌으면 빈 기록으로 바꾼다. 완료를 시작 전으로 보일 수는 있어도 그 반대는 없다(POL-P-2).
 */
class DataStoreProgressStore internal constructor(private val store: DataStore<Preferences>) : ProgressStore {

    private suspend fun prefs(): Preferences =
        store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()

    override suspend fun result(courseId: String): CourseResult = prefs()[resultKey(courseId)].toResult()

    /** 저장값과 이번 시도 중 나은 것을 남긴다(POL-R-7). 읽고 쓰는 것을 한 번의 편집 안에서 한다. */
    override suspend fun record(courseId: String, attempt: CourseResult) {
        store.edit { p ->
            val key = resultKey(courseId)
            val best = maxOf(p[key].toResult(), attempt)
            if (best != CourseResult.NOT_STARTED) p[key] = best.name
        }
    }

    /**
     * 배지는 저장하지 않는다. 코스 결과에서 BadgeRule로 계산하는 쪽이 기록 지우기와 맞는다(기술 설계 5-7절).
     * 11-15 제출본에는 배지가 없어 빈 집합이다. 배지가 들어올 때(W-37) A와 이 메서드의 자리를 정한다.
     */
    override suspend fun badges(): Set<String> = emptySet()

    override suspend fun clearAll() {
        store.edit { it.clear() }
    }

    companion object {
        private fun resultKey(courseId: String) = stringPreferencesKey("course.$courseId.result")
        private fun String?.toResult(): CourseResult =
            CourseResult.entries.firstOrNull { it.name == this } ?: CourseResult.NOT_STARTED

        /** 앱에서 하나만 만든다. 같은 파일에 DataStore를 둘 만들면 안 된다. */
        fun create(context: Context): DataStoreProgressStore =
            DataStoreProgressStore(dataStore { context.applicationContext.preferencesDataStoreFile("progress") })

        internal fun dataStore(
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            file: () -> File,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = file,
        )
    }
}
