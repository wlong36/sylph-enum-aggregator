plugins {
    `java-library`
}

dependencies {
    api(project(":sylph-enum-aggregator-api"))

    // https://mvnrepository.com/artifact/com.google.protobuf/protobuf-java
    implementation("com.google.protobuf:protobuf-java:4.30.2")
}