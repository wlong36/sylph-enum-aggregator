package io.github.wlong36.sylph.enumaggregator.processor;

import com.google.auto.service.AutoService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;
import io.github.wlong36.sylph.enumaggregator.api.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 注解处理器，用于处理 @SylphEnumReg 注解。
 * ... (rest of the javadoc) ...
 */
@AutoService(Processor.class) // 自动注册处理器
@SupportedAnnotationTypes({
	"io.github.wlong36.sylph.enumaggregator.api.SylphEnumReg",
	"io.github.wlong36.sylph.enumaggregator.api.SylphEnumRegs"
}) // 只处理这个注解
@SupportedSourceVersion(SourceVersion.RELEASE_21) // Updated to match your code
public class SylphEnumProcessor extends AbstractProcessor {
	private static final String OPTION_PROTO_PACKAGE = "sylph.enumaggregator.proto.package";
	private static final String DEFAULT_PROTO_PACKAGE = "proto"; // Default for both if not specified
	private static final String OPTION_REGISTRAR_PACKAGE = "sylph.enumaggregator.registrar.package";
	private static final String DEFAULT_REGISTRAR_PACKAGE = "io.github.wlong36.sylph.enumaggregator.generated"; // Default for both if not specified

	private static final String OPTION_PROTO_TEMPLATE = "sylph.enumaggregator.proto.template";
	private static final String DEFAULT_PROTO_TEMPLATE = "default_enum_proto.ftl";
	private static final String OPTION_REGISTRAR_TEMPLATE = "sylph.enumaggregator.registrar.template";
	private static final String DEFAULT_REGISTRAR_TEMPLATE = "default_enum_registrar.ftl"; // Use the template name you provided

	// ** Corrected Registrar Class Name to avoid conflict with interface **
	private static final String registrarClassName = "SylphEnumRegistrarImpl"; // Implementation class name

	private String protoPackage = null;// Store the configured package name
	private String registrarPackage = null;
	private Configuration templateCfg;// 用于加载模板文件
	private Template protoTemplate;
	private Template registrarTemplate;

	private Messager messager; // 用于报告错误和警告
	private Filer filer;      // 用于创建文件 (生成的源代码和资源文件)

	// Data structures remain the same...
	private final Map<String, Set<DefinitionData>> collectedDefinitions = new HashMap<>();
	private final Map<String, Set<Integer>> usedIdsByType = new HashMap<>();
	private final Map<String, Map<String, Element>> usedNamesByType = new HashMap<>();
	private boolean hasGenerated = false;

	// DefinitionData inner class remains the same...
	private static class DefinitionData implements Comparable<DefinitionData> {
		//自定义编号 id()
		private final int id;
		//枚举常量名 name()
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

		public int getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public String getDescription() {
			return description;
		}

		public String getEnumType() {
			return enumType;
		}

		// compareTo, equals, hashCode remain the same...
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
			// Corrected: Should compare based on ID within the same type for uniqueness tracking primarily
			// Name check is done separately. However, for Set uniqueness, using name+type is fine.
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

		this.hasGenerated = false;

		// --- 读取配置参数 ---
		Map<String, String> options = processingEnv.getOptions();

		this.protoPackage = options.getOrDefault(OPTION_PROTO_PACKAGE, DEFAULT_PROTO_PACKAGE);
		this.registrarPackage = options.getOrDefault(OPTION_REGISTRAR_PACKAGE, DEFAULT_REGISTRAR_PACKAGE);

		if (isInvalidPackageName(this.protoPackage)) {
			error(null, "无效的proto文件生成路径在处理器选项 '%s': %s", OPTION_PROTO_PACKAGE, this.protoPackage);
			// Consider not throwing here, but failing the build via error message return
			return; // Stop init if invalid
		}
		if (isInvalidPackageName(this.registrarPackage)) {
			error(null, "无效的java文件生成在处理器选项 '%s': %s", OPTION_REGISTRAR_PACKAGE, this.registrarPackage);
			return; // Stop init if invalid
		}
		log("将在目标位置 '%s' 生成 Proto 文件", this.protoPackage);
		log("将在目标位置 '%s' 生成 %s.java", this.registrarPackage, registrarClassName);

