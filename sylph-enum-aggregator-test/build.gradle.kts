import com.google.protobuf.gradle.*
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.Copy
import org.gradle.api.file.FileCollection
import org.gradle.api.artifacts.Configuration // 导入 Configuration

plugins {
    id("java")
    id("com.google.protobuf") version "0.9.5"
}

repositories {
    mavenCentral()
}

// --- 创建一个专门用于 APT 任务的独立配置 ---
val aptClasspath: Configuration = configurations.create("aptClasspath") {
    isCanBeResolved = true // 可以被解析获取文件
    isCanBeConsumed = false // 不应被其他项目消费
    // 继承 compileClasspath，这样可以自动获得外部库依赖
    // 但是！这可能仍然会引入问题，我们需要在后面手动过滤
    // 或者，不继承，手动添加所需依赖
    // extendsFrom(configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME))
}


dependencies {
    // 注解处理器添加到 annotationProcessor 和我们自定义的 aptClasspath
    annotationProcessor(project(":sylph-enum-aggregator-processor"))
    aptClasspath(project(":sylph-enum-aggregator-processor")) // 处理器本身需要在这个 classpath

    // 将项目运行时需要的、且注解处理器在分析代码时需要引用的库，添加到 aptClasspath
    // 例如，如果处理器需要 protobuf-java 的注解或类型
    aptClasspath("com.google.protobuf:protobuf-java:4.27.2")
    // 添加其他你的注解处理器分析时需要的库...
    // aptClasspath("com.google.code.findbugs:jsr305:3.0.2") // 举例

    // 常规依赖
    implementation(project(":sylph-enum-aggregator-runtime"))
    implementation("com.google.protobuf:protobuf-java:4.27.2")
    testImplementation("junit:junit:4.13.2")
}

// --- 手动控制两阶段编译 ---

// 1. 定义路径
val aptGeneratedProtoDir = layout.buildDirectory.dir("generated/sources/aptProtoGenerator/proto")
val aptTempClassOutput = layout.buildDirectory.dir("generated/sources/aptProtoGenerator/tempClasses")
val generatedProtoJavaDir = layout.buildDirectory.dir("generated/source/proto/main/java")

// 2. 阶段一：仅运行 APT 生成 Proto 文件，使用隔离的 Classpath
val generateProtoSources = tasks.register<JavaCompile>("generateProtoSources") {
    group = "generation"
    description = "Runs Annotation Processor to generate Proto source files."

    source = sourceSets.main.get().java

    // !! 关键：使用我们自定义的、隔离的 aptClasspath !!
    classpath = configurations.getByName("aptClasspath")

    // Annotation Processor Path 仍然来自标准配置
    options.annotationProcessorPath = configurations.getByName(sourceSets.main.get().annotationProcessorConfigurationName)

    options.generatedSourceOutputDirectory.set(aptGeneratedProtoDir.get().asFile)
    options.compilerArgs = listOf("-proc:only")
    destinationDirectory.set(aptTempClassOutput)
}

// 3. 配置 Protobuf 任务
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.27.2"
    }
    generateProtoTasks {
        all().forEach { task ->
            // 强制在 APT 生成 Proto 之后运行
            task.mustRunAfter(generateProtoSources)
            task.builtins {
                java {}
            }
        }
    }
}

// 4. 配置源集，包含 APT 生成的 Proto 目录
sourceSets {
    main {
        proto {
            srcDir("src/main/proto")
            srcDir(aptGeneratedProtoDir)
        }
    }
}

// 5. 配置主 `compileJava` 任务
tasks.named<JavaCompile>("compileJava") {
    // 强制在 Protobuf 生成 Java 之后运行
    mustRunAfter(tasks.withType<GenerateProtoTask>())

    // 禁用此任务的注解处理
    options.compilerArgs.add("-proc:none")
}

// --- 其他任务配置 ---
// 6. 配置 processResources (保持不变)
tasks.named<Copy>("processResources") {
    dependsOn(tasks.named("compileJava"))
    exclude("**/*.proto")
}

// 7. 配置 Clean 任务 (保持不变)
tasks.named("clean") {
    delete(protobuf.generatedFilesBaseDir)
    delete(aptGeneratedProtoDir.get().asFile.parentFile)
    delete(layout.buildDirectory.dir("generated/sources/annotationProcessor"))
}