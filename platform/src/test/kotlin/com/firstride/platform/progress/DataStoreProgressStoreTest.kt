package com.firstride.platform.progress

import com.firstride.engine.progress.CourseResult.COMPLETED
import com.firstride.engine.progress.CourseResult.HAS_SKIPPED
import com.firstride.engine.progress.CourseResult.NOT_STARTED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataStoreProgressStoreTest {
    private val dir: File = Files.createTempDirectory("progress").toFile()
    private val scopes = mutableListOf<CoroutineScope>()

    /** 같은 파일을 여는 새 저장소. 앱을 껐다 켠 것과 같다. */
    private suspend fun open(): Pair<DataStoreProgressStore, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>> {
        scopes.forEach { it.coroutineContext[Job]!!.cancelAndJoin() }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val ds = DataStoreProgressStore.dataStore(scope) { File(dir, "progress.preferences_pb") }
        return DataStoreProgressStore(ds) to ds
    }

    @AfterTest fun cleanUp() { scopes.forEach { it.cancel() }; dir.deleteRecursively() }

    @Test
    fun `AC-11-1 앱을 껐다 켜도 코스 상태가 남는다`() = runBlocking {
        open().first.record("CRS-01", COMPLETED)
        assertEquals(COMPLETED, open().first.result("CRS-01"))
    }

    @Test
    fun `파일이 깨졌으면 빈 기록으로 보고 새로 쓴다`() = runBlocking {
        File(dir, "progress.preferences_pb").writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        val store = open().first
        assertEquals(NOT_STARTED, store.result("CRS-01"))
        store.record("CRS-01", COMPLETED)
        assertEquals(COMPLETED, store.result("CRS-01"))
    }

    @Test
    fun `기록이 없는 코스는 시작 전이다`() = runBlocking {
        assertEquals(NOT_STARTED, open().first.result("CRS-02"))
    }

    @Test
    fun `AC-11-6 POL-R-7 완료한 코스를 건너뛰며 다시 해도 완료로 남는다`() = runBlocking {
        val store = open().first
        store.record("CRS-01", COMPLETED)
        store.record("CRS-01", HAS_SKIPPED)
        assertEquals(COMPLETED, store.result("CRS-01"))
    }

    @Test
    fun `POL-R-7 건너뜀 있음 뒤에 완료하면 완료로 오른다`() = runBlocking {
        val store = open().first
        store.record("CRS-01", HAS_SKIPPED)
        assertEquals(HAS_SKIPPED, store.result("CRS-01"))
        store.record("CRS-01", COMPLETED)
        assertEquals(COMPLETED, store.result("CRS-01"))
    }

    @Test
    fun `AC-11-5 POL-R-6 기록하지 않은 시도는 저장 상태를 바꾸지 않는다`() = runBlocking {
        // 그만하기는 record를 부르지 않는다(5-7절). 저장소 쪽에서는 시작 전을 기록해도 아무 키가 생기지 않는다.
        val (store, ds) = open()
        store.record("CRS-01", NOT_STARTED)
        assertTrue(ds.data.first().asMap().isEmpty())
    }

    @Test
    fun `AC-11-7 POL-S-2 코스 결과 말고는 아무것도 저장하지 않는다`() = runBlocking {
        val (store, ds) = open()
        store.record("CRS-01", HAS_SKIPPED)
        store.record("CRS-03", COMPLETED)
        assertEquals(setOf("course.CRS-01.result", "course.CRS-03.result"), ds.data.first().asMap().keys.map { it.name }.toSet())
    }

    @Test
    fun `POL-S-5 기록 지우기는 모든 코스 기록을 지운다`() = runBlocking {
        val (store, ds) = open()
        store.record("CRS-01", COMPLETED)
        store.record("CRS-03", HAS_SKIPPED)
        store.clearAll()
        assertEquals(NOT_STARTED, store.result("CRS-01"))
        assertEquals(NOT_STARTED, store.result("CRS-03"))
        assertTrue(ds.data.first().asMap().isEmpty())
    }
}
