package com.firstride.app

/**
 * 앱의 화면(사용자 플로우 1절). [AppViewModel]이 지금 화면 하나를 낸다.
 * 주행 중 차단(SCR-08)과 배지 획득(SCR-07)은 화면 전환이 아니라 그 위에 겹쳐 그린다(기술 설계 12절).
 */
sealed interface Screen {
    /** SCR-01 시작: 차량 연결, 지원 여부 확인 */
    data object Start : Screen
    /** SCR-02 홈 */
    data object Home : Screen
    /** SCR-03 코스 소개 */
    data class CourseIntro(val courseId: String) : Screen
    /** SCR-04 단계 진행 */
    data class Step(val courseId: String) : Screen
    /** SCR-05 코스 완료 */
    data class CourseDone(val courseId: String) : Screen
    /** SCR-06 소등 확인 */
    data object LightsCheck : Screen
    /** SCR-09 연결 오류 */
    data object ConnectionError : Screen
    /** SCR-10 설정 */
    data object Settings : Screen
    /** SCR-11 후속 조치 확인 */
    data class FollowUp(val courseId: String) : Screen
}
