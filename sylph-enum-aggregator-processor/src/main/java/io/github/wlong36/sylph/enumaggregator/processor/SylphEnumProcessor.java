package io.github.wlong36.sylph.enumaggregator.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import io.github.wlong36.sylph.enumaggregator.api.SylphEnumReg;
import io.github.wlong36.sylph.enumaggregator.api.SylphEnumRegistrar;
import io.github.wlong36.sylph.enumaggregator.api.SylphEnumRegs;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 注解处理器，用于处理 @SylphEnumReg 注解。
 * 核心职责：
 * 1. 扫描所有使用了 @SylphEnumReg 注解的类/接口。
 * 2. 收集注解中定义的枚举条目信息 (id, name, desc, type)。
 * 3. 根据注解中的 type() 对定义进行分组 (每个 type 对应一个要生成的 Enum 类)。
 * 4. 对每个分组进行验证，检查 ID 和 Name 是否在同类型内唯一。
 * 5. 为每个分组生成一个聚合的 Java Enum 类，该类位于配置的包下，类名由 type() 决定，并实现 SylphEnum 接口。
 * 6. 在每个生成的 Enum 类内部，创建一个静态内部类 `ServiceProvider`，实现 {@link SylphEnumRegistrar} 接口。
 * 7. 这个内部类的 `doRegister` 方法负责调用注册上下文的 `register` 方法，传入生成的 Enum 的 Class 对象。
 * 8. 生成标准的 Java ServiceLoader 文件 (`META-INF/services/io...SylphEnumRegistrar`)，列出所有生成的内部 `ServiceProvider` 类的全限定名。
 *
 * @author wlong
 * @since 2024/4/12
 */
@AutoService(Processor.class) // 自动注册处理器
@SupportedAnnotationTypes({
		"io.github.wlong36.sylph.enumaggregator.api.SylphEnumReg",
		"io.github.wlong36.sylph.enumaggregator.api.SylphEnumRegs"
}) // 只处理这个注解
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class SylphEnumProcessor extends AbstractProcessor {
	private static final String OPTION_TARGET_PACKAGE = "sylph.enum.generated.package";
	private static final String DEFAULT_TARGET_PACKAGE = "io.github.wlong36.sylph.enumaggregator.generated";
	private static final ClassName NONNULL = ClassName.get("javax.annotation", "Nonnull");
	private static final ClassName CONTEXT = ClassName.get("io.github.wlong36.sylph.enumaggregator.api", "SylphEnumRegistrationContext");
	private static final ClassName SYLPH_ENUM = ClassName.get("io.github.wlong36.sylph.enumaggregator.api", "SylphEnum");
	private static final String providerClassName = "ServiceProvider";

	private String configuredTargetPackage = null; // Store the configured package name
	private Messager messager; // 用于报告错误和警告
	private Filer filer;      // 用于创建文件 (生成的源代码和资源文件)
//	private Elements elementUtils; // 用于处理程序元素 (包、类、方法等)
//	private Types typeUtils;       // 用于处理类型 (TypeMirror)

	// 存储收集到的定义数据，按目标 SylphEnum 接口分组
	// Key: 目标接口的 TypeMirror
	// Value: 该接口对应的所有定义条目 Set<DefinitionData>
	private final Map<String, Set<DefinitionData>> collectedDefinitions = new HashMap<>();
	// 存储已使用的 ID 和 Name，用于更高效的冲突检查
	// Key: 枚举类型名称 (type)
	// Value: 该类型下已使用的 ID 集合
	private final Map<String, Set<Integer>> usedIdsByType = new HashMap<>();
	// Key: 枚举类型名称 (type)
	// Value: 该类型下已使用的 Name 集合 (Key: Name, Value: 冲突发生时的原始元素)
	private final Map<String, Map<String, Element>> usedNamesByType = new HashMap<>();

	// 存储已生成的服务提供者内部类的完全限定名，用于生成 ServiceLoader 文件
	private final Set<String> generatedProviderClassNames = new HashSet<>();

	// 用于标记是否已经执行过生成逻辑
	private final AtomicBoolean hasGenerated = new AtomicBoolean(false);

	// 内部类，用于存储一个 @SylphEnumReg 注解的信息
	private static class DefinitionData implements Comparable<DefinitionData> {
		//自定义编号
		private final int id;
		//枚举常量名 (来自注解的 name)
		private final String name;
		//来自 desc()
		private final String description;
		//枚举类型名 type()
		private final String enumType;
//		注解所在的元素，用于错误报告
//		private final Element originatingElement;

		public DefinitionData(int id, String name, String description, String enumType, Element originatingElement) {
			this.id = id;
			this.name = name;
			this.description = description;
			this.enumType = enumType;
//			this.originatingElement = originatingElement;
		}

		// 按 ID 排序，用于生成有序的枚举常量
		@Override
		public int compareTo(DefinitionData other) {
			return Integer.compare(this.id, other.id);
		}

		// 基于 name 和 targetInterface 判断重复（在同一接口下名称唯一）
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			DefinitionData that = (DefinitionData) o;
			return name.equals(that.name) && enumType.equals(that.enumType);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, enumType);
		}
	}

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		this.messager = processingEnv.getMessager();
		this.filer = processingEnv.getFiler();
