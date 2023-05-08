plugins {
    kotlin("jvm") version "1.8.21"
}

group = "io.github.vinicreis"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")
    implementation ("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation (platform("org.junit:junit-bom:5.9.1"))
    testImplementation ("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}