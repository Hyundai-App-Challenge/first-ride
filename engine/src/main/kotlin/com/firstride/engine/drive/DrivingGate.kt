package com.firstride.engine.drive

import kotlinx.coroutines.flow.StateFlow

enum class DriveState { PARKED, RESTRICTED }

/** 구현의 초기값은 RESTRICTED여야 한다. 첫 값을 받기 전이나 등록 실패 시 제한 상태로 남는다(POL-D-7). */
interface DrivingGate { val state: StateFlow<DriveState> }
