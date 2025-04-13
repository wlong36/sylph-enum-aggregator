package io.github.wlong36.sylph.enumaggregator.api;

import javax.annotation.Nonnull;

/**
 * SylphEnum 注册器，由Annotation Processor自动生成实现类
 *
 * @author wlong
 * @since 2025/4/12
 */
public interface SylphEnumRegistrar {

	/**
	 * 注册枚举
	 *
	 * @param registrationContext 注册上下文
	 */
	void doRegister(@Nonnull SylphEnumRegistrationContext registrationContext);

}