		// --- FreeMarker Initialization ---
		templateCfg = new Configuration(new Version("2.3.32")); // Use your FreeMarker version

		templateCfg.setClassForTemplateLoading(getClass(), "/templates"); // Path relative to JAR root
		templateCfg.setDefaultEncoding("UTF-8");
		templateCfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		templateCfg.setLogTemplateExceptions(false);
		templateCfg.setWrapUncheckedExceptions(true);
		templateCfg.setFallbackOnNullLoopVariable(false);
		templateCfg.setNumberFormat("computer");

		String enumProtoTemplatePath = options.getOrDefault(OPTION_PROTO_TEMPLATE, DEFAULT_PROTO_TEMPLATE);
		log("enum proto 模板文件：%s", enumProtoTemplatePath);
		try {
			this.protoTemplate = templateCfg.getTemplate(enumProtoTemplatePath);
		} catch (IOException e) {
			error(null, "无法加载 enum proto 模板: %s. 请确保它在类路径 '/templates' 下. 错误: %s", enumProtoTemplatePath, e.getMessage());
			this.protoTemplate = null; // Mark as unloaded
		}

		String enumRegistrarTemplatePath = options.getOrDefault(OPTION_REGISTRAR_TEMPLATE, DEFAULT_REGISTRAR_TEMPLATE);
		log("enum registrar 模板文件：%s", enumRegistrarTemplatePath);
		try {
			this.registrarTemplate = templateCfg.getTemplate(enumRegistrarTemplatePath);
		} catch (IOException e) {
			error(null, "无法加载 enum registrar 模板: %s. 请确保它在类路径 '/templates' 下. 错误: %s", enumRegistrarTemplatePath, e.getMessage());
			this.registrarTemplate = null; // Mark as unloaded
		}

		log("SylphEnumProcessor initialized.");
	}

