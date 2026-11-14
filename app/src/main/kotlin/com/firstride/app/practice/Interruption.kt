package com.firstride.app.practice

/**
 * 주행 전환이나 백그라운드 전환으로 끊긴 실습의 위치. 코스와 단계 번호와 시각만 기억한다(POL-R-10).
 * 메모리에만 둔다. 앱이 종료됐다 다시 실행되면 묻지 않는다(POL-R-13).
 */
data class Interruption(val courseId: String, val stepIndex: Int, val atMillis: Long) {
    /** 끊긴 지 [RESUME_WINDOW_MILLIS]가 지나지 않았으면 이어서 하기를 묻는다(POL-R-12). */
    fun canResume(nowMillis: Long): Boolean = nowMillis - atMillis <= RESUME_WINDOW_MILLIS

    companion object {
        /** 이어서 하기 유효 시간 30분 [가정] (정책서 12절). */
        const val RESUME_WINDOW_MILLIS = 30 * 60 * 1000L
    }
}
