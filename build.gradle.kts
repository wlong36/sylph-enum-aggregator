plugins {
    kotlin("jvm") version "2.0.21"
}

allprojects {
    group = "io.github.wlong36"
    version = "1.0-SNAPSHOT"

    plugins.apply("java")

    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")

        compileOnly("com.google.code.findbugs:jsr305:3.0.2")
        testCompileOnly("com.google.code.findbugs:jsr305:3.0.2")
    }

    tasks.test {
        useJUnitPlatform()
    }
}