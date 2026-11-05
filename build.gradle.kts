plugins {
    id("com.android.application") version "8.7.3" apply false
}

subprojects {
    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir(name))
}
