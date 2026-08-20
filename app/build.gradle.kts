plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

val generatedLegalResDir = layout.buildDirectory.dir("generated/legal-res")
val generateLegalResources by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE"))
    into(generatedLegalResDir.map { it.dir("raw") })
    rename { "gpl_3_0.txt" }
}

android {
    namespace = "com.hackerman.sohacksrev2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hackerman.sohacksrev2"
        minSdk = 25
        targetSdk = 34
        versionCode = 27
        versionName = "2.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

android.sourceSets.getByName("main").res.srcDir(generatedLegalResDir)
tasks.named("preBuild").configure { dependsOn(generateLegalResources) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.osmdroid.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
