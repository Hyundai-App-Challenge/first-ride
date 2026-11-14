plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    `java-test-fixtures`   // FakeSignalSource는 testFixtures에 둔다. app은 debug에서만 가져다 쓴다(release에 들어가지 않는다).
}

repositories { mavenCentral() }

kotlin { jvmToolchain(17) }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.test {
    useJUnitPlatform()
    // 테스트가 외부 모듈의 실제 코스·차종 자산을 읽으므로 데이터 변경도 입력으로 추적한다.
    inputs.dir(rootProject.file("app/src/main/assets"))
        .withPropertyName("courseAssets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
