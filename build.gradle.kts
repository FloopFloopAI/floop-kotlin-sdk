plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    `maven-publish`
}

group = "com.floopfloop"
version = "0.1.0-alpha.3"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

kotlin {
    jvmToolchain(11)
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
}

// JitPack publishes a "jar" via maven-publish; we expose a simple
// configuration so `com.github.FloopFloopAI:floop-kotlin-sdk:<tag>`
// resolves cleanly for downstream Gradle/Maven consumers.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("floop-kotlin-sdk")
                description.set("Official Kotlin SDK for the FloopFloop API.")
                url.set("https://github.com/FloopFloopAI/floop-kotlin-sdk")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
