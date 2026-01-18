plugins {
    id("java")
    id("me.champeau.jmh") version "0.7.2"
}

group = "com.itmo"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    implementation("it.unimi.dsi:fastutil:8.5.13")
}

tasks.test {
    useJUnitPlatform()
}

jmh {
    warmupIterations.set(5)
    iterations.set(10)
    fork.set(1)
    timeUnit.set("ms")

    includes.set(listOf(".*BTree.*"))
}