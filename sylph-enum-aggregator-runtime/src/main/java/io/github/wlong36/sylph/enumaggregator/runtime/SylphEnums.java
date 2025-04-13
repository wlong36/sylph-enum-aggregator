package io.github.wlong36.sylph.enumaggregator.runtime; // 或者你选择的运行时包

import io.github.wlong36.sylph.enumaggregator.api.SylphEnum;
import io.github.wlong36.sylph.enumaggregator.api.SylphEnumRegistrar;
import io.github.wlong36.sylph.enumaggregator.api.SylphEnumRegistrationContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 一个用于动态生成的、实现了 SylphEnum 接口的枚举的中央注册表
 * 使用 ServiceLoader 来发现并注册由 EnumRegistrationProvider 实现（在编译时生成）提供的枚举
 *
 * @author wlong
 * @since 2025/4/12
 */
public final class SylphEnums implements SylphEnumRegistrationContext {
	private static final Logger LOGGER = Logger.getLogger(SylphEnums.class.getName());

	/**
	 * 存储按具体枚举类型索引的映射
	 */
	private final Map<String, SylphEnumDefinition> definitionMap = new ConcurrentHashMap<>();

	/**
	 * 通过 ServiceLoader 加载提供者来确保注册表已初始化
	 */
	public void initialized() {
		ServiceLoader<SylphEnumRegistrar> sylphEnumProviders = ServiceLoader.load(SylphEnumRegistrar.class,
																				  SylphEnums.class.getClassLoader());

		for (SylphEnumRegistrar provider : sylphEnumProviders) {
			provider.doRegister(this);
		}
		LOGGER.info("EnumRegistry 初始化成功");
	}

	@Override
	public <T extends Enum<T> & SylphEnum> void register(Class<T> enumType) {
		if (enumType == null) {
			throw new NullPointerException("枚举类型不能为空");
		}
		if (!enumType.isEnum()) {
			throw new IllegalArgumentException("枚举类型 " + enumType + " 不是一个枚举类型");
		}
		if (!SylphEnum.class.isAssignableFrom(enumType)) {
			throw new IllegalArgumentException("枚举类型 " + enumType + " 不是一个 SylphEnum 类型");
		}

		SylphEnumDefinition enumDefinition = new SylphEnumDefinition(enumType);
		if (definitionMap.containsKey(enumType.getSimpleName())) {
			throw new IllegalArgumentException("枚举类型 " + enumType + " 已经注册");
		}
		definitionMap.put(enumType.getSimpleName(), enumDefinition);
	}

	// --- 公共查找方法 ---


	/**
	 * 根据给定的枚举类型名获取其定义
	 *
	 * @param enumType 枚举类型名
	 * @return 包含枚举定义的 Optional，如果未找到则返回 Optional.empty()
	 */
	@Nonnull
	public Optional<SylphEnumDefinition> findDefinition(String enumType) {
		return Optional.ofNullable(definitionMap.get(enumType));
	}

	/**
	 * 根据给定的枚举类型名和序数获取枚举常量
	 *
	 * @param enumType 枚举类型名
	 * @param ordinal  枚举常量的序数
	 * @return 枚举常量
	 * @throws NullPointerException      如果是未注册的枚举类型
	 * @throws IndexOutOfBoundsException 如果ordinal传入错误
	 */
	public SylphEnum getByOrdinal(String enumType, int ordinal) {
		return findDefinition(enumType)
				.map(definition -> definition.getByOrdinal(ordinal))
				.orElseThrow(() -> new NullPointerException("未注册的枚举类型: " + enumType));
	}

	/**
	 * 根据给定的枚举类型名和 ID 获取枚举常量
	 *
	 * @param enumType 枚举类型名
	 * @param id       枚举常量的 ID
	 * @return 枚举常量
	 * @throws NullPointerException   如果是未注册的枚举类型
	 * @throws NoSuchElementException 如果未找到枚举常量
	 */
	@Nonnull
	public SylphEnum getById(String enumType, int id) {
		return findDefinition(enumType)
				.orElseThrow(() -> new NullPointerException("未注册的枚举类型: " + enumType))
				.getById(id);
	}

	/**
	 * 根据给定的枚举类型名和 ID 获取枚举常量
	 *
	 * @param enumType 枚举类型名
	 * @param id       枚举常量的 ID
	 * @return 枚举常量，如果未找到则返回 null
	 */
	@Nullable
	public SylphEnum getByIdOrNull(String enumType, int id) {
		return findDefinition(enumType)
				.map(definition -> definition.getByIdOrNull(id))
				.orElse(null);
	}

	/**
	 * 根据给定的枚举类型名和名称获取枚举常量
	 *
	 * @param enumType 枚举类型名
	 * @param name     枚举常量的名称 (区分大小写)
	 * @return 枚举常量
	 * @throws NullPointerException   如果是未注册的枚举类型
	 * @throws NoSuchElementException 如果未找到枚举常量
	 */
	@Nonnull
	public SylphEnum getByName(String enumType, String name) {
		return findDefinition(enumType)
				.orElseThrow(() -> new NullPointerException("未注册的枚举类型: " + enumType))
				.getByName(name);
	}

	/**
	 * 根据给定的枚举类型名和名称获取枚举常量
	 *
	 * @param enumType 枚举类型名
	 * @param name     枚举常量的名称 (区分大小写)
	 * @return 枚举常量，如果未找到则返回 null
	 */
	@Nullable
	public SylphEnum getByNameOrNull(String enumType, String name) {
		return findDefinition(enumType)
				.map(definition -> definition.getByNameOrNull(name))
				.orElse(null);
	}

	/**
	 * 根据给定的枚举类型名获取所有枚举常量
	 *
	 * @param enumType 枚举类型名
	 * @return 包含所有枚举常量的集合
	 */
	@Nonnull
	public List<SylphEnum> getAll(String enumType) {
		return findDefinition(enumType)
				.map(SylphEnumDefinition::getAll)
				.orElse(Collections.emptyList());
	}
}