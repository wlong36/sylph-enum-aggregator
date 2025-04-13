package io.github.wlong36.sylph.enumaggregator.api;

/**
 * 定义一个通用的注册上下文接口，用于向实际的注册表注册枚举。
 *
 * @author wlong
 * @since 2025/4/12
 */
public interface SylphEnumRegistrationContext {

	/**
	 * 注册一个聚合enum类
	 * 在初始化期间调用
	 * 它会填充按具体 Enum 类和所有已实现的 SylphEnum 接口索引的映射
	 *
	 * @param <T>      具体的枚举类型
	 * @param enumType 注册的枚举类型的 Class 对象
	 * @throws NullPointerException  如果任何参数为 null
	 * @throws IllegalStateException 如果检测到枚举名冲突 或者 同一类型的 ID 或名称冲突
	 */
	<T extends Enum<T> & SylphEnum> void register(Class<T> enumType);
}