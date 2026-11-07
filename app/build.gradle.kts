plugins {
    id("com.android.application")
}

val generatedLauncherResources = layout.buildDirectory.dir("generated/launcherRes")
val generateLauncherIcon by tasks.registering(Copy::class) {
    from(layout.projectDirectory.file("assets/icons/meshdropicon.png"))
    into(generatedLauncherResources.map { it.dir("mipmap-nodpi") })
    rename { "ic_launcher.jpg" }
}

android {
    namespace = "com.meshdrop"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.meshdrop"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

android.sourceSets.getByName("main").res.srcDir(generatedLauncherResources)

tasks.named("preBuild").configure {
    dependsOn(generateLauncherIcon)
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.yalantis:ucrop:2.2.11")
}
