package com.firstride.engine.progress

enum class CourseResult { NOT_STARTED, HAS_SKIPPED, COMPLETED }   // 순서가 곧 우열

interface ProgressStore {
    suspend fun result(courseId: String): CourseResult
    suspend fun record(courseId: String, attempt: CourseResult)   // max(저장값, attempt)로 저장 (POL-R-7)
    suspend fun badges(): Set<String>
    suspend fun clearAll()                                         // 설정값은 남긴다 (POL-S-5)
}
