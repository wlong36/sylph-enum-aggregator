rootProject.name = "sylph-enum-aggregator"

pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
    }
}

include("sylph-enum-aggregator-api")
include("sylph-enum-aggregator-processor")
include("sylph-enum-aggregator-runtime")
include("sylph-enum-aggregator-test")