	// isInvalidPackageName remains the same...
	private boolean isInvalidPackageName(String packageName) {
		if (packageName == null || packageName.isEmpty()) {
			return true;
		}
		String[] parts = packageName.split("\\.", -1); // Use point as delimiter
		for (String part : parts) {
			if (!SourceVersion.isIdentifier(part) || SourceVersion.isKeyword(part)) {
				// Each part must be a valid identifier and not a keyword
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		// Check if templates loaded successfully in init
		if (this.protoTemplate == null || this.registrarTemplate == null) {
			error(null, "模板加载失败，处理器无法继续。请检查之前的错误日志。");
			return false; // Stop processing if templates aren't ready
		}
		if (hasGenerated) {
			return true;
		}

		hasGenerated = true;

		// Collect definitions in intermediate rounds
		collectDefinitions(roundEnv);

		try {
			generateFiles();
		} catch (IOException e) {
			error(null, "生成文件时出错: %s", e.getMessage());
			e.printStackTrace(); // Print stacktrace for debugging
		}


		return true; // Claim the annotations
	}

	// collectDefinitions remains largely the same...
	private void collectDefinitions(RoundEnvironment roundEnv) {
		// Find elements annotated with the repeatable container @SylphEnumRegs
		for (Element element : roundEnv.getElementsAnnotatedWith(SylphEnumRegs.class)) {
			processElementForAnnotations(element);
		}
		// Also find elements annotated directly with @SylphEnumReg (handles non-repeating case)
		for (Element element : roundEnv.getElementsAnnotatedWith(SylphEnumReg.class)) {
			// Avoid double processing if also annotated with @SylphEnumRegs
			if (element.getAnnotation(SylphEnumRegs.class) == null) {
				processElementForAnnotations(element);
			}
		}
	}

	private void processElementForAnnotations(Element element) {
		SylphEnumReg[] regsOnElement = element.getAnnotationsByType(SylphEnumReg.class);

		for (SylphEnumReg regAnnotation : regsOnElement) {
			String enumType = regAnnotation.type(); // This is the Java Enum Class Name
			int id = regAnnotation.id();
			String name = regAnnotation.name(); // This is the Java Enum Constant Name
			String desc = regAnnotation.desc();

			// --- Basic Validation ---
			if (isInvalidJavaIdentifier(enumType)) {
				error(element, "注解属性 'type' 的值 '%s' 不是一个有效的 Java 类名标识符。", enumType);
				continue;
			}
			if (isInvalidJavaIdentifier(name)) {
				error(element, "注解属性 'name' 的值 '%s' 不是一个有效的 Java 枚举常量标识符。", name);
				continue;
			}
			if (id == 0 && !name.endsWith("_UNSPECIFIED")) {
				warn(element, "ID 0 通常保留给 '%s_UNSPECIFIED'。为 '%s' 定义 ID 0 可能违反 Protobuf 约定。", enumType, name);
			}

			// --- Conflict Check ---
			Set<Integer> currentIds = usedIdsByType.computeIfAbsent(enumType, k -> new HashSet<>());
			Map<String, Element> currentNames = usedNamesByType.computeIfAbsent(enumType, k -> new HashMap<>());

			if (!currentIds.add(id)) {
				error(element, "枚举类型 '%s' 中检测到重复的 ID: %d (用于名称 '%s')", enumType, id, name);
				continue;
			}

			Element conflictingElement = currentNames.putIfAbsent(name, element);
			if (conflictingElement != null) {
				currentIds.remove(id); // Rollback ID addition
				error(element, "枚举类型 '%s' 中检测到重复的名称: '%s' (ID: %d)。它与在 %s (%s) 处定义的条目冲突。",
					enumType, name, id, conflictingElement.getSimpleName(), conflictingElement.getEnclosingElement().getSimpleName());
				continue;
			}

			// --- Store Valid Definition ---
			Set<DefinitionData> definitionsForType = collectedDefinitions.computeIfAbsent(enumType, k -> new TreeSet<>());
			DefinitionData data = new DefinitionData(id, name, desc, enumType, element);
			if (definitionsForType.add(data)) { // Add returns true if it was actually added
				log("收集到定义: Type=%s, ID=%d, Name=%s (来源: %s in %s)",
					enumType, id, name, element.getSimpleName(), element.getEnclosingElement().getSimpleName());
			} else {
				// This case should ideally not happen if name check works, but good to log
				warn(element, "尝试添加重复定义 (基于 equals/hashCode): Type=%s, Name=%s", enumType, name);
				// Rollback ID and Name additions if the set didn't accept it
				currentIds.remove(id);
				currentNames.remove(name);
			}
		}
	}


	/**
	 * Generates all necessary files (Proto Enums, Registrar, ServiceLoader).
	 */
	private void generateFiles() throws IOException {
		if (collectedDefinitions.isEmpty()) {
			log("未收集到定义，跳过文件生成。");
			return;
		}

		log("开始文件生成阶段...");

		// Generate Proto Enum files for each type
		for (Map.Entry<String, Set<DefinitionData>> entry : collectedDefinitions.entrySet()) {
			String enumType = entry.getKey(); // This is the Java Enum Name (e.g., "ConditionType")
			Set<DefinitionData> definitions = entry.getValue(); // TreeSet, already sorted by ID

			// Check if there are definitions for this type before generating
			if (definitions != null && !definitions.isEmpty()) {
				generateEnumProtoFile(enumType, definitions);
			} else {
				warn(null, "发现空的定义集 '%s'，跳过生成 proto 文件。", enumType);
			}
		}

		// Generate the single Registrar Impl Java file containing all types
		generateEnumRegistrarFile();

		// Generate the ServiceLoader file pointing to the Registrar Impl
		generateServiceLoaderFile();

		log("文件生成阶段完成。");
	}

	/**
	 * Generates a .proto file for a specific enum type.
	 *
	 * @param enumJavaName The Java Enum simple class name (e.g., "ConditionType").
	 * @param definitions  The set of definitions for this enum type.
	 * @throws IOException If file writing fails.
	 */
	private void generateEnumProtoFile(String enumJavaName, Set<DefinitionData> definitions) throws IOException {
		log("为类型 '%s' 生成 proto 文件...", enumJavaName);

		// --- Prepare Data Model for the Proto Template ---
		Map<String, Object> dataModel = new HashMap<>();
		dataModel.put("protoPackage", this.protoPackage); // Use configured proto package

		// Derive Java options (can be made configurable later)
		String derivedJavaPackage = this.protoPackage; // Default: use proto package
		String derivedOuterClassName = enumJavaName + "Proto"; // Convention: TypeNameProto
		dataModel.put("javaPackage", derivedJavaPackage);

		// Global params - currently empty, add logic if needed via -A options
		dataModel.put("globalParams", Collections.emptyMap());

		// Prepare the single enum entry for the 'enums' list in the template
		Map<String, Object> enumModel = new HashMap<>();
		enumModel.put("name", enumJavaName); // Use the Java name as the Proto Enum name
		boolean hasZero = definitions.stream().anyMatch(d -> d.getId() == 0);
		enumModel.put("hasZeroId", hasZero);
		enumModel.put("params", Collections.emptyMap()); // Per-enum params - currently empty

		// Prepare members list
		List<Map<String, Object>> membersList = definitions.stream()
			.map(d -> {
				Map<String, Object> memberMap = new HashMap<>();
				memberMap.put("name", d.getName()); // Java constant name becomes Proto member name
				memberMap.put("id", d.getId());
				memberMap.put("comment", d.getDescription() == null ? "" : d.getDescription());
				return memberMap;
			})
			.collect(Collectors.toList());
		enumModel.put("members", membersList);

		// The template expects a list of enums, even if we generate one per file
		dataModel.put("enums", List.of(enumModel));

		// --- Determine Output File ---
		// Convention: lowercase_with_underscores.proto
		String protoFileName = camelCaseToSnakeCase(enumJavaName) + ".proto";
		String relativePath = this.protoPackage.replace('.', '/');

		// --- Write File using Filer ---
		FileObject fileObject = null;
		try {
			log("尝试在路径 '%s' 创建资源文件 '%s'", relativePath, protoFileName);
			// Generate into SOURCE_OUTPUT, assuming Gradle config maps it to proto sources
			// Note: StandardLocation.CLASS_OUTPUT might be another option if plugins expect it there
			fileObject = filer.createResource(StandardLocation.SOURCE_OUTPUT, // Or CLASS_OUTPUT depending on setup
				"", // Package is handled by relativePath combined with filename
				relativePath + "/" + protoFileName); // Combine relative path and filename

			try (Writer writer = fileObject.openWriter()) {
				protoTemplate.process(dataModel, writer);
				log("成功生成 proto 文件: %s", fileObject.getName());
			}
		} catch (Exception e) { // Catch TemplateException and IOException
			String filePath = (fileObject != null) ? fileObject.getName() : (relativePath + "/" + protoFileName);
			error(null, "无法为类型 '%s' 生成 proto 文件 '%s': %s", enumJavaName, filePath, e.getMessage());
			e.printStackTrace(); // Print stacktrace for easier debugging
			// Optionally rethrow or handle to potentially fail the build
			// throw new IOException("Failed to generate proto file for " + enumJavaName, e);
		}
	}


	/**
	 * Generates the single SylphEnumRegistrarImpl.java file.
	 * This implementation will register all discovered enum types.
	 *
	 * @throws IOException If file writing fails.
	 */
	private void generateEnumRegistrarFile() throws IOException {
		// Only generate if there are definitions
		if (collectedDefinitions.isEmpty()) {
			return;
		}
		log("生成 %s.java...", registrarClassName);

		// --- Prepare Data Model for the Registrar Template ---
		Map<String, Object> dataModel = new HashMap<>();

		// The template needs a list of enum types to register
		List<Map<String, Object>> enumTypeList = collectedDefinitions.keySet().stream()
			.sorted() // Sort alphabetically for consistent output
			.map(enumTypeName -> Map.<String, Object>of("name", enumTypeName)) // Map each name
			.collect(Collectors.toList());

		dataModel.put("enums", enumTypeList);
		dataModel.put("className", registrarClassName);
		dataModel.put("packageName", this.registrarPackage);
		dataModel.put("enumInterfaceClass", SylphEnum.class.getCanonicalName());
		dataModel.put("registrarInterfaceClass", SylphEnumRegistrar.class.getCanonicalName());
		dataModel.put("registrationContextClass", SylphEnumRegistrationContext.class.getCanonicalName());
		dataModel.put("registrarInterfaceName", SylphEnumRegistrar.class.getSimpleName());
		dataModel.put("protoPackage", protoPackage);

		// --- Determine Output File ---
		String fullyQualifiedClassName = this.registrarPackage + "." + registrarClassName;

		// --- Write File using Filer ---
		FileObject fileObject = null;
		try {
			log("尝试创建源文件: %s", fullyQualifiedClassName);
			fileObject = filer.createSourceFile(fullyQualifiedClassName); // Use createSourceFile for Java

			try (Writer writer = fileObject.openWriter()) {
				registrarTemplate.process(dataModel, writer);
				log("成功生成 Java 文件: %s", fileObject.getName());
			}
		} catch (Exception e) { // Catch TemplateException and IOException
			String filePath = (fileObject != null) ? fileObject.getName() : fullyQualifiedClassName;
			error(null, "无法生成 registrar 文件 '%s': %s", filePath, e.getMessage());
			e.printStackTrace();
			// Optionally rethrow
			// throw new IOException("Failed to generate registrar file", e);
		}
	}

	// generateServiceLoaderFile remains largely the same, but ensure correct class name
	private void generateServiceLoaderFile() throws IOException {
		// Only generate if there are definitions, otherwise the registrar wasn't generated
		if (collectedDefinitions.isEmpty()) {
			return;
		}

		String serviceInterfaceName = SylphEnumRegistrar.class.getCanonicalName();
		log("为 %s 生成 META-INF/services 文件", serviceInterfaceName);

		FileObject fileObject = null;
		try {
			// IMPORTANT: Use CLASS_OUTPUT for META-INF/services
			fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + serviceInterfaceName);
			try (Writer writer = fileObject.openWriter()) {
				// Write the fully qualified name of the *implementation* class
				String implementationClassName = registrarPackage + "." + registrarClassName;
				writer.write(implementationClassName);
				writer.write("\n"); // Add newline for good measure
				log("  + 注册 provider: %s", implementationClassName);
			}
			log("完成生成 META-INF/services 文件。");
		} catch (IOException e) {
			String filePath = (fileObject != null) ? fileObject.getName() : ("META-INF/services/" + serviceInterfaceName);
			error(null, "无法生成 service loader 文件 '%s': %s", filePath, e.getMessage());
			e.printStackTrace();
			// Optionally rethrow
			// throw new IOException("Failed to generate service loader file", e);
		}
	}

	// --- Helper methods ---

	// isInvalidJavaIdentifier remains the same...
	private boolean isInvalidJavaIdentifier(String s) {
		if (s == null || s.isEmpty()) {
			return true;
		}
		if (!Character.isJavaIdentifierStart(s.charAt(0))) {
			return true;
		}
		for (int i = 1; i < s.length(); i++) {
			if (!Character.isJavaIdentifierPart(s.charAt(i))) {
				return true;
			}
		}
		return SourceVersion.isKeyword(s);
	}

	// Helper to convert CamelCase to snake_case for proto filenames
	private String camelCaseToSnakeCase(String camelCase) {
		if (camelCase == null || camelCase.isEmpty()) {
			return "";
		}
		StringBuilder snakeCase = new StringBuilder();
		snakeCase.append(Character.toLowerCase(camelCase.charAt(0)));
		for (int i = 1; i < camelCase.length(); i++) {
			char currentChar = camelCase.charAt(i);
			if (Character.isUpperCase(currentChar)) {
				snakeCase.append('_');
				snakeCase.append(Character.toLowerCase(currentChar));
			} else {
				snakeCase.append(currentChar);
			}
		}
		return snakeCase.toString();
	}

	// log, warn, error methods remain the same...
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