import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

// 서명 값은 저장소 밖의 파일에서 읽는다. 기본 위치는 ~/.android-keys/keystore.properties,
// 다른 곳에 두었으면 -Pfirstride.signing=<경로>로 넘긴다. 파일이 없으면 release APK는 서명되지 않는다.
val signingFile = file(
    providers.gradleProperty("firstride.signing").orNull
        ?: "${System.getProperty("user.home")}/.android-keys/keystore.properties"
)
val signing = signingFile.takeIf { it.isFile }?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

// 제출 규정의 APK 이름(팀명_앱이름_release.apk). 앱 이름(D1)이 정해지기 전의 임시값이다.
val apkName = "first-ride_release.apk"

android {
    namespace = "com.firstride"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.firstride"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (signing != null) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":platform"))
    debugImplementation(testFixtures(project(":engine")))   // 가짜 신호원. release에는 들어가지 않는다
    debugImplementation("ai.pleos.playground:Vehicle:2.3.2")   // debug의 실측 화면이 SDK를 직접 부른다

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation(testFixtures(project(":engine")))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

// release APK를 apk/ 폴더에 제출용 이름으로 복사한다.
val copyReleaseApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*.apk") }
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { apkName }
}
afterEvaluate { tasks.named("assembleRelease") { finalizedBy(copyReleaseApk) } }
