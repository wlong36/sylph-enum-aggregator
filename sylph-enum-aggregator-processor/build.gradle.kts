dependencies {
    implementation(project(":sylph-enum-aggregator-api"))

    // 让你的代码能识别 @AutoService 注解
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    // 运行 AutoService 这个注解处理器来生成 META-INF/services 文件
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")

    // https://mvnrepository.com/artifact/com.squareup/javapoet
    implementation("com.squareup:javapoet:1.13.0")
    // https://mvnrepository.com/artifact/org.freemarker/freemarker
    implementation("org.freemarker:freemarker:2.3.34")
}