//		this.elementUtils = processingEnv.getElementUtils();
//		this.typeUtils = processingEnv.getTypeUtils();

		this.hasGenerated.set(false);

		// --- 读取配置参数 ---
		Map<String, String> options = processingEnv.getOptions();
		this.configuredTargetPackage = options.get(OPTION_TARGET_PACKAGE);

		if (this.configuredTargetPackage != null && !this.configuredTargetPackage.trim().isEmpty()) {
			this.configuredTargetPackage = this.configuredTargetPackage.trim();
			// 验证包名是否合法
			if (!isValidPackageName(this.configuredTargetPackage)) {
				error(null, "无效的包名在处理器选项 '%s': %s", OPTION_TARGET_PACKAGE, this.configuredTargetPackage);
				// 可以选择让处理器失败，或者回退到默认行为。这里我们让它后面回退。
				this.configuredTargetPackage = DEFAULT_TARGET_PACKAGE; // 标记为无效以便后续使用默认值
			} else {
				log("将使用配置的包 '%s' 来生成 Enum 文件。", this.configuredTargetPackage);
			}
		} else {
			this.configuredTargetPackage = DEFAULT_TARGET_PACKAGE;
			log("处理器选项 '%s' 未设置。将在目标位置 '%s' 生成 Enum 文件 (默认行为)。", OPTION_TARGET_PACKAGE, DEFAULT_TARGET_PACKAGE);
			// configuredTargetPackage 保持为 null
		}

		log("SylphEnumProcessor initialized.");
	}

	// 添加包名验证辅助方法
	private boolean isValidPackageName(String packageName) {
		if (packageName == null || packageName.isEmpty()) {
			return false;
		}
		String[] parts = packageName.split("\\.", -1); // 使用点分割
		for (String part : parts) {
			if (!SourceVersion.isIdentifier(part) || SourceVersion.isKeyword(part)) {
				// 每个部分必须是有效标识符且非关键字
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (hasGenerated.get()) {
			log("Files have already been generated in a previous round, skipping further processing.");
			return true;
		}
		// 收集定义信息
		collectDefinitions(roundEnv);

		try {
			generateFiles();
		} catch (IOException e) {
			error(null, "Failed to generate files: %s", e.getMessage());
		}

		hasGenerated.set(true);
		// 返回 false 表示这些注解可能还需要后续处理器处理（虽然这里我们是唯一处理者）
		// 返回 true 表示我们声明独占处理这些注解
		return true;
	}

	/**
	 * 从当前处理轮次 (RoundEnvironment) 中收集 @SylphEnumReg 注解定义的元数据。
	 * 进行验证并存储到内部数据结构中。
	 */
	private void collectDefinitions(RoundEnvironment roundEnv) {
		log(roundEnv.toString());
		// 查找所有使用了 @SylphEnumReg 注解的元素 (接口或类)
		for (Element element : roundEnv.getElementsAnnotatedWith(SylphEnumRegs.class)) {
			// 获取该元素上所有的 @SylphEnumReg 实例 (处理可重复注解)
			SylphEnumReg[] regsOnElement = element.getAnnotationsByType(SylphEnumReg.class);

			// 遍历找到的每个注解实例
			for (SylphEnumReg regAnnotation : regsOnElement) {
				String enumType = regAnnotation.type();
				int id = regAnnotation.id();
				String name = regAnnotation.name();
				String desc = regAnnotation.desc(); // 获取描述

				// --- 基本验证 ---
				if (isInvalidJavaIdentifier(enumType)) {
					error(element, "注解属性 'type' 的值 '%s' 不是一个有效的 Java 类名标识符。", enumType);
					continue; // 跳过这个无效的定义
				}
				if (isInvalidJavaIdentifier(name)) {
					error(element, "注解属性 'name' 的值 '%s' 不是一个有效的 Java 枚举常量标识符。", name);
					continue; // 跳过这个无效的定义
				}

				// --- 冲突检查 (优化后的版本) ---
				// 获取或初始化该 enumType 的 ID 和 Name 记录
				Set<Integer> currentIds = usedIdsByType.computeIfAbsent(enumType, k -> new HashSet<>());
				Map<String, Element> currentNames = usedNamesByType.computeIfAbsent(enumType, k -> new HashMap<>());

				// 检查 ID 冲突
				if (!currentIds.add(id)) { // add 返回 false 表示已存在
					// 尝试找到之前的定义位置 (如果存储了 Element)
					// 注意：这里无法直接获取到之前添加相同 ID 的 DefinitionData，因为我们只存了 ID 集合。
					// 可以在 usedIdsByType 中存储 Map<Integer, Element> 来改进错误信息，但这会增加内存。
					// 简单的错误信息：
					error(element, "枚举类型 '%s' 中检测到重复的 ID: %d (用于名称 '%s')", enumType, id, name);
					continue;
				}

				// 检查 Name 冲突
				Element conflictingElement = currentNames.putIfAbsent(name, element); // putIfAbsent 返回 null 表示没有旧值
				if (conflictingElement != null) { // 如果返回非 null，表示名称已存在
					// 清理刚刚添加的 ID，因为名称冲突导致此条目无效
					currentIds.remove(id);
					// 报告名称冲突，并指出冲突来源
					error(element, "枚举类型 '%s' 中检测到重复的名称: '%s' (ID: %d)。它与在 %s 处定义的条目冲突。",
						  enumType, name, id, conflictingElement.getSimpleName());
					continue;
				}

				// --- 存储有效定义 ---
				// 获取或创建该 enumType 的定义集合 (使用 TreeSet 自动按 ID 排序)
				Set<DefinitionData> definitionsForType = collectedDefinitions.computeIfAbsent(enumType, k -> new TreeSet<>());
				DefinitionData data = new DefinitionData(id, name, desc, enumType, element);
				definitionsForType.add(data); // 添加到集合

				log("收集到定义: Type=%s, ID=%d, Name=%s (来源: %s)", enumType, id, name, element.getSimpleName());
			}
		}
	}

	/**
	 * 生成所有必需的 Enum 文件和 Service 文件。
	 */
	private void generateFiles() throws IOException {
		if (collectedDefinitions.isEmpty()) {
			log("No definitions collected, skipping file generation.");
			return;
		}

		log("Starting file generation phase...");
		generatedProviderClassNames.clear(); // 清空上一轮可能遗留的数据

		// 为每个目标接口生成一个 Enum 类
		for (Map.Entry<String, Set<DefinitionData>> entry : collectedDefinitions.entrySet()) {
			String enumType = entry.getKey();
			Set<DefinitionData> definitions = entry.getValue(); // TreeSet, 已按 ID 排序

			if (definitions.isEmpty()) {
				continue;
			}

			generateEnumFile(enumType, definitions);
		}

		// 如果生成了任何提供者，则生成 ServiceLoader 文件
		if (!generatedProviderClassNames.isEmpty()) {
			generateServiceLoaderFile();
		}
		log("File generation phase completed.");
	}

	/**
	 * 为给定的目标接口和定义集生成一个 Enum 文件。
	 */
	private void generateEnumFile(String enumType, Set<DefinitionData> definitions) {
		// --- 使用最终确定的包名和类名 ---
		ClassName enumClassName = ClassName.get(configuredTargetPackage, enumType);

		// ... (字段、构造函数、方法、枚举常量的定义保持不变) ...
		FieldSpec idField = FieldSpec.builder(int.class, "id", Modifier.PRIVATE, Modifier.FINAL).build();
		// ... constructor, idGetter, nameGetter, ordinalGetter ...
		MethodSpec constructor = MethodSpec.constructorBuilder() // ... build constructor ...
				.addModifiers(Modifier.PRIVATE)
				.addParameter(int.class, "id")
				.addStatement("this.$N = $N", idField, "id")
				.build();
		MethodSpec idGetter = MethodSpec.methodBuilder("id") // ... build id getter ...
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.returns(int.class)
				.addStatement("return this.$N", idField)
				.build();

		TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(enumClassName)
				.addModifiers(Modifier.PUBLIC)
				.addSuperinterface(SYLPH_ENUM)
				.addField(idField)
				.addMethod(constructor)
				.addMethod(idGetter);

		for (DefinitionData data : definitions) {
			String constantName = data.name;
			int constantId = data.id;
			String constantDesc = data.description;
			TypeSpec anonymousClass = TypeSpec.anonymousClassBuilder("$L", constantId)
					.addJavadoc(constantDesc)
					.build();
			enumBuilder.addEnumConstant(constantName, anonymousClass);
		}

		MethodSpec doRegisterMethod = MethodSpec.methodBuilder("doRegister")
				.addAnnotation(Override.class)
				.addAnnotation(NONNULL)
				.addModifiers(Modifier.PUBLIC)
				.addParameter(ParameterSpec.builder(CONTEXT, "context").addAnnotation(NONNULL).build())
				.addStatement("context.register($T.class);", enumClassName)
				.build();

		TypeSpec providerClass = TypeSpec.classBuilder(providerClassName)
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
				.addSuperinterface(SylphEnumRegistrar.class)
				.addMethod(doRegisterMethod)
				.addJavadoc("自动生成的服务提供者，用于向 $T 注册 $T 的枚举常量。", CONTEXT, enumClassName)
				.build();

		enumBuilder.addType(providerClass);
		addGeneratedAnnotation(enumBuilder);

		// --- 写入文件 (使用最终确定的包名) ---
		try {
			JavaFile javaFile = JavaFile.builder(configuredTargetPackage, enumBuilder.build()) // 使用 finalOutputPackageName
					.indent("    ")
					.skipJavaLangImports(true)
					.build();
			javaFile.writeTo(filer);
			log("已生成枚举文件: %s", enumClassName);

			// --- 记录生成的提供者类的 FQN (使用最终的包名) ---
			String providerFQN = enumClassName + "." + providerClassName;
			generatedProviderClassNames.add(providerFQN);

		} catch (IOException e) {
			error(null, "无法写入生成的枚举文件: %s.%s\n%s", configuredTargetPackage, enumClassName, e.getMessage());
		}
	}

	/**
	 * 生成 META-INF/services/io.github.wlong36.sylph.enumaggregator.api.SylphEnumRegistrar 文件。
	 */
	private void generateServiceLoaderFile() throws IOException {
		String serviceInterfaceName = SylphEnumRegistrar.class.getCanonicalName();
		log("Generating META-INF/services file for %s", serviceInterfaceName);

		FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + serviceInterfaceName);
		try (Writer writer = fileObject.openWriter()) {
			for (String className : generatedProviderClassNames) {
				writer.write(className);
				writer.write("\n");
				log("  + Registered provider: %s", className);
			}
		}
		log("Finished generating META-INF/services file.");
	}

	/**
	 * 向 TypeSpec.Builder 添加 @Generated 注解 (如果可用)。
	 */
	private void addGeneratedAnnotation(TypeSpec.Builder builder) {
		ClassName generatedAnnotation = null;
		try {
			generatedAnnotation = ClassName.get("javax.annotation.processing", "Generated"); // Java 9+
		} catch (Exception ignored) {
			try {
				generatedAnnotation = ClassName.get("javax.annotation", "Generated"); // Java 6-8
			} catch (Exception ignored2) {
				// 注解不可用
			}
		}

		if (generatedAnnotation != null) {
			builder.addAnnotation(AnnotationSpec.builder(generatedAnnotation)
										  .addMember("value", "$S", SylphEnumProcessor.class.getName())
										  .addMember("comments", "$S", "Generated by sylph-enum-aggregator. DO NOT EDIT.")
										  .build());
		}
	}


	// --- 辅助方法 ---

	/**
	 * 检查给定的字符串是否是无效的 Java 标识符。
	 *
	 * @param s 要检查的字符串。
	 * @return 如果无效则返回 true，否则返回 false。
	 */
	private boolean isInvalidJavaIdentifier(String s) {
		if (s == null || s.isEmpty()) {
			return true; // 空字符串无效
		}
		// 第一个字符必须是 Java 标识符的起始字符
		if (!Character.isJavaIdentifierStart(s.charAt(0))) {
			return true;
		}
		// 后续字符必须是 Java 标识符的部分
		for (int i = 1; i < s.length(); i++) {
			if (!Character.isJavaIdentifierPart(s.charAt(i))) {
				return true;
			}
		}
		// 检查是否是 Java 关键字
		return SourceVersion.isKeyword(s);
	}

	private void log(String message, Object... args) {
		messager.printMessage(Diagnostic.Kind.NOTE, String.format(message, args));
	}

	private void warn(Element e, String message, Object... args) {
		messager.printMessage(Diagnostic.Kind.WARNING, String.format(message, args), e);
	}

	private void error(Element e, String message, Object... args) {
		messager.printMessage(Diagnostic.Kind.ERROR, String.format(message, args), e);
	}
}