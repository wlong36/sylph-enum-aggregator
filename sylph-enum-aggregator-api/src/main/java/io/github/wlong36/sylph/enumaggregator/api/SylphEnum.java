package io.github.wlong36.sylph.enumaggregator.api;

import javax.annotation.Nonnull;

/**
 *  所有由 EnumRegistry 管理的枚举类型的基础接口。
 *  在编译时生成的枚举应该实现这个接口（或其子接口），
 *  以便能够被注册表发现和管理，并提供通用的访问方法，如获取 ID 和名称。
 *  框架代码通常依赖此接口类型进行交互，而不是依赖具体的、可能尚未生成的枚举类。
 *
 * @author wlong
 * @since 2025/4/12
 */
public interface SylphEnum {

    /**
     * 获取与此定义关联的整数索引。
     *
     * @return 定义的整数索引。这个索引在相同类型的定义中（例如所有条件类型）应该是唯一的。
     */
    int ordinal();

    /**
     * 获取与此定义关联的唯一整数 ID。
     *
     * @return 定义的整数 ID。这个 ID 在同一类型的定义中（例如所有条件类型）应该是唯一的。
     */
    int id();

    /**
     * 获取此定义的程序化名称（通常直接对应于生成的枚举常量的名称）。
     *
     * @return 定义的名称字符串 (例如 "POISON", "COMMON")。这个名称在同一类型的定义中应该是唯一的。
     */
    @Nonnull
    String name();
}