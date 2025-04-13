package io.github.wlong36.sylph.enumaggregator.runtime;

import io.github.wlong36.sylph.enumaggregator.api.SylphEnum;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 聚合枚举定义
 *
 * @author wlong
 * @since 2025/4/12
 */
public class SylphEnumDefinition {
	@Nonnull
	private final Class<? extends Enum<? extends SylphEnum>> enumType;
	@Nonnull
	private final Map<Integer, SylphEnum> byId;
	@Nonnull
	private final Map<String, SylphEnum> byName;
	@Nonnull
	private final List<SylphEnum> all;

	public <T extends Enum<T> & SylphEnum> SylphEnumDefinition(@Nonnull Class<T> enumType) {
		this.enumType = enumType;
		SylphEnum[] enumConstants = enumType.getEnumConstants();
		this.byId = new HashMap<>(enumConstants.length);
		this.byName = new HashMap<>(enumConstants.length);
		for (SylphEnum enumConstant : enumConstants) {
			SylphEnum exist = this.byId.put(enumConstant.id(), enumConstant);
			if (exist != null) {
				throw new IllegalStateException(enumType + " 存在重复的id: " + exist.id());
			}
			exist = this.byName.put(enumConstant.name(), enumConstant);
			if (exist != null) {
				throw new IllegalStateException(enumType + " 存在重复的name: " + exist.name());
			}
		}
		this.all = List.of(enumConstants);
	}

	/**
	 * 获取枚举数量
	 */
	public int length() {
		return all.size();
	}

	/**
	 * 根据索引获取枚举
	 *
	 * @param ordinal 索引
	 */
	public SylphEnum getByOrdinal(int ordinal) {
		return all.get(ordinal);
	}

	/**
	 * 根据id获取枚举
	 *
	 * @param id id
	 * @throws NoSuchElementException 找不到枚举时抛出
	 */
	@Nonnull
	public SylphEnum getById(int id) {
		SylphEnum t = byId.get(id);
		if (t == null) {
			throw new NoSuchElementException(enumType + " 找不到id为" + id + "的枚举");
		}
		return t;
	}

	/**
	 * 根据id获取枚举
	 *
	 * @param id id
	 * @return 找不到枚举时返回null
	 */
	@Nullable
	public SylphEnum getByIdOrNull(int id) {
		return byId.get(id);
	}

	/**
	 * 根据name获取枚举
	 *
	 * @param name name
	 * @throws NoSuchElementException 找不到枚举时抛出
	 */
	@Nonnull
	public SylphEnum getByName(String name) {
		SylphEnum t = byName.get(name);
		if (t == null) {
			throw new NoSuchElementException(enumType + " 找不到name为" + name + "的枚举");
		}
		return t;
	}

	/**
	 * 根据name获取枚举
	 *
	 * @param name name
	 * @return 找不到枚举时返回null
	 */
	@Nullable
	public SylphEnum getByNameOrNull(String name) {
		return byName.get(name);
	}

	/**
	 * 获取所有枚举
	 */
	@Nonnull
	public List<SylphEnum> getAll() {
		return all;
	}

	@Nonnull
	public Class<? extends Enum<? extends SylphEnum>> getEnumType() {
		return enumType;
	}

}
