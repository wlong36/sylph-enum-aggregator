package io.github.wlong36.sylph.enumaggregator.api;

import java.lang.annotation.*;

/**
 * SylphEnum 注册
 *
 * @author wlong
 * @since 2025/4/12
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Repeatable(SylphEnumRegs.class)
public @interface SylphEnumReg {

	/**
	 * 枚举类型名
	 */
	String type();

	/**
	 * 枚举id
	 */
	int id();

	/**
	 * 枚举名称
	 */
	String name();

	/**
	 * 枚举描述
	 */
	String desc();
}
