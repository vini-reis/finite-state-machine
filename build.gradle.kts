plugins {
    kotlin("jvm") version "1.8.21"
    `maven-publish`
    signing
}

group = "io.github.vinicreis"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")
    implementation ("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

java {
    withJavadocJar()
    withSourcesJar()
}

task("getVersion") {
    doLast {
        println(project.version)
    }
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

val mavenUrl: String = findProperty("mavenUrl").toString()
val mavenUsername: String = findProperty("mavenUsername").toString()
val mavenPassword: String = findProperty("mavenPassword").toString()

publishing {
    repositories {
        maven {
            url = uri(mavenUrl)
            credentials {
                username = mavenUsername
                password = mavenPassword
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = rootProject.group.toString()
            artifactId = "finite-state-machine-async-ktx"
            version = rootProject.version.toString()
            from(components["java"])

            artifacts {
                archives(tasks["sourcesJar"])
            }

            pom {
                name.set("Kotlin Async Finite State Machine")
                description.set("A state machine design pattern implementation to run async FSMs in Kotlin")
                url.set("https://github.com/vinicreis/finite-state-machine")

                licenses {
                    license {
                        name.set("GNU General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/gpl-3.0.en.html")
                    }
                }

                developers {
                    developer {
                        id.set("vinicreis")
                        name.set("Vinícius Reis")
                        email.set("vnc.reis@outlook.com")
                    }
                }

                scm {
                    connection.set("git@github.com:vinicreis/finite-state-machine.git")
                    developerConnection.set("git@github.com:vinicreis/finite-state-machine.git")
                    url.set("https://github.com/vinicreis/finite-state-machine.git")
                }
            }
        }
    }
}

val signingKey = findProperty("signingKey").toString()
val signingPassword = findProperty("signingPassword").toString()

signing {
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
